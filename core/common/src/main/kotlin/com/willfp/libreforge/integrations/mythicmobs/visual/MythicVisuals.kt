package com.willfp.libreforge.integrations.mythicmobs.visual

import com.willfp.eco.core.config.ConfigType
import com.willfp.eco.core.config.ExtendableConfig
import com.willfp.libreforge.plugin
import com.willfp.libreforge.visual.*
import io.lumine.mythic.api.mobs.GenericCaster
import io.lumine.mythic.api.skills.*
import io.lumine.mythic.bukkit.BukkitAdapter
import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.bukkit.events.*
import io.lumine.mythic.core.skills.SkillMetadataImpl
import io.lumine.mythic.core.skills.SkillMechanic
import io.lumine.mythic.core.skills.SkillTriggers
import org.bukkit.Bukkit
import org.bukkit.event.*
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal fun <T> resolveVisualSkills(names:List<String>,resolve:(String)->T?,missing:(String)->Unit):List<T> =
    names.take(8).mapNotNull { key -> resolve(key).also { if(it==null)missing(key) } }

private class MythicVisualsYml : ExtendableConfig("mythic-visuals",true,plugin,plugin.javaClass,"",ConfigType.YAML,"profiles")
/** Only Mythic owns geometry and resources. This adapter binds their lifetime to a native session. */
object MythicVisuals : VisualBackend, Listener {
    private const val SCOPE="ecosuite_visual_scope"
    private class Scope(val session:VisualSession) {
        val terminated=AtomicBoolean(false)
        val resources=IdentityHashMap<AutoCloseable,Int>()
    }
    private val scopes=IdentityHashMap<VisualSession,Scope>()
    private val config by lazy { MythicVisualsYml() }
    private var catalog=emptyMap<String,Map<VisualPhase,List<Skill>>>()
    private val warnings=mutableSetOf<String>()
    private var registered=false
    private var bindingObserved=false
    fun install() {
        if(!registered) { Bukkit.getPluginManager().registerEvents(this,plugin);registered=true }
        VisualPhases.install(this);reload()
    }
    override fun reload() {
        catalog=emptyMap();warnings.clear()
        if(!bindingObserved) { warn("bootstrap","Mythic reload required after LibreForge enables: ecosuite_visual_bind not loaded");return }
        val profiles=com.willfp.eco.core.config.Configs.fromFile(config.configFile,ConfigType.YAML).getSubsection("profiles");val prepared=linkedMapOf<String,Map<VisualPhase,List<Skill>>>()
        for(id in profiles.getKeys(false).take(32)) {
            if(profiles.getBoolOrNull("$id.enabled")==false) { prepared[id]=emptyMap();continue }
            val phases=profiles.getSubsection("$id.phases");val mapped=linkedMapOf<VisualPhase,List<Skill>>()
            for(name in phases.getKeys(false).take(10)) {
                val phase=runCatching { VisualPhase.valueOf(name.uppercase()) }.getOrNull()
                if(phase==null) { warn("$id/$name","unknown phase");continue }
                val skills=resolveVisualSkills(phases.getStrings(name),{ key -> MythicBukkit.inst().skillManager.getSkill(key).orElse(null) },
                    { key -> warn("$id/$name/$key","missing skill") })
                mapped[phase]=skills.toList()
            }
            prepared[id]=mapped.toMap()
        }
        catalog=prepared.toMap()
    }
    private fun warn(key:String,detail:String) {
        if(warnings.size<128 && warnings.add(key))plugin.logger.warning("Mythic visual $key unavailable: $detail")
    }
    override fun emit(session:VisualSession,event:VisualEvent):Boolean {
        val skills=catalog[session.profile]?.get(event.phase)
        if(skills==null) { if(session.profile !in catalog)warn(session.profile,"profile not prepared");return false }
        val caster=Bukkit.getPlayer(session.context.caster) ?: return false
        if(!caster.isOnline || caster.world!=session.context.origin.world)return false
        val receiver=event.receiver?.let { Bukkit.getEntity(it) }
        if(event.receiver!=null && receiver==null)return false
        val scope=scopes.getOrPut(session){Scope(session)}
        val impactLocation=if(event.phase==VisualPhase.CHARGE_SPENT) {
            val resource=scope.resources.entries.firstOrNull{it.value==event.index}?.key
            val position=(resource as? io.lumine.mythic.core.skills.projectiles.ProjectileBulletableTracker)?.currentLocation
            position?.let{BukkitAdapter.adapt(it).clone()} ?: event.location
        } else event.location
        if(event.phase==VisualPhase.CHARGE_SPENT)closeResources(scope,event.index)
        var success=true
        for(skill in skills) {
            val data=SkillMetadataImpl(SkillTriggers.API,GenericCaster(BukkitAdapter.adapt(caster)),BukkitAdapter.adapt(caster),
                BukkitAdapter.adapt(if(event.phase in setOf(VisualPhase.CHARGE_SPENT,VisualPhase.DAMAGE_CONFIRMED,VisualPhase.HIT,VisualPhase.MISS))impactLocation?:session.context.origin else session.context.origin),listOfNotNull(receiver?.let{BukkitAdapter.adapt(it)}),
                listOfNotNull(impactLocation?.let{BukkitAdapter.adapt(it)}),1.0f)
            data.setTerminatedReference(scope.terminated)
            data.setMetadata(SCOPE,scope)
            data.parameters.putAll(parameters(session,event))
            // No look-at target, mutable inventory reference or globally shared player variables.
            try {
                if(!skill.isUsable(data)) { warn("${session.profile}/${event.phase}/${skill.internalName}","skill not usable");success=false } else skill.execute(data)
            } catch(problem:RuntimeException) { warn("${session.profile}/${event.phase}/${skill.internalName}",problem.toString());success=false }
        }
        return success
    }
    internal fun parameters(session:VisualSession,event:VisualEvent):Map<String,String> {
        val c=session.context;val origin=c.origin;val direction=c.direction
        return linkedMapOf("eco_session" to c.sessionId.toString(),"eco_instance" to event.instance.toString(),
            "eco_source" to c.sourceId,"eco_caster" to c.caster.toString(),
            "eco_phase" to event.phase.name.lowercase(),"eco_reason" to event.reason,
            "eco_duration_ticks" to ((c.durationMillis+49)/50).toString(),"eco_charges" to c.charges.toString(),
            "eco_radius" to c.radius.toString(),"eco_yaw" to origin.yaw.toString(),"eco_pitch" to origin.pitch.toString(),
            "eco_index" to event.index.toString(),"eco_remaining" to event.remaining.toString(),
            "eco_origin_x" to origin.x.toString(),"eco_origin_y" to origin.y.toString(),"eco_origin_z" to origin.z.toString(),
            "eco_direction_x" to direction.x.toString(),"eco_direction_y" to direction.y.toString(),"eco_direction_z" to direction.z.toString()
        ).apply { if(c.level>=0)put("eco_level",c.level.toString());c.itemInstance?.let{put("eco_item_instance",it.toString())};event.location?.let { put("eco_target_x",it.x.toString());put("eco_target_y",it.y.toString());put("eco_target_z",it.z.toString()) };event.receiver?.let{put("eco_receiver",it.toString())};event.confirmedDamage?.let{put("eco_damage",it.toString())} }
    }
    private fun closeResources(scope:Scope,index:Int?=null) {
        val selected=scope.resources.entries.filter{index==null || it.value==index}.map{it.key}
        for(resource in selected) { scope.resources.remove(resource);runCatching{resource.close()}.onFailure{warn(scope.session.profile,it.toString())} }
    }
    override fun close(session:VisualSession) {
        val scope=scopes.remove(session)?:return
        scope.terminated.set(true);closeResources(scope)
    }
    /** Public Mythic INoTargetSkill hook. No damage, hit testing or movement. */
    internal fun bind(data:SkillMetadata,index:Int):Boolean {
        val scope=data.getMetadata(SCOPE).orElse(null) as? Scope ?: return false
        val resource=data.callingEvent as? AutoCloseable ?: return false
        if(scope.terminated.get() || scope.session.isClosed || scope.resources.size>=32 || index>=scope.session.context.charges && index>=0) { resource.close();return false }
        scope.resources[resource]=index
        return true
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    fun skill(event:MythicSkillEvent) {
        val scope=event.skillMetadata.getMetadata(SCOPE).orElse(null) as? Scope ?: return
        if(scope.terminated.get() || scope.session.isClosed)event.isCancelled=true
    }
    @EventHandler fun reloaded(event:MythicReloadedEvent) { VisualPhases.reset("mythic_reload");reload() }
    @EventHandler fun mechanic(event:MythicMechanicLoadEvent) {
        if(event.mechanicName.equals("ecosuite_visual_bind",true)) { bindingObserved=true;event.register(object:
            SkillMechanic(MythicBukkit.inst().skillManager,"ecosuite_visual_bind",event.config),INoTargetSkill {
                init { setAsyncSafe(false) }
                override fun cast(data:SkillMetadata):SkillResult =
                    if(bind(data,event.config.getInteger("index",-1)))SkillResult.SUCCESS else SkillResult.CONDITION_FAILED
            }) }
    }
}
