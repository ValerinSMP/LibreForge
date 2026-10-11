package com.willfp.libreforge.effects.templates

import com.willfp.eco.core.integrations.antigrief.AntigriefManager
import com.willfp.eco.util.runExempted
import com.willfp.libreforge.EmptyProvidedHolder
import com.willfp.libreforge.HolderStates
import com.willfp.libreforge.isBrokenItem
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.diagnostics.performanceCount
import com.willfp.libreforge.diagnostics.performanceScope
import com.willfp.libreforge.plugin
import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.WeakHashMap
import java.util.IdentityHashMap

/** Opt-in extra-block queue. No TriggerData, event, or live tool is retained between ticks. */
internal object MiningBatches : Listener {
    private data class Position(val world: UUID, val x: Int, val y: Int, val z: Int)
    private class Target(val position: Position, val state: String) { var invalidated = false }
    private class SourceDecision(var allowed: Boolean = false)
    // Removed in MONITOR during the same event; unresolved events fail closed at the next tick.
    private val sourceEvents = IdentityHashMap<BlockBreakEvent, SourceDecision>()
    private val targets = HashMap<Position, MutableSet<Target>>()
    private val queue = FairMiningQueue<UUID, Target>()
    private var tick = 0L
    private var enabled = false
    private var limit = 24
    private var budget = 4_000_000L
    private var totalLimit = 8192
    private var playerLimit = 256
    private var maxAge = 40L
    private var broken = 0L
    private var skipped = 0L
    private var lastWarning = Long.MIN_VALUE
    private var lastFailureWarning = Long.MIN_VALUE
    private val playerWarnings = WeakHashMap<Player, Long>()

    fun reload() {
        shutdown()
        val config = plugin.configYml
        enabled = config.getBool("effects.mining-batches.enabled")
        limit = config.getInt("effects.max-blocks-per-tick").coerceIn(1, 256)
        budget = config.getInt("effects.mining-batches.budget-ms").coerceIn(1, 10) * 1_000_000L
        totalLimit = config.getInt("effects.mining-batches.max-queued-blocks").coerceIn(24, 16384)
        playerLimit = config.getInt("effects.mining-batches.max-queued-blocks-per-player").coerceIn(8, 1024)
        maxAge = config.getInt("effects.mining-batches.max-age-ticks").coerceIn(1, 200).toLong()
    }

    fun start() {
        plugin.scheduler.runTimer(1, 1) {
            tick++
            sourceEvents.clear()
            if (enabled && queue.pending > 0) performanceScope({ "libreforge:blocks" }, { "blocks:queue:tick" }) {
                queue.drain(tick, limit, budget, maxAge) { error ->
                    if (lastFailureWarning == Long.MIN_VALUE || tick - lastFailureWarning >= 100) {
                        lastFailureWarning = tick
                        plugin.logger.log(java.util.logging.Level.WARNING,
                            "Mining batches: block callback failed; remaining job cancelled. See performance status.", error)
                    }
                }
            }
        }
    }

    fun shutdown() { queue.clear("reset"); targets.clear(); sourceEvents.clear(); playerWarnings.clear() }

    fun status(): String = "Mining batches: enabled=$enabled pending=${queue.pending} players=${queue.players} " +
        "attempted=${queue.processed} broken=$broken skipped=$skipped discarded=${queue.discarded} " +
        "rejected=${queue.rejected} failures=${queue.failures} limit=$limit budget_ms=${budget / 1_000_000}"

    /** null means legacy mode; false means bounded admission refused, never unbounded fallback. */
    fun submit(player: Player, blocks: Collection<Block>, preventTriggers: Boolean, data: TriggerData): Boolean? {
        if (!enabled || preventTriggers || plugin.configYml.getBool("effects.use-setblock-break")) return null
        check(Bukkit.isPrimaryThread()) { "Mining batches require the server thread" }
        val anchor = data.block?.location ?: data.location ?: return false
        if (anchor.world != player.world || data.holder === EmptyProvidedHolder) return null
        val id = player.uniqueId
        if (!queue.canAccept(id, blocks.size, totalLimit, playerLimit)) {
            queue.reject()
            performanceCount({ "blocks:queue:rejected" })
            if (lastWarning == Long.MIN_VALUE || tick - lastWarning >= 100) {
                lastWarning = tick
                plugin.logger.warning("Mining batches: queue capacity reached; extra-block job refused. See /libreforge performance status.")
            }
            val previous = playerWarnings[player]
            if (previous == null || tick - previous >= 100) {
                playerWarnings[player] = tick
                player.sendMessage(plugin.langYml.getStringOrNull("messages.mining-batches-full")
                    ?: "Additional mining is busy; wait before using this ability again.")
            }
            return false
        }
        val worldId = player.world.uid
        val slot = player.inventory.heldItemSlot
        val tool = normalizedTool(player.inventory.itemInMainHand)
        val reference = WeakReference(player)
        val holder = data.holder.holder.id
        val originalEvent = data.event as? BlockBreakEvent
        val decision = if (originalEvent == null) SourceDecision(true)
            else sourceEvents[originalEvent] ?: SourceDecision(!originalEvent.isCancelled)
        val x = anchor.blockX; val y = anchor.blockY; val z = anchor.blockZ
        val work = blocks.map { Target(Position(it.world.uid, it.x, it.y, it.z), it.blockData.asString) }
        work.forEach { targets.getOrPut(it.position) { HashSet() }.add(it) }
        val accepted = queue.enqueue(id, work, tick, totalLimit, playerLimit, {
            val current = reference.get()
            decision.allowed && current != null && Bukkit.getPlayer(id) === current && current.isOnline && !current.isDead &&
                current.world.uid == worldId && current.inventory.heldItemSlot == slot &&
                !current.inventory.itemInMainHand.isBrokenItem &&
                sameTool(tool, current.inventory.itemInMainHand) &&
                current.eyeLocation.let { eye -> isMiningAnchorInReach(eye.x, eye.y, eye.z, x, y, z,
                    current.getAttribute(Attribute.BLOCK_INTERACTION_RANGE)?.value ?: 0.0) } &&
                HolderStates.providedActiveEffects(current.toDispatcher()).any { it.holder.holder.id == holder }
        }, { target ->
            try {
                val current = reference.get()
                if (current != null) breakTarget(current, target, holder.toString())
            } finally { release(target) }
        }, { remaining, reason ->
            remaining.forEach { release(it) }
            performanceCount({ "blocks:queue:discarded:$reason" }, remaining.size.toLong())
        })
        if (!accepted) work.forEach { release(it) }
        if (accepted) performanceCount({ "blocks:queue:accepted" }, work.size.toLong())
        return accepted
    }

    private fun normalizedTool(item: ItemStack): ItemStack = item.clone().also {
        val meta = it.itemMeta
        if (meta is Damageable) { meta.damage = 0; it.itemMeta = meta }
    }

    private fun sameTool(expected: ItemStack, actual: ItemStack): Boolean =
        expected.amount == actual.amount && expected.isSimilar(normalizedTool(actual))

    private fun release(target: Target) {
        val registered = targets[target.position] ?: return
        registered.remove(target)
        if (registered.isEmpty()) targets.remove(target.position)
    }

    private fun invalidate(block: Block) {
        if (targets.isEmpty()) return
        targets[Position(block.world.uid, block.x, block.y, block.z)]?.forEach { it.invalidated = true }
    }

    private fun breakTarget(player: Player, target: Target, origin: String) =
        performanceScope({ origin }, { "blocks:queue:break" }) {
            val p = target.position
            val world = player.world
            if (target.invalidated || world.uid != p.world || !world.isChunkLoaded(p.x shr 4, p.z shr 4)) {
                skipped++; performanceCount({ "blocks:queue:skipped" }); return@performanceScope
            }
            val block = world.getBlockAt(p.x, p.y, p.z)
            if (block.blockData.asString != target.state || !AntigriefManager.canBreakBlock(player, block)) {
                skipped++; performanceCount({ "blocks:queue:skipped" }); return@performanceScope
            }
            player.runExempted {
                val key = "blockbreakevent-ignore"
                try {
                    block.setMetadata(key, plugin.createMetadataValue(true))
                    performanceCount({ "blocks:break:event-attempts" })
                    if (player.breakBlock(block)) {
                        broken++; performanceCount({ "blocks:break:event-success" })
                    }
                } finally { block.removeMetadata(key, plugin) }
            }
        }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) { invalidate(event.blockPlaced) }
    @EventHandler(priority = EventPriority.LOWEST)
    fun onSourceBegin(event: BlockBreakEvent) {
        if (enabled) sourceEvents[event] = SourceDecision()
    }
    @EventHandler(priority = EventPriority.MONITOR)
    fun onBreak(event: BlockBreakEvent) {
        sourceEvents.remove(event)?.allowed = !event.isCancelled
        if (!event.isCancelled) invalidate(event.block)
    }
    @EventHandler fun onQuit(event: PlayerQuitEvent) { queue.cancel(event.player.uniqueId) }
    @EventHandler fun onWorld(event: PlayerChangedWorldEvent) { queue.cancel(event.player.uniqueId) }
    @EventHandler fun onDeath(event: PlayerDeathEvent) { queue.cancel(event.entity.uniqueId) }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHeld(event: PlayerItemHeldEvent) { queue.cancel(event.player.uniqueId) }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSwap(event: PlayerSwapHandItemsEvent) { queue.cancel(event.player.uniqueId) }
    // GUI plugins may move an item even when the click/drag is cancelled.
    @EventHandler(priority = EventPriority.MONITOR)
    fun onClick(event: InventoryClickEvent) { (event.whoClicked as? Player)?.let { queue.cancel(it.uniqueId) } }
    @EventHandler(priority = EventPriority.MONITOR)
    fun onDrag(event: InventoryDragEvent) { (event.whoClicked as? Player)?.let { queue.cancel(it.uniqueId) } }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) { queue.cancel(event.player.uniqueId) }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGameMode(event: PlayerGameModeChangeEvent) { queue.cancel(event.player.uniqueId) }
}
