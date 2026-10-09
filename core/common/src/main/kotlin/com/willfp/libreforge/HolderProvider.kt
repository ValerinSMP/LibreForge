package com.willfp.libreforge

import com.willfp.eco.core.cache.EcoCache
import com.willfp.eco.core.map.listMap
import com.willfp.libreforge.effects.EffectBlock
import com.willfp.libreforge.slot.ItemHolderFinder
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID
import java.time.Duration
import com.willfp.libreforge.conditions.DiagnosticWindow


/**
 * Provides the holders that are held by a player.
 */
interface HolderProvider {
    /**
     * Provide the holders.
     */
    fun provide(dispatcher: Dispatcher<*>): Collection<ProvidedHolder>
}

/**
 * A typed holder provider.
 */
interface TypedHolderProvider<T : Holder> : HolderProvider {
    override fun provide(dispatcher: Dispatcher<*>): Collection<TypedProvidedHolder<T>>
}

@Deprecated(
    "HolderProvideEvent now only fires when the holder set changes, not on every refresh. " +
    "Use HolderEnableEvent and HolderDisableEvent to react to holder additions and removals. " +
    "This event will be removed in a future version.",
    level = DeprecationLevel.WARNING
)
class HolderProvideEvent(
    val dispatcher: Dispatcher<*>,
    val holders: Collection<ProvidedHolder>
) : Event() {
    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlerList
        }
    }
}

class HolderEnableEvent(
    val dispatcher: Dispatcher<*>,
    val holder: ProvidedHolder,
    val newHolders: Collection<ProvidedHolder>
) : Event() {
    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlerList
        }
    }
}

class HolderDisableEvent(
    val dispatcher: Dispatcher<*>,
    val holder: ProvidedHolder,
    val previousHolders: Collection<ProvidedHolder>
) : Event() {
    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlerList
        }
    }
}

@Deprecated(
    "Use ProvidedEffectBlock instead, this is no longer used and will be removed in a future version.",
    ReplaceWith("ProvidedEffectBlock"),
    DeprecationLevel.ERROR
)
data class ProvidedEffectBlocks(
    val holder: ProvidedHolder,
    val effects: Set<EffectBlock>
)

/**
 * EffectBlock provided by a holder.
 */
data class ProvidedEffectBlock(
    val effect: EffectBlock,
    val holder: ProvidedHolder
) : Comparable<ProvidedEffectBlock> {
    override fun compareTo(other: ProvidedEffectBlock): Int {
        return this.effect.weight - other.effect.weight
    }
}

/**
 * Pairs each [ProvidedEffectBlock] with its occurrence index: how many prior entries in this
 * list share the same (effect, holder id) pair, in list order.
 *
 * Used to derive a modifier identity that is independent of the physical holder (e.g. the
 * inventory slot an item occupies), while still allowing distinct holders with the same id
 * (e.g. the same enchantment on two armor pieces) to stack.
 */
internal fun List<ProvidedEffectBlock>.withOccurrences(): List<Pair<ProvidedEffectBlock, Int>> {
    val seen = HashMap<Pair<EffectBlock, Any>, Int>()
    return map { block ->
        val key = block.effect to block.holder.holder.id
        val occurrence = seen.getOrDefault(key, 0)
        seen[key] = occurrence + 1
        block to occurrence
    }
}

private val providers = mutableListOf<HolderProvider>()

/**
 * Register a new holder provider.
 */
fun registerHolderProvider(provider: HolderProvider) = providers.add(provider)

/**
 * Register a new holder provider for all possible dispatchers.
 */
fun registerGenericHolderProvider(provider: (Dispatcher<*>) -> Collection<ProvidedHolder>) =
    registerHolderProvider(object : HolderProvider {
        override fun provide(dispatcher: Dispatcher<*>) = provider(dispatcher)
    })

/**
 * Register a new holder provider for a specific type of dispatcher.
 */
inline fun <reified T> registerSpecificHolderProvider(crossinline provider: (T) -> Collection<ProvidedHolder>) =
    registerHolderProvider(object : HolderProvider {
        override fun provide(dispatcher: Dispatcher<*>): Collection<ProvidedHolder> {
            return if (dispatcher.isType<T>()) {
                provider(dispatcher.get<T>()!!)
            } else {
                emptyList()
            }
        }
    })

fun registerSlotHolderFinderAsProvider(finder: ItemHolderFinder<*>) =
    registerHolderProvider(finder.toHolderProvider())

private val refreshFunctions = mutableListOf<(Dispatcher<*>) -> Unit>()

/**
 * Register a function to be called when a dispatcher's holders are refreshed.
 */
fun registerRefreshFunction(function: (Dispatcher<*>) -> Unit) {
    refreshFunctions += function
}

/**
 * Register a function to be called when a dispatcher's holders are refreshed for a specific dispatcher.
 */
inline fun <reified T> registerSpecificRefreshFunction(crossinline function: (T) -> Unit) {
    registerRefreshFunction {
        it.get<T>()?.let { t ->
            function(t)
        }
    }
}

private val holderCooldown: EcoCache<UUID, Unit>? =
    plugin.configYml.getInt("refresh.cooldown").takeIf { it > 0 }?.let {
        EcoCache.builder<UUID, Unit>()
            .expireAfterWrite(Duration.ofMillis(it.toLong()))
            .build()
    }

/**
 * Update holders, effects, and call refresh functions.
 */
fun Dispatcher<*>.refreshHolders() {
    if (holderCooldown != null) {
        val isOnCooldown = holderCooldown.get(this.uuid) != null
        if (isOnCooldown) {
            return
        }

        holderCooldown.put(this.uuid, Unit)
    }

    this.forceRefreshHolders()
}

/**
 * Forcibly refresh holders, ignoring cooldown.
 */
fun Dispatcher<*>.forceRefreshHolders() {
    measuredRefresh { tracePlayer, started ->
        RefreshTracing.phase("refresh-functions") { refreshFunctions.forEach { it(this) } }
        // Pre-populate cache so updateEffects() gets a hit instead of a miss
        RefreshTracing.phase("holders") { holderCache.put(this.uuid, this.computeHolders()) }
        this.updateEffectsMeasured(tracePlayer, "forced", started)
    }
}

/**
 * Re-evaluate active effects against the current (possibly cached) holders.
 *
 * Used by the periodic polling task: holders are presumed stable between events,
 * so we skip the provider rescan and only re-check conditions. The holder cache
 * still expires naturally (4 s TTL), so providers are still rescanned periodically.
 * Event-based callers (equipment change, inventory click, etc.) use [refreshHolders]
 * or [forceRefreshHolders] which explicitly invalidate the cache.
 */
internal fun Dispatcher<*>.pollEffects() {
    measuredRefresh { tracePlayer, started ->
        RefreshTracing.phase("refresh-functions") { refreshFunctions.forEach { it(this) } }
        this.updateEffectsMeasured(tracePlayer, "poll", started)
    }
}

private val holderPlaceholderProviders = mutableListOf<(ProvidedHolder, Dispatcher<*>) -> Collection<NamedValue>>()

/**
 * Register a function to generate placeholders for a holder.
 */
fun registerPlaceholderProvider(provider: (ProvidedHolder, Dispatcher<*>) -> Collection<NamedValue>) {
    holderPlaceholderProviders += provider
}

/**
 * Register a function to generate placeholders for a holder for any dispatcher.
 */
inline fun <reified T : Holder> registerHolderPlaceholderProvider(crossinline provider: (T, Dispatcher<*>) -> Collection<NamedValue>) {
    registerPlaceholderProvider { provided, dispatcher ->
        val holder = provided.holder
        if (holder is T) {
            provider(holder, dispatcher)
        } else {
            emptyList()
        }
    }
}

/**
 * Register a function to generate placeholders for a holder for a specific dispatcher.
 */
inline fun <reified T : Holder, reified R> registerSpecificHolderPlaceholderProvider(crossinline provider: (T, R) -> Collection<NamedValue>) {
    registerPlaceholderProvider { provided, dispatcher ->
        val holder = provided.holder
        if (holder is T && dispatcher.isType<R>()) {
            provider(holder, dispatcher.get<R>()!!)
        } else {
            emptyList()
        }
    }
}

/**
 * Generate placeholders for a holder.
 */
fun ProvidedHolder.generatePlaceholders(dispatcher: Dispatcher<*>): List<NamedValue> {
    return buildList {
        for (provider in holderPlaceholderProviders) {
            addAll(provider(this@generatePlaceholders, dispatcher))
        }
    }
}

private val previousHolders: EcoCache<UUID, Collection<ProvidedHolder>> =
    EcoCache.builder<UUID, Collection<ProvidedHolder>>()
        .expireAfterAccess(Duration.ofSeconds(30))
        .build()

private val holderCache = EcoCache.builder<UUID, Collection<ProvidedHolder>>()
    .expireAfterWrite(Duration.ofSeconds(4))
    .build()

private fun Dispatcher<*>.computeHolders(): Collection<ProvidedHolder> {
    if (this is EntityDispatcher && this.dispatcher !is Player && !plugin.configYml.getBool("refresh.entities.enabled")) {
        return emptyList()
    }

    RefreshTracing.holderRebuilt()
    val holders = providers.flatMapIndexed { index, provider ->
        RefreshTracing.measure({ "provider:$index:${provider.javaClass.name}" }) { provider.provide(this) }
    }

    val old = previousHolders.get(this.uuid) ?: emptyList()

    val newByID = holders.associateBy { it.holder.id }
    val oldByID = old.associateBy { it.holder.id }

    val added = newByID.keys - oldByID.keys
    val removed = oldByID.keys - newByID.keys

    for (id in added) {
        Bukkit.getPluginManager().callEvent(
            HolderEnableEvent(this, newByID[id]!!, holders)
        )
    }

    for (id in removed) {
        Bukkit.getPluginManager().callEvent(
            HolderDisableEvent(this, oldByID[id]!!, old)
        )
    }

    previousHolders.put(this.uuid, holders)

    if (added.isNotEmpty() || removed.isNotEmpty()) {
        @Suppress("DEPRECATION")
        Bukkit.getPluginManager().callEvent(
            HolderProvideEvent(this, holders)
        )
    }

    return holders
}

/**
 * The holders.
 */
val Dispatcher<*>.holders: Collection<ProvidedHolder>
    get() = holderCache.get(this.uuid) { computeHolders() }

/**
 * Get holders of a specific type.
 */
inline fun <reified T : Holder> Dispatcher<*>.getHoldersOfType(): Collection<T> {
    return this.holders.mapNotNull { it.holder as? T }
}

/**
 * Invalidate holder cache to force rescan.
 */
fun Dispatcher<*>.updateHolders() {
    holderCache.invalidate(this.uuid)
}

internal fun Dispatcher<*>.purgePreviousHolders() {
    previousHolders.invalidate(this.uuid)
    previousStates.remove(this.uuid)
}

internal fun clearAllHolderCaches() {
    holderCache.invalidateAll()
    previousHolders.invalidateAll()
}

// Effects that were active on previous update
private val previousStates = listMap<UUID, ProvidedEffectBlock>() // Optimisation.

/**
 * Get active effects for a [dispatcher] from holders mapped to the holder
 * that has provided them.
 */
fun Collection<ProvidedHolder>.getProvidedActiveEffects(dispatcher: Dispatcher<*>): List<ProvidedEffectBlock> {
    val blocks = mutableListOf<ProvidedEffectBlock>()

    for (holder in this) {
        if (holder.holder.conditions.areMet(dispatcher, holder)) {
            for (block in holder.getActiveEffects(dispatcher)) {
                blocks += ProvidedEffectBlock(block, holder)
            }
        }
    }

    return blocks.sorted()
}

/**
 * Get active effects for a [dispatcher].
 */
fun ProvidedHolder.getActiveEffects(dispatcher: Dispatcher<*>) =
    this.holder.effects.filter { it.conditions.areMet(dispatcher, this) }.toSet()

/**
 * Recalculate active effects.
 */
fun Dispatcher<*>.calculateActiveEffects(): List<ProvidedEffectBlock> {
    val holders = RefreshTracing.phase("holders") { this.holders }
    return RefreshTracing.phase("conditions") { holders.getProvidedActiveEffects(this) }
}

/**
 * The active effects.
 */
val Dispatcher<*>.activeEffects: List<EffectBlock>
    get() = previousStates[this.uuid].map { it.effect }

/**
 * The active effects mapped to the holder that provided them.
 */
val Dispatcher<*>.providedActiveEffects: List<ProvidedEffectBlock>
    get() = previousStates[this.uuid]

/**
 * Update the active effects.
 */
private val refreshDiagnosticWindow = DiagnosticWindow()

fun Dispatcher<*>.updateEffects() {
    measuredRefresh { tracePlayer, started ->
        this.updateEffectsMeasured(tracePlayer, "direct", started)
    }
}

private fun Dispatcher<*>.measuredRefresh(action: (Player?, Long) -> Unit) {
    val tracePlayer = if (plugin.configYml.getBool("diagnostics.effects-refresh.enabled")) {
        get<Player>()?.takeIf { player ->
            val filter = plugin.configYml.getString("diagnostics.effects-refresh.player")
            !filter.isNullOrBlank() && (filter == "*" || filter.equals(player.name, true)) &&
                    refreshDiagnosticWindow.allow(System.nanoTime(), 2)
        }
    } else null
    RefreshTracing.scoped(tracePlayer != null) {
        action(tracePlayer, if (tracePlayer != null) System.nanoTime() else 0L)
    }
}

private fun Dispatcher<*>.updateEffectsMeasured(tracePlayer: Player?, source: String, refreshStarted: Long) {
    val started = if (tracePlayer != null) System.nanoTime() else 0L
    val before = this.providedActiveEffects
    val after = this.calculateActiveEffects()
    val calculated = if (tracePlayer != null) System.nanoTime() else 0L

    val (afterWithOcc, added, removed) = RefreshTracing.phase("diff") {
        previousStates[this.uuid] = after

        // Preserve identity, ordering and multiplicity of physically distinct holders.
        val beforeWithOcc = before.withOccurrences()
        val afterWithOcc = after.withOccurrences()

        // Permanent effects also have a run order, so we need to sort them.
        val added = orderedOccurrenceDifference(afterWithOcc, beforeWithOcc, compareBy { it.first })
        val removed = orderedOccurrenceDifference(beforeWithOcc, afterWithOcc, compareBy { it.first })
        Triple(afterWithOcc, added, removed)
    }

    RefreshTracing.phase("callbacks-remove") {
        for ((block, occurrence) in removed) {
            val (effect, holder) = block
            RefreshTracing.measure({ "remove:${holder.holder.id}" }) {
                effect.disable(this, holder, occurrence = occurrence)
            }
        }
    }

    RefreshTracing.phase("callbacks-add") {
        for ((block, occurrence) in added) {
            val (effect, holder) = block
            RefreshTracing.measure({ "add:${holder.holder.id}" }) {
                effect.enable(this, holder, occurrence = occurrence)
            }
        }
    }

    // Reloading is now done by disabling all, then enabling all. Effect#reload is deprecated.
    // Since permanent effects are not allowed in chains, they are always done in the correct
    // order as mixing weights is not a concern.
    val toReload = RefreshTracing.phase("diff") {
        orderedOccurrenceDifference(afterWithOcc, added, compareBy { it.first })
    }

    RefreshTracing.phase("callbacks-reload-disable") {
        for ((block, occurrence) in toReload) {
            val (effect, holder) = block
            RefreshTracing.measure({ "reload-disable:${holder.holder.id}" }) {
                effect.disable(this, holder, occurrence = occurrence, isReload = true)
            }
        }
    }

    RefreshTracing.phase("callbacks-reload-enable") {
        for ((block, occurrence) in toReload) {
            val (effect, holder) = block
            RefreshTracing.measure({ "reload-enable:${holder.holder.id}" }) {
                effect.enable(this, holder, occurrence = occurrence, isReload = true)
            }
        }
    }
    if (tracePlayer != null) {
        plugin.logger.info("Effects refresh: player=${tracePlayer.name} before=${before.size} after=${after.size} " +
                "added=${added.size} removed=${removed.size} reloaded=${toReload.size} " +
                "conditions_us=${(calculated - started) / 1000} total_us=${(System.nanoTime() - started) / 1000} " +
                "source=$source refresh_total_us=${(System.nanoTime() - refreshStarted) / 1000} " +
                "holder_rebuilds=${RefreshTracing.rebuildCount()} phases=[${RefreshTracing.phaseSummary()}] " +
                "top=[${RefreshTracing.summary()}]")
    }
}


/**
 * Removes all elements from the given [other] list that are contained in this list.
 *
 * Elements are only removed as many times as they are present.
 */
inline infix fun <reified T> Collection<T>.without(other: Collection<T>): List<T> {
    val counts = HashMap<T, Int>(other.size)
    for (element in other) {
        counts[element] = (counts[element] ?: 0) + 1
    }
    return filter { element ->
        val count = counts[element]
        if (count != null && count > 0) {
            counts[element] = count - 1
            false
        } else {
            true
        }
    }
}
