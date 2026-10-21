package com.willfp.libreforge.visual
import com.willfp.libreforge.integrations.mythicmobs.visual.*
import io.lumine.mythic.api.skills.*
import org.bukkit.Location
import org.bukkit.util.Vector
import org.mockito.Mockito.*
import java.util.*
import kotlin.test.*
class MythicBindingsTest {
 @Test fun `catalog reports missing skill preserves order and bounds links`() {
  val missing=mutableListOf<String>();val result=resolveVisualSkills(listOf("first","bad","second"),{if(it=="bad")null else it},missing::add)
  assertEquals(listOf("first","second"),result);assertEquals(listOf("bad"),missing)
  assertEquals(8,resolveVisualSkills((1..50).map{it.toString()},{it},{}).size)
 }
 @Test fun `real adapter binds exact resources closes selected charge and closes late resources without touching unrelated ones`() {
  val ctx=VisualContext(UUID.randomUUID(),"EcoItems","captured",1,UUID.randomUUID(),Location(null,0.0,0.0,0.0),Vector(0,0,1),1000,3)
  val session=VisualSession("test",ctx,{_,_->true},{})
  val type=Class.forName("com.willfp.libreforge.integrations.mythicmobs.visual.MythicVisuals\$Scope")
  val scope=type.getDeclaredConstructor(VisualSession::class.java).apply{isAccessible=true}.newInstance(session)
  val resources=(0..2).map { mock(IParentSkill::class.java,withSettings().extraInterfaces(AutoCloseable::class.java)) }
  val data=mock(SkillMetadata::class.java);`when`(data.getMetadata("ecosuite_visual_scope")).thenReturn(Optional.of(scope))
  for(i in resources.indices) { `when`(data.callingEvent).thenReturn(resources[i]);assertTrue(MythicVisuals.bind(data,i)) }
  val method=MythicVisuals::class.java.getDeclaredMethod("closeResources",type,java.lang.Integer::class.java).apply{isAccessible=true}
  method.invoke(MythicVisuals,scope,1);verify(resources[1] as AutoCloseable,times(1)).close();verify(resources[0] as AutoCloseable,never()).close()
  val field=MythicVisuals::class.java.getDeclaredField("scopes").apply{isAccessible=true}
  @Suppress("UNCHECKED_CAST") val scopes=field.get(MythicVisuals) as MutableMap<VisualSession,Any>
  scopes[session]=scope;MythicVisuals.close(session);MythicVisuals.close(session)
  for(resource in resources)verify(resource as AutoCloseable,times(1)).close()
  val late=mock(IParentSkill::class.java,withSettings().extraInterfaces(AutoCloseable::class.java));`when`(data.callingEvent).thenReturn(late)
  assertFalse(MythicVisuals.bind(data,0));verify(late as AutoCloseable,times(1)).close()
 }
 @Test fun metadataClonesKeepIsolatedSessionAndTerminationWithoutGlobalPlayerVariables() {
  val entity=mock(io.lumine.mythic.api.adapters.AbstractEntity::class.java)
  val caster=mock(SkillCaster::class.java);`when`(caster.entity).thenReturn(entity)
  val first=io.lumine.mythic.core.skills.SkillMetadataImpl(io.lumine.mythic.core.skills.SkillTriggers.API,caster,entity)
  val second=io.lumine.mythic.core.skills.SkillMetadataImpl(io.lumine.mythic.core.skills.SkillTriggers.API,caster,entity)
  val stop=java.util.concurrent.atomic.AtomicBoolean(false);first.setTerminatedReference(stop)
  first.parameters["eco_session"]="first";second.parameters["eco_session"]="second"
  val marker=Any();first.setMetadata("ecosuite_visual_scope",marker)
  val child=first.deepClone()
  assertSame(marker,child.getMetadata("ecosuite_visual_scope").orElse(null))
  assertEquals("first",child.parameters["eco_session"]);assertEquals("second",second.parameters["eco_session"])
  first.terminate();assertTrue(child.isTerminated);assertFalse(second.isTerminated)
 }

}
