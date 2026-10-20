package com.willfp.libreforge.triggers

import com.willfp.libreforge.triggers.event.*
import org.bukkit.Location
import kotlin.test.*

class EditableXpAdjustmentTest {
 private fun drop()=EditableXpDropEvent(7,XpDropCause.ENTITY,XpDropContext(),Location(null,0.0,0.0,0.0))
 @Test fun `claims are event local and cannot modify XP cancellation or snapshot by themselves`(){
  val first=drop();val second=drop()
  assertTrue(first.claimAdjustment("ecoenchants:experience"));assertFalse(first.claimAdjustment("ecoenchants:experience"));assertTrue(second.claimAdjustment("ecoenchants:experience"))
  assertEquals(7,first.xp);assertEquals(7,first.originalXp);assertEquals(7,first.finalXp)
  first.setCancelled(true);assertEquals(0,first.finalXp);assertEquals(7,first.xp)
 }
 @Test fun `claim storage is bounded above the entire catalogue without dropping prior claims`(){
  val event=drop();assertFalse(event.claimAdjustment(""));assertFalse(event.claimAdjustment("x".repeat(129)))
  repeat(256){assertTrue(event.claimAdjustment("family:$it"))}
  assertFalse(event.claimAdjustment("overflow"));assertFalse(event.claimAdjustment("family:0"));assertEquals(7,event.finalXp)
 }
}
