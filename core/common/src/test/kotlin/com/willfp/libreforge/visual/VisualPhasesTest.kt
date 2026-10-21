package com.willfp.libreforge.visual

import org.bukkit.Location
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.test.*
class VisualPhasesTest {
 private fun context()=VisualContext(UUID.randomUUID(),"EcoItems","test:captured",3,UUID.randomUUID(),Location(null,1.0,2.0,3.0),Vector(0,0,1),1000,3)
 @Test fun `phase order is explicit and duplicate receiver callbacks do not suppress other receivers`() {
  val emitted=mutableListOf<VisualEvent>();var clean=0;val ctx=context()
  val session=VisualSession("test",ctx,{_,e->emitted+=e;true},{clean++})
  assertTrue(session.emit(VisualPhase.ACTIVATE));assertFalse(session.emit(VisualPhase.ACTIVATE));assertTrue(session.emit(VisualPhase.ACTIVE));assertFalse(session.emit(VisualPhase.ACTIVE))
  val first=UUID.randomUUID();val second=UUID.randomUUID();val instance=UUID.randomUUID();val impact=UUID.randomUUID()
  assertTrue(session.emit(VisualPhase.LAUNCH,instance));assertTrue(session.emit(VisualPhase.HIT,instance,first,impact))
  assertFalse(session.emit(VisualPhase.HIT,instance,first,impact));assertTrue(session.emit(VisualPhase.HIT,instance,second,impact))
  assertFalse(session.emit(VisualPhase.MISS,instance,reason="wall"))
  assertTrue(session.emit(VisualPhase.DAMAGE_CONFIRMED,instance,first,impact,confirmedDamage=2.5))
  assertFalse(session.emit(VisualPhase.DAMAGE_CONFIRMED,instance,first,impact,confirmedDamage=2.5))
  session.close("duration",false);session.close("quit");assertEquals(1,clean)
  assertEquals(VisualPhase.DEACTIVATE,emitted.last().phase);assertFalse(session.emit(VisualPhase.ACTIVE))
 }
 @Test fun `zero damage does not create confirmation and reject is not a projectile miss`() {
  val events=mutableListOf<VisualEvent>();val ctx=context();val session=VisualSession("test",ctx,{_,e->events+=e;true},{})
  val victim=UUID.randomUUID();assertTrue(session.emit(VisualPhase.HIT,receiver=victim))
  assertFalse(session.emit(VisualPhase.DAMAGE_CONFIRMED,receiver=victim,confirmedDamage=0.0))
  assertFalse(session.emit(VisualPhase.MISS,reason="wall"));assertTrue(session.emit(VisualPhase.REJECT,reason="cooldown"))
  val instance=UUID.randomUUID();assertTrue(session.emit(VisualPhase.LAUNCH,instance));assertTrue(session.emit(VisualPhase.MISS,instance,reason="range"))
  assertFalse(session.emit(VisualPhase.HIT,instance,victim));assertEquals(0,events.count{it.phase==VisualPhase.DAMAGE_CONFIRMED})
 }
 @Test fun `captured origin direction and impact cannot be rewritten through mutable caller references`() {
  val origin=Location(null,1.0,2.0,3.0);val direction=Vector(1,0,0)
  val ctx=VisualContext(UUID.randomUUID(),"EcoEnchants","original:tier",2,UUID.randomUUID(),origin,direction,1000,0)
  origin.x=99.0;direction.x=0.0;ctx.origin.x=500.0;ctx.direction.x=6.0
  assertEquals(1.0,ctx.origin.x);assertEquals(1.0,ctx.direction.x)
  val impact=Location(null,4.0,5.0,6.0);val e=VisualEvent(VisualPhase.HIT,UUID.randomUUID(),null,UUID.randomUUID(),impact,"",-1,0,null)
  impact.x=8.0;e.location!!.x=9.0;assertEquals(4.0,e.location!!.x)
 }
 @Test fun `actual charge indices deduplicate independently and cleanup runs after local visual failure`() {
  var clean=0;val session=VisualSession("absent",context(),{_,_->false},{clean++})
  assertFalse(session.emit(VisualPhase.CHARGE_SPENT,index=0,remaining=2))
  assertFalse(session.emit(VisualPhase.CHARGE_SPENT,index=0,remaining=2))
  session.emit(VisualPhase.CHARGE_SPENT,index=1,remaining=1);session.close("reload");session.close("quit")
  assertTrue(session.isClosed);assertEquals(1,clean)
 }
 @Test fun reentrantTerminalCallbackCannotEmitBothTerminalPhasesOrCleanTwice() {
  val events=mutableListOf<VisualPhase>();var cleaned=0
  val session=VisualSession("test",context(),{current,event ->
   events+=event.phase
   if(event.phase==VisualPhase.DEACTIVATE)current.close("reentrant_interrupt")
   true
  },{cleaned++})
  session.emit(VisualPhase.ACTIVATE);session.close("duration",false)
  assertEquals(listOf(VisualPhase.ACTIVATE,VisualPhase.DEACTIVATE),events)
  assertEquals(1,cleaned);assertTrue(session.isClosed)
 }

}
