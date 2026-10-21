package com.willfp.libreforge.visual

import com.willfp.libreforge.plugin
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.util.Vector
import org.bukkit.event.*
import org.bukkit.event.player.*
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/** Observation of accepted native mechanics, never a damage or projectile authority. */
enum class VisualPhase { ACTIVATE, ACTIVE, LAUNCH, HIT, DAMAGE_CONFIRMED, MISS, REJECT, CHARGE_SPENT, DEACTIVATE, INTERRUPT }
class VisualContext(val sessionId:UUID, val provider:String, val sourceId:String, val level:Int, val caster:UUID,
                    origin:Location, direction:Vector, val durationMillis:Long, val charges:Int, val itemInstance:UUID?=null, val radius:Double=0.0) {
    private val capturedOrigin=origin.clone()
    private val capturedDirection=direction.clone()
    val origin:Location get()=capturedOrigin.clone()
    val direction:Vector get()=capturedDirection.clone()
}
class VisualEvent(val phase:VisualPhase, val instance:UUID, val receiver:UUID?, val impact:UUID,
                  location:Location?, val reason:String, val index:Int, val remaining:Int, val confirmedDamage:Double?) {
    private val capturedLocation=location?.clone()
    val location:Location? get()=capturedLocation?.clone()
}
interface VisualBackend {
    fun emit(session:VisualSession,event:VisualEvent):Boolean
    fun close(session:VisualSession)
    fun reload()
}
/** Per-instance/receptor deduplication, not a single suppression flag for a whole activation. */
class VisualSession internal constructor(val profile:String,val context:VisualContext,
    private val emitTo:(VisualSession,VisualEvent)->Boolean,private val cleanup:(VisualSession)->Unit) {
    private val emitted=mutableSetOf<String>()
    private val outcomes=mutableMapOf<UUID,VisualPhase>()
    var isClosed=false;private set
    private var terminalInProgress=false
    internal var expiry:BukkitTask?=null
    fun emit(phase:VisualPhase,instance:UUID=context.sessionId,receiver:UUID?=null,impact:UUID=instance,
             location:Location?=null,reason:String="",index:Int=-1,remaining:Int=0,confirmedDamage:Double?=null):Boolean {
        if(isClosed || terminalInProgress || ((emitted.size>=256 || outcomes.size>=64) && phase !in setOf(VisualPhase.DEACTIVATE,VisualPhase.INTERRUPT))) return false
        if(phase==VisualPhase.DAMAGE_CONFIRMED && (receiver==null || confirmedDamage==null || !confirmedDamage.isFinite() || confirmedDamage<=0)) return false
        if(phase!=VisualPhase.DAMAGE_CONFIRMED && confirmedDamage!=null) return false
        if(phase in setOf(VisualPhase.MISS,VisualPhase.REJECT,VisualPhase.INTERRUPT) && reason.isEmpty()) return false
        if(phase==VisualPhase.DAMAGE_CONFIRMED && outcomes[instance]==VisualPhase.MISS) return false
        if(phase==VisualPhase.CHARGE_SPENT && (index !in 0 until context.charges || remaining !in 0..context.charges)) return false
        if(phase==VisualPhase.MISS && outcomes[instance]!=VisualPhase.LAUNCH) return false
        if(phase==VisualPhase.HIT && outcomes[instance]==VisualPhase.MISS) return false
        val key=when(phase) {
            VisualPhase.ACTIVATE,VisualPhase.ACTIVE -> phase.name
            VisualPhase.CHARGE_SPENT -> "charge:$index"
            else -> "$phase:$instance:$receiver:$impact"
        }
        if(!emitted.add(key)) return false
        if(phase in setOf(VisualPhase.LAUNCH,VisualPhase.HIT,VisualPhase.MISS)) {
            if(phase==VisualPhase.LAUNCH && instance in outcomes) return false
            outcomes[instance]=phase
        }
        val terminal=phase==VisualPhase.DEACTIVATE || phase==VisualPhase.INTERRUPT
        if(terminal)terminalInProgress=true
        val result=try { emitTo(this,VisualEvent(phase,instance,receiver,impact,location,reason,index,remaining,confirmedDamage)) }
            finally { if(terminal) { isClosed=true;expiry?.cancel();expiry=null;cleanup(this) } }
        return result
    }
    fun close(reason:String,interrupted:Boolean=true) { emit(if(interrupted)VisualPhase.INTERRUPT else VisualPhase.DEACTIVATE,reason=reason) }
}
object VisualPhases : Listener {
    private var backend:VisualBackend?=null
    private val sessions=linkedMapOf<UUID,VisualSession>()
    private var registered=false
    private val warned=mutableSetOf<String>()
    fun install(value:VisualBackend) {
        reset("provider_replace");backend=value
        if(!registered) { Bukkit.getPluginManager().registerEvents(this,plugin);registered=true }
    }
    fun open(profile:String,context:VisualContext):VisualSession? = try { openSession(profile,context) }
        catch(error:RuntimeException) { sessions.remove(context.sessionId)?.close("visual_failure");failure(profile,error);null }
        catch(error:LinkageError) { sessions.remove(context.sessionId)?.close("visual_failure");failure(profile,error);null }
    private fun openSession(profile:String,context:VisualContext):VisualSession? {
        if(!Bukkit.isPrimaryThread() || context.durationMillis !in 50L..30000L || sessions.size>=128 || context.sessionId in sessions) return null
        val target=backend
        if(target==null) {
            if(warned.size<32 && warned.add(profile))plugin.logger.warning("Visual profile $profile unavailable: MythicMobs backend not enabled")
            return null
        }
        val session=VisualSession(profile,context,{s,e ->
            try { target.emit(s,e) } catch(error:RuntimeException) { failure(profile,error);false }
                catch(error:LinkageError) { failure(profile,error);false }
        },{s -> sessions.remove(s.context.sessionId);runCatching { target.close(s) }.onFailure { failure(profile,it) }})
        sessions[context.sessionId]=session
        session.expiry=Bukkit.getScheduler().runTaskLater(plugin,Runnable { session.close("duration",false) },(context.durationMillis+49)/50)
        return session
    }
    private fun failure(profile:String,error:Throwable) {
        if(warned.size<32 && warned.add("error:$profile"))plugin.logger.log(java.util.logging.Level.WARNING,"Mythic visual $profile degraded locally",error)
    }
    fun interruptCaster(caster:UUID,reason:String) { sessions.values.filter{it.context.caster==caster}.toList().forEach{it.close(reason)} }
    fun interruptProvider(provider:String,reason:String) { sessions.values.filter{it.context.provider.equals(provider,true)}.toList().forEach{it.close(reason)} }
    fun reset(reason:String) { sessions.values.toList().forEach{it.close(reason)};warned.clear() }
    fun reload() { reset("libreforge_reload");backend?.reload() }
    @EventHandler fun quit(e:PlayerQuitEvent)=interruptCaster(e.player.uniqueId,"quit")
    @EventHandler fun death(e:PlayerDeathEvent)=interruptCaster(e.entity.uniqueId,"death")
    @EventHandler fun world(e:PlayerChangedWorldEvent)=interruptCaster(e.player.uniqueId,"world_change")
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    fun slot(e:PlayerItemHeldEvent) { if(e.newSlot!=e.previousSlot)interruptCaster(e.player.uniqueId,"item_change") }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    fun swap(e:PlayerSwapHandItemsEvent)=interruptCaster(e.player.uniqueId,"item_change")
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    fun inventory(e:org.bukkit.event.inventory.InventoryClickEvent) {
        val player=e.whoClicked as? org.bukkit.entity.Player ?: return
        if(e.hotbarButton==player.inventory.heldItemSlot || (e.clickedInventory?.type==org.bukkit.event.inventory.InventoryType.PLAYER && e.slot==player.inventory.heldItemSlot))interruptCaster(player.uniqueId,"item_change")
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    fun drag(e:org.bukkit.event.inventory.InventoryDragEvent) {
        val player=e.whoClicked as? org.bukkit.entity.Player ?: return
        if(e.rawSlots.any{e.view.getInventory(it)?.type==org.bukkit.event.inventory.InventoryType.PLAYER && e.view.convertSlot(it)==player.inventory.heldItemSlot})interruptCaster(player.uniqueId,"item_change")
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    fun drop(e:PlayerDropItemEvent) { if(e.player.inventory.itemInMainHand.type.isAir)interruptCaster(e.player.uniqueId,"item_change") }
    @EventHandler fun disabled(e:PluginDisableEvent) {
        if(e.plugin.name=="MythicMobs" || e.plugin===plugin) { reset("provider_disable");backend=null }
        else sessions.values.filter{it.context.provider.equals(e.plugin.name,true)}.toList().forEach{it.close("provider_disable")}
    }
}
