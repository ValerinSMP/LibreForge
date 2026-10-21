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
    override val description = "Invokes bounded charge shields from the equipped item when sneaking starts."
    override val categories = setOf("combat", "visual")
    override val parameters = setOf(TriggerParameter.PLAYER, TriggerParameter.EVENT)
    override val arguments = arguments {
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
    @EventHandler fun quit(event: PlayerQuitEvent) = OrbitalShields.close(event.player.uniqueId)
    @EventHandler fun death(event: PlayerDeathEvent) = OrbitalShields.close(event.entity.uniqueId)
    @EventHandler fun world(event: PlayerChangedWorldEvent) = OrbitalShields.close(event.player.uniqueId)
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

internal data class OrbitalProfile(val item: Material, val scale: Float, val height: Double,
    val speed: Double, val particle: Particle, val invoke: String, val breaking: String, val expiry: String, val volume: Float, val pitch: Float)

/** The representation owns only displays; charge and damage decisions stay in OrbitalShields. */
internal class OrbitalRig(private val player: Player, count: Int, private val radius: Double, private val profile: OrbitalProfile) {
    private val displays = arrayOfNulls<ItemDisplay>(count)
    init {
        try { repeat(count) { index ->
            val location = frame(player.location, index, count, 0.0, radius, profile.height, profile.speed)
            check(player.world.isChunkLoaded(location.blockX shr 4,location.blockZ shr 4)) { "Display chunk unavailable" }
            displays[index] = player.world.spawn(location, ItemDisplay::class.java) { display ->
                displays[index]=display
                display.isPersistent=false; display.isInvulnerable=true; display.setGravity(false)
                display.isSilent=true; display.setItemStack(ItemStack(profile.item))
                display.itemDisplayTransform=ItemDisplay.ItemDisplayTransform.FIXED
                display.teleportDuration=2; display.interpolationDuration=2; display.viewRange=0.5f
                val transformation=display.transformation; transformation.scale.set(profile.scale)
                display.transformation=transformation
            }
            check(displays[index]?.isValid==true) { "Display spawn rejected" }
        } } catch (failure: RuntimeException) { close(); throw failure }
            catch (failure: LinkageError) { close(); throw failure }
    }
    fun render(ageTicks: Double): Boolean {
        for ((index, display) in displays.withIndex()) if (display != null) {
            if (!display.isValid) return false
            val next=frame(player.location,index,displays.size,ageTicks,radius,profile.height,profile.speed)
            if (!next.world.isChunkLoaded(next.blockX shr 4,next.blockZ shr 4)) return false
            display.teleport(next)
        }
        return true
    }
    fun breakShield(index: Int) {
        val display=displays.getOrNull(index) ?: return
        val location=display.location; display.remove(); displays[index]=null
        player.world.spawnParticle(profile.particle,location,6,0.12,0.18,0.12,0.02)
        sound(profile.breaking,location)
    }
    fun sound(name: String, location: Location = player.location) {
        if (name.isNotEmpty()) player.playSound(location,name,SoundCategory.PLAYERS,profile.volume,profile.pitch)
    }
    fun expire() { sound(profile.expiry); close() }
    fun close() { for(index in displays.indices) { runCatching { displays[index]?.remove() }; displays[index]=null } }
    companion object {
        fun frame(origin: Location, index: Int, count: Int, age: Double, radius: Double, height: Double, speed: Double): Location {
            val angle=2*PI*index/count + age*speed
            return origin.clone().add(cos(angle)*radius,height,sin(angle)*radius).apply { yaw=Math.toDegrees(angle).toFloat()+90; pitch=0f }
        }
    }
}

object OrbitalShields {
    private fun now() = System.nanoTime()/1_000_000L
    private val gestures = com.willfp.libreforge.triggers.impl.PhysicalGestures()
    private val charges=OrbitalCharges { now() }
    private val boundary=OrbitalDamageBoundary(charges)
    private val key=NamespacedKey("libreforge","orbital_item")
    private val petKey=NamespacedKey("vpets","pet-owner")
    private class Active(val player: Player, val token: UUID, val world: UUID, val slot: Int,
        val item: UUID, val holder: ProvidedHolder, val context: CombatTargets.Context?, val since: Long, val rig: OrbitalRig)
    private val active=mutableMapOf<UUID,Active>()
    private val pending=IdentityHashMap<EntityDamageEvent,Pair<Active,OrbitalCharges.Lease>>()
    private val profiles=mutableMapOf<String,OrbitalProfile?>()
    private val profilesYml by lazy { OrbitalProfilesYml(plugin) }
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
    private fun profile(id:String):OrbitalProfile? {
        if (!id.matches(Regex("[a-z0-9_-]{1,48}")) || (!profiles.containsKey(id) && profiles.size>=8)) return null
        if (profiles.containsKey(id)) return profiles[id]
        val config=profilesYml.getSubsectionOrNull("profiles.$id")
        val result=config?.let {
            runCatching {
                val item=Material.matchMaterial(it.getString("item"))?.takeIf { m -> m.isItem && !m.isAir } ?: error("Invalid display item")
                val scale=it.getDouble("scale");val height=it.getDouble("height");val speed=it.getDouble("speed")
                val volume=it.getDouble("volume");val pitch=it.getDouble("pitch")
                require(scale.isFinite() && scale in 0.1..1.0 && height.isFinite() && height in 0.0..2.5 && speed.isFinite() && speed in 0.01..0.3)
                require(volume.isFinite() && volume in 0.0..1.0 && pitch.isFinite() && pitch in 0.5..2.0)
                fun sound(path:String)=it.getString(path).also { name -> require(name.isEmpty() || NamespacedKey.fromString(name)?.let { k -> org.bukkit.Registry.SOUNDS.get(k) }!=null) }
                val particle=Particle.valueOf(it.getString("break-particle").uppercase())
                require(particle.dataType==Void::class.java) { "Break particle must not require extra data" }
                OrbitalProfile(item,scale.toFloat(),height,speed,particle,sound("invoke-sound"),sound("break-sound"),sound("expire-sound"),volume.toFloat(),pitch.toFloat())
            }.onFailure { failure -> plugin.logger.warning("Orbital profile $id unavailable: ${failure.message}") }.getOrNull()
        }
        profiles[id]=result
        return result
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
        val visual=profile(config.getString("profile")) ?: return false
        val amount=config.getDoubleFromExpression("amount",data);val radius=config.getDoubleFromExpression("radius",data)
        val duration=config.getDoubleFromExpression("duration",data);val cooldown=config.getDoubleFromExpression("proc_cooldown",data)
        if (!amount.isFinite() || amount != floor(amount) || amount !in 1.0..6.0 || !radius.isFinite() || radius !in 0.5..3.0 ||
            !duration.isFinite() || duration !in 0.05..30.0 || !cooldown.isFinite() || cooldown !in 0.05..120.0) return false
        val session=charges.start(player.uniqueId,amount.toInt(),(duration*1000).toLong(),(cooldown*1000).toLong()) ?: return false
        var rig:OrbitalRig?=null
        try {
            val meta=held.itemMeta ?: error("Missing item metadata")
            val item=meta.persistentDataContainer.get(key,PersistentDataType.STRING)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: UUID.randomUUID()
            meta.persistentDataContainer.set(key,PersistentDataType.STRING,item.toString());held.itemMeta=meta
            player.inventory.setItemInMainHand(held)
            rig=OrbitalRig(player,amount.toInt(),radius,visual)
            active[player.uniqueId]=Active(player,session.token,player.world.uid,player.inventory.heldItemSlot,item,holder,CombatTargets.context(data),now(),rig)
            rig.sound(visual.invoke)
            if(task==null) task=Bukkit.getScheduler().runTaskTimer(plugin,Runnable {
                try { tick() } catch(failure:RuntimeException) { reset();plugin.logger.log(java.util.logging.Level.WARNING,"Orbital shields stopped after runtime failure",failure) }
                  catch(failure:LinkageError) { reset();plugin.logger.log(java.util.logging.Level.WARNING,"Orbital shields stopped after runtime linkage failure",failure) }
            },1L,1L)
            return true
        } catch(failure:RuntimeException) {
            rig?.close();active.remove(player.uniqueId);charges.abort(player.uniqueId,session.token)
            plugin.logger.log(java.util.logging.Level.WARNING,"Orbital shield invocation failed",failure)
            return false
        } catch(failure:LinkageError) {
            rig?.close();active.remove(player.uniqueId);charges.abort(player.uniqueId,session.token)
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
    private var frame=0
    private fun tick() {
        val resolved=pending.values.toList();pending.clear()
        for((state,lease) in resolved) {
            val index=boundary.finish(lease)
            if(index!=null && active[lease.player]===state && state.player.isOnline && state.player.world.uid==state.world) state.rig.breakShield(index)
        }
        frame++
        for((id,state) in active.toMap()) {
            val expires=charges.current(id)?.expires
            if(expires!=null && expires<=now()) { state.rig.expire();close(id);continue }
            if(!valid(state)) { close(id);continue }
            if(frame%2==0 && !state.rig.render((now()-state.since)/50.0))close(id)
        }
        if(active.isEmpty()) { task?.cancel();task=null }
    }
    fun dropped(player:Player,item:ItemStack) {
        val state=active[player.uniqueId] ?: return
        if(item.itemMeta?.persistentDataContainer?.get(key,PersistentDataType.STRING)==state.item.toString())close(player.uniqueId)
    }
    fun close(player:UUID) { active.remove(player)?.rig?.close();charges.close(player) }
    fun reset() { active.keys.toList().forEach { close(it) };pending.clear();task?.cancel();task=null;profiles.clear() }
    fun disabled(event:PluginDisableEvent) {
        if(event.plugin===plugin) { reset();return }
        active.filterValues { it.holder.holder.javaClass.classLoader===event.plugin.javaClass.classLoader }.keys.toList().forEach { close(it) }
    }
}
