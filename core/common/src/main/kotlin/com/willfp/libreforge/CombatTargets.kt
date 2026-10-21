package com.willfp.libreforge

import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.Cancellable
import org.bukkit.event.entity.EntityDamageByEntityEvent

/** Opt-in recipient policy. Other holders keep their existing behaviour. Main thread only. */
interface CombatTargetHolder {
    /** Opaque original session; opt-in holders may reuse their existing session authority. */
    fun combatTargetSession(actor: Player): Any? = Unit
    fun allowsCombatTarget(actor: Player, target: LivingEntity, allowDead: Boolean, defensive: Boolean): Boolean
    /** A death-triggered cloud is an existing posthumous mechanic, not a new live attack. */
    fun allowsPosthumousCombatTarget(actor: Player, target: LivingEntity): Boolean = false
}

/** Scoped to one effect, never inferred from damage attribution (use_source can be false). */
object CombatTargets : org.bukkit.event.Listener {
    class Context internal constructor(private val holder: CombatTargetHolder, val actor: Player, internal val posthumous: Boolean = false) {
        private val session = holder.combatTargetSession(actor)
        fun allows(target: LivingEntity, allowDead: Boolean = false, defensive: Boolean = false) =
            if (posthumous) holder.allowsPosthumousCombatTarget(actor, target)
            else session != null && holder.combatTargetSession(actor) == session &&
                holder.allowsCombatTarget(actor, target, allowDead, defensive)
    }
    private fun policy(holder: Holder, depth: Int = 0): CombatTargetHolder? = when {
        depth > 16 -> null
        holder is CombatTargetHolder -> holder
        holder is NestedHolder -> policy(holder.parent.holder, depth + 1)
        else -> null
    }
    private val current = ThreadLocal<Context?>()
    private var registered = false
    private data class Bound(val entity: org.bukkit.entity.Entity, val context: Context)
    private val entities = mutableMapOf<java.util.UUID, Bound>()
    fun register() {
        if (registered) return
        plugin.eventManager.registerListener(this)
        registered = true
    }
    /** Only owned ephemeral effect entities; cap failure removes the entity rather than losing its policy. */
    fun bind(entity: org.bukkit.entity.Entity, context: Context?): Boolean {
        if (context == null) return true
        if (entities.size >= 512) { entity.remove(); return false }
        entities[entity.uniqueId] = Bound(entity, context)
        return true
    }
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun cloud(event: org.bukkit.event.entity.AreaEffectCloudApplyEvent) {
        val context = entities[event.entity.uniqueId]?.context ?: return
        event.affectedEntities.removeIf { !context.allows(it) }
    }
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun lightning(event: org.bukkit.event.weather.LightningStrikeEvent) {
        val context = current.get() ?: return
        if (!bind(event.lightning, context)) event.isCancelled = true
    }
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun damage(event: org.bukkit.event.entity.EntityDamageEvent) {
        val context = (event as? EntityDamageByEntityEvent)?.damager?.uniqueId?.let { entities[it]?.context } ?: current.get() ?: return
        val target = event.entity as? LivingEntity ?: return
        if (!context.allows(target)) event.isCancelled = true
    }
    @org.bukkit.event.EventHandler
    fun removed(event: com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent) { entities.remove(event.entity.uniqueId) }
    private fun retire(predicate: (Bound) -> Boolean) {
        val removed = entities.values.filter(predicate)
        removed.forEach { entities.remove(it.entity.uniqueId) }
        removed.forEach { it.entity.remove() }
    }
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    fun chunk(event: org.bukkit.event.world.ChunkUnloadEvent) {
        retire { it.entity.world == event.world && (it.entity.location.blockX shr 4) == event.chunk.x && (it.entity.location.blockZ shr 4) == event.chunk.z }
    }
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    fun world(event: org.bukkit.event.world.WorldUnloadEvent) { retire { it.entity.world == event.world } }
    @org.bukkit.event.EventHandler
    fun respawn(event: org.bukkit.event.player.PlayerRespawnEvent) { retire { it.context.actor.uniqueId == event.player.uniqueId && !it.context.posthumous } }
    @org.bukkit.event.EventHandler
    fun changedWorld(event: org.bukkit.event.player.PlayerChangedWorldEvent) { retire { it.context.actor.uniqueId == event.player.uniqueId } }
    @org.bukkit.event.EventHandler
    fun quit(event: org.bukkit.event.player.PlayerQuitEvent) { retire { it.context.actor.uniqueId == event.player.uniqueId } }
    @org.bukkit.event.EventHandler
    fun disable(event: org.bukkit.event.server.PluginDisableEvent) {
        if (event.plugin === plugin) { retire { true }; registered = false; current.remove() }
    }
    fun capture(): Context? = current.get()
    fun context(data: TriggerData): Context? {
        if (data.combatTargetsPrepared) return data.combatTargetContext
        data.combatTargetsPrepared = true
        val holder = policy(data.holder.holder) ?: return null
        data.combatTargetContext = data.originalPlayer?.let {
            Context(holder, it, (data.event as? org.bukkit.event.entity.EntityDeathEvent)?.entity === it)
        }
        return data.combatTargetContext
    }
    fun allows(target: LivingEntity): Boolean = current.get()?.allows(target) ?: true
    fun allows(data: TriggerData, target: LivingEntity): Boolean = context(data)?.allows(target) ?: true
    fun anchor(data: TriggerData): Boolean {
        val holder = policy(data.holder.holder) ?: return true
        val actor = data.originalPlayer ?: return false
        val event = data.event
        val defensive = event is EntityDamageByEntityEvent && event.entity === actor
        val target = if (defensive) {
            val damager = (event as EntityDamageByEntityEvent).damager
            damager as? LivingEntity ?: (damager as? Projectile)?.shooter as? LivingEntity
        } else data.victim?.takeUnless { it === actor }
        if (target == null) return true // Non-combat utility effects retain their existing semantics.
        if (event is Cancellable && event.isCancelled) return false
        return context(data)?.allows(target, true, defensive) == true
    }
    fun <T> with(context: Context?, action: () -> T): T {
        val previous = current.get()
        current.set(context)
        try { return action() } finally { if (previous == null) current.remove() else current.set(previous) }
    }
}
