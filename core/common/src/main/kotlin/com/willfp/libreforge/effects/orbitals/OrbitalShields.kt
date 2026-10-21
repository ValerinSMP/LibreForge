package com.willfp.libreforge.effects.orbitals

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.integrations.antigrief.AntigriefManager
import com.willfp.libreforge.*
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.impl.DamageMultiplierBypass
import com.willfp.libreforge.triggers.*
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.entity.*
import org.bukkit.event.player.*
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.inventory.*
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import java.util.IdentityHashMap
import java.util.UUID
import kotlin.math.*

object EffectOrbitalShields : Effect<NoCompileData>("orbital_shields") {
    override val supportsDelay = false
    override val description = "Invokes charge shields from the equipped item using the configured physical input."
    override val categories = setOf("combat", "visual")
    override val parameters = setOf(TriggerParameter.PLAYER, TriggerParameter.EVENT)
    override val arguments = arguments {
        optional("activation",description="Physical input for this effect only.",type=ArgType.STRING,default="sneak_start",choices=OrbitalInput.modes.toList())
        optional("send_cooldown_message",description="Show the optional bounded actionbar notice.",type=ArgType.BOOLEAN,default="false")
        optional("cooldown_message",description="Local cooldown text, supports %seconds%.",type=ArgType.STRING)
        optional("cooldown_message_duration",description="Temporary notice duration in milliseconds (50..10000).",type=ArgType.INT,default="1000")
        optional("source_level","Captured source tier for visual metadata only.",type=ArgType.EXPRESSION)
        require("profile", "Specify an orbital visual profile.", type=ArgType.STRING)
        for (key in listOf("amount", "radius", "duration", "proc_cooldown")) require(key, "Specify $key.", type=ArgType.EXPRESSION)
    }
    override fun onTrigger(config: Config, data: TriggerData, compileData: NoCompileData): Boolean {
        val player = data.player ?: return false
        if (!OrbitalInput.matches(config.getStringOrNull("activation") ?: "sneak_start", player, data.event)) return false
        return OrbitalShields.invoke(config, data)
    }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    fun damage(event: EntityDamageByEntityEvent) = OrbitalShields.damage(event)
    @EventHandler fun quit(event: PlayerQuitEvent) = OrbitalShields.close(event.player.uniqueId,"quit")
    @EventHandler fun death(event: PlayerDeathEvent) = OrbitalShields.close(event.entity.uniqueId,"death")
    @EventHandler fun world(event: PlayerChangedWorldEvent) = OrbitalShields.close(event.player.uniqueId,"world_change")
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    fun slot(event: PlayerItemHeldEvent) { if(event.newSlot != event.previousSlot) OrbitalShields.close(event.player.uniqueId) }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    fun swap(event: PlayerSwapHandItemsEvent) = OrbitalShields.close(event.player.uniqueId)
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    fun inventory(event: InventoryClickEvent) {
        val player=event.whoClicked as? Player ?: return
        if(event.hotbarButton==player.inventory.heldItemSlot ||
            (event.clickedInventory?.type==InventoryType.PLAYER && event.slot==player.inventory.heldItemSlot)) OrbitalShields.close(player.uniqueId)
    }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    fun drag(event: InventoryDragEvent) {
        val player=event.whoClicked as? Player ?: return
        if(event.rawSlots.any { event.view.getInventory(it)?.type==InventoryType.PLAYER && event.view.convertSlot(it)==player.inventory.heldItemSlot }) OrbitalShields.close(player.uniqueId)
    }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    fun drop(event: PlayerDropItemEvent) = OrbitalShields.dropped(event.player,event.itemDrop.itemStack)
    @EventHandler fun disable(event: PluginDisableEvent) = OrbitalShields.disabled(event)
}

object OrbitalShields {
    private fun now() = System.nanoTime()/1_000_000L
    private val gestures = com.willfp.libreforge.triggers.impl.PhysicalGestures()
    private val charges=OrbitalCharges { now() }
    private val boundary=OrbitalDamageBoundary(charges)
    private val key=NamespacedKey("libreforge","orbital_item")
    private val petKey=NamespacedKey("vpets","pet-owner")
    private class Active(val player: Player, val token: UUID, val world: UUID, val slot: Int,
        val item: UUID, val holder: ProvidedHolder, val context: CombatTargets.Context?, var remaining: Int, val visual: com.willfp.libreforge.visual.VisualSession?)
    private val active=mutableMapOf<UUID,Active>()
    private val pending=IdentityHashMap<EntityDamageEvent,Pair<Active,OrbitalCharges.Lease>>()
    private var task:BukkitTask?=null
    private var commandContext:ThreadLocal<*>?=null
    private var guardResolved=false
    private fun gameplay():Boolean {
        if (!guardResolved) {
            guardResolved=true
            try {
                val field=Class.forName("net.minecraft.commands.Commands").getDeclaredField("CURRENT_EXECUTION_CONTEXT")
                check(java.lang.reflect.Modifier.isStatic(field.modifiers) && field.trySetAccessible())
                commandContext=field.get(null) as ThreadLocal<*>
            } catch(failure:ReflectiveOperationException) { plugin.logger.log(java.util.logging.Level.WARNING,"Orbital shields unavailable: native command origin guard",failure) }
              catch(failure:RuntimeException) { plugin.logger.log(java.util.logging.Level.WARNING,"Orbital shields unavailable: native command origin guard",failure) }
        }
        return commandContext?.get()==null && commandContext!=null
    }
    private fun root(provided:ProvidedHolder):ProvidedHolder {
        var result=provided
        repeat(16) { result=(result.holder as? NestedHolder)?.parent ?: return result }
        return result
    }
    fun invoke(config:Config,data:TriggerData):Boolean {
        if (!Bukkit.isPrimaryThread() || !gameplay()) return false
        val player=data.player ?: return false
        if (!player.isOnline || player.isDead) return false
        val holder=root(data.holder);val supplied=holder.provider as? ItemStack ?: return false
        val held=player.inventory.itemInMainHand
        if (held.amount!=1 || supplied!=held) return false
        if (!gestures.claim(player.uniqueId, Bukkit.getCurrentTick())) return false
        val remaining=charges.remainingMillis(player.uniqueId)
        if (remaining > 0) {
            if (charges.claimNotice(player.uniqueId, config.getBoolOrNull("send_cooldown_message") == true))
                OrbitalCooldownNotice.send(player, config, (remaining + 999L) / 1000L)
            return false
        }
        if (player.uniqueId in active) return false
        val amount=config.getDoubleFromExpression("amount",data);val radius=config.getDoubleFromExpression("radius",data)
        val duration=config.getDoubleFromExpression("duration",data);val cooldown=config.getDoubleFromExpression("proc_cooldown",data)
        if (!amount.isFinite() || amount != floor(amount) || amount !in 1.0..6.0 || !radius.isFinite() || radius !in 0.5..3.0 ||
            !duration.isFinite() || duration !in 0.05..30.0 || !cooldown.isFinite() || cooldown !in 0.05..120.0) return false
        val session=charges.start(player.uniqueId,amount.toInt(),(duration*1000).toLong(),(cooldown*1000).toLong()) ?: return false
        var visual:com.willfp.libreforge.visual.VisualSession?=null
        try {
            val meta=held.itemMeta ?: error("Missing item metadata")
            val item=meta.persistentDataContainer.get(key,PersistentDataType.STRING)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: UUID.randomUUID()
            meta.persistentDataContainer.set(key,PersistentDataType.STRING,item.toString());held.itemMeta=meta
            player.inventory.setItemInMainHand(held)
            visual=com.willfp.libreforge.visual.VisualPhases.open(config.getString("profile"),com.willfp.libreforge.visual.VisualContext(
                session.token,holder.holder.id.namespace,holder.holder.id.toString(),if(config.has("source_level"))runCatching{config.getIntFromExpression("source_level",data)}.getOrDefault(-1) else 0,player.uniqueId,player.location,player.eyeLocation.direction,(duration*1000).toLong(),amount.toInt(),item,radius))
            active[player.uniqueId]=Active(player,session.token,player.world.uid,player.inventory.heldItemSlot,item,holder,CombatTargets.context(data),amount.toInt(),visual)
            visual?.emit(com.willfp.libreforge.visual.VisualPhase.ACTIVATE)
            visual?.emit(com.willfp.libreforge.visual.VisualPhase.ACTIVE)
            if(task==null) task=Bukkit.getScheduler().runTaskTimer(plugin,Runnable {
                try { tick() } catch(failure:RuntimeException) { reset();plugin.logger.log(java.util.logging.Level.WARNING,"Orbital shields stopped after runtime failure",failure) }
                  catch(failure:LinkageError) { reset();plugin.logger.log(java.util.logging.Level.WARNING,"Orbital shields stopped after runtime linkage failure",failure) }
            },1L,1L)
            return true
        } catch(failure:RuntimeException) {
            visual?.close("invocation_failure");active.remove(player.uniqueId);charges.abort(player.uniqueId,session.token)
            plugin.logger.log(java.util.logging.Level.WARNING,"Orbital shield invocation failed",failure)
            return false
        } catch(failure:LinkageError) {
            visual?.close("invocation_failure");active.remove(player.uniqueId);charges.abort(player.uniqueId,session.token)
            plugin.logger.log(java.util.logging.Level.WARNING,"Orbital shield invocation unavailable",failure)
            return false
        }
    }
    private fun valid(state:Active):Boolean {
        val player=state.player
        if (!player.isOnline || player.isDead || player.world.uid!=state.world || player.inventory.heldItemSlot!=state.slot ||
            charges.current(player.uniqueId)?.token!=state.token) return false
        val item=player.inventory.itemInMainHand
        if (item.amount!=1 || item.itemMeta?.persistentDataContainer?.get(key,PersistentDataType.STRING)!=state.item.toString()) return false
        return player.toDispatcher().holders.any { it.holder === state.holder.holder }
    }
    private fun eligible(state:Active, attacker:LivingEntity):Boolean {
        val player=state.player
        if(attacker===player || !attacker.isValid || attacker.isDead || attacker.isInvulnerable || attacker.world!=player.world ||
            attacker is ArmorStand || attacker.type.name=="MANNEQUIN" || attacker.hasMetadata("NPC") || player.hasMetadata("NPC") ||
            (attacker is Tameable && (attacker.isTamed || attacker.owner!=null)) || attacker.persistentDataContainer.has(petKey,PersistentDataType.STRING)) return false
        if (attacker is Player && (!attacker.isOnline || attacker.gameMode in setOf(GameMode.CREATIVE,GameMode.SPECTATOR) || !attacker.world.pvp || !AntigriefManager.canInjure(attacker,player))) return false
        if (sequenceOf(player.scoreboard,Bukkit.getScoreboardManager()?.mainScoreboard).filterNotNull().any { board ->
                val team=board.getEntryTeam(player.name); team!=null && team==board.getEntryTeam(if(attacker is Player)attacker.name else attacker.uniqueId.toString()) }) return false
        return state.context?.allows(attacker,defensive=true) ?: true
    }
    fun damage(event:EntityDamageByEntityEvent) {
        val player=event.entity as? Player ?: return
        val state=active[player.uniqueId] ?: return
        if (pending.containsKey(event) || event.isCancelled || player.isInvulnerable || player.gameMode in setOf(GameMode.CREATIVE,GameMode.SPECTATOR) ||
            DamageMultiplierBypass.current!=null || !gameplay() || !valid(state)) return
        val attacker=directOrbitalAttacker(event) ?: return
        if (!eligible(state,attacker)) return
        try {
            val lease=boundary.block(player.uniqueId,event) ?: return
            pending[event]=state to lease
        } catch(failure:RuntimeException) { close(player.uniqueId);plugin.logger.log(java.util.logging.Level.WARNING,"Orbital damage boundary stopped",failure) }
          catch(failure:LinkageError) { close(player.uniqueId);plugin.logger.log(java.util.logging.Level.WARNING,"Orbital damage boundary unavailable",failure) }
    }
    private fun tick() {
        val resolved=pending.values.toList();pending.clear()
        for((state,lease) in resolved) {
            val index=boundary.finish(lease)
            if(index!=null && active[lease.player]===state) {
                state.remaining--
                state.visual?.emit(com.willfp.libreforge.visual.VisualPhase.CHARGE_SPENT,index=index,remaining=state.remaining)
                if(state.remaining==0)close(lease.player,"charges_spent",false)
            }
        }
        for((id,state) in active.toMap()) {
            val expires=charges.current(id)?.expires
            if(expires!=null && expires<=now()) { close(id,"duration",false);continue }
            if(!valid(state)) { close(id);continue }
        }
        if(active.isEmpty()) { task?.cancel();task=null }
    }
    fun dropped(player:Player,item:ItemStack) {
        val state=active[player.uniqueId] ?: return
        if(item.itemMeta?.persistentDataContainer?.get(key,PersistentDataType.STRING)==state.item.toString())close(player.uniqueId)
    }
    fun close(player:UUID) = close(player,"item_change",true)
    internal fun close(player:UUID,reason:String,interrupted:Boolean=true) { active.remove(player)?.visual?.close(reason,interrupted);charges.close(player) }
    fun reset() { active.keys.toList().forEach { close(it,"provider_reload") };pending.clear();task?.cancel();task=null }
    fun disabled(event:PluginDisableEvent) {
        if(event.plugin===plugin) { reset();return }
        active.filterValues { it.holder.holder.javaClass.classLoader===event.plugin.javaClass.classLoader }.keys.toList().forEach { close(it) }
    }
}
