package com.willfp.libreforge.effects.orbitals

import com.willfp.libreforge.triggers.impl.*
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.player.*
import org.bukkit.event.entity.*
import org.bukkit.inventory.EquipmentSlot
import org.mockito.Mockito.*
import java.util.UUID
import kotlin.test.*

class OrbitalInputTest {
 private val player=mock(Player::class.java)
 private fun click(action:Action,hand:EquipmentSlot=EquipmentSlot.HAND):PlayerInteractEvent {
  val e=mock(PlayerInteractEvent::class.java)
  `when`(e.player).thenReturn(player);`when`(e.action).thenReturn(action);`when`(e.hand).thenReturn(hand)
  `when`(e.useItemInHand()).thenReturn(Event.Result.DEFAULT);`when`(e.useInteractedBlock()).thenReturn(Event.Result.DEFAULT)
  return e
 }
 @Test fun `default shift starts only and combos need physical click while sneaking`() {
  val shift=mock(PlayerToggleSneakEvent::class.java);`when`(shift.player).thenReturn(player);`when`(shift.isSneaking).thenReturn(true)
  assertTrue(OrbitalInput.matches("sneak_start",player,shift));assertFalse(OrbitalInput.matches("shift_right_click",player,shift))
  `when`(shift.isSneaking).thenReturn(false);assertFalse(OrbitalInput.matches("sneak_start",player,shift))
  val right=click(Action.RIGHT_CLICK_AIR);assertFalse(OrbitalInput.matches("shift_right_click",player,right))
  `when`(player.isSneaking).thenReturn(true);assertTrue(OrbitalInput.matches("shift_right_click",player,right))
  assertFalse(OrbitalInput.matches("shift_left_click",player,right));assertTrue(OrbitalInput.matches("shift_left_click",player,click(Action.LEFT_CLICK_AIR)))
 }
 @Test fun `physical left right never inspect or invert item material and reject offhand`() {
  for(action in listOf(Action.LEFT_CLICK_AIR,Action.LEFT_CLICK_BLOCK)) assertEquals("left_click",physicalClick(click(action)))
  for(action in listOf(Action.RIGHT_CLICK_AIR,Action.RIGHT_CLICK_BLOCK)) assertEquals("right_click",physicalClick(click(action)))
  assertNull(physicalClick(click(Action.RIGHT_CLICK_AIR,EquipmentSlot.OFF_HAND)))
  verify(player,never()).inventory
 }
 @Test fun `predicted air block cancellation is allowed but explicit item or block denial is not`() {
  val air=click(Action.RIGHT_CLICK_AIR);`when`(air.isCancelled).thenReturn(true);`when`(air.useInteractedBlock()).thenReturn(Event.Result.DENY)
  assertEquals("right_click",physicalClick(air));`when`(air.useItemInHand()).thenReturn(Event.Result.DENY);assertNull(physicalClick(air))
  val block=click(Action.RIGHT_CLICK_BLOCK);`when`(block.clickedBlock).thenReturn(mock(org.bukkit.block.Block::class.java))
  `when`(block.useInteractedBlock()).thenReturn(Event.Result.DENY);assertNull(physicalClick(block))
 }
 @Test fun `entity interactions mainhand only and cancelled attacks or sweep do not activate`() {
  val entity=mock(PlayerInteractEntityEvent::class.java);`when`(entity.hand).thenReturn(EquipmentSlot.HAND)
  assertEquals("right_click",physicalClick(entity));`when`(entity.isCancelled).thenReturn(true);assertNull(physicalClick(entity))
  val hit=mock(EntityDamageByEntityEvent::class.java);`when`(hit.damager).thenReturn(player);`when`(hit.cause).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK)
  assertEquals("left_click",physicalClick(hit));`when`(hit.cause).thenReturn(EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK);assertNull(physicalClick(hit))
 }
 @Test fun `same tick hand entity and duplicate event representations claim only one gesture`() {
  val gestures=PhysicalGestures();val id=UUID.randomUUID();assertTrue(gestures.claim(id,123));repeat(4){assertFalse(gestures.claim(id,123))}
  assertTrue(gestures.claim(id,124));assertTrue(gestures.claim(UUID.randomUUID(),124))
 }
 @Test fun `cooldown remaining rounds up never renews and notices are limited to one second`() {
  var now=0L;val ledger=OrbitalCharges { now };val id=UUID.randomUUID();ledger.start(id,3,8000,20000)
  assertEquals(20L,(ledger.remainingMillis(id)+999)/1000);assertFalse(ledger.claimNotice(id,false));assertTrue(ledger.claimNotice(id));assertFalse(ledger.claimNotice(id))
  now=999;assertFalse(ledger.claimNotice(id));assertEquals(20L,(ledger.remainingMillis(id)+999)/1000)
  now=1000;assertTrue(ledger.claimNotice(id));assertNull(ledger.start(id,3,8000,20000));assertEquals(19000L,ledger.remainingMillis(id))
  ledger.clear();now=19999;assertEquals(1L,(ledger.remainingMillis(id)+999)/1000);now=20000;assertEquals(0L,ledger.remainingMillis(id));assertFalse(ledger.claimNotice(id))
 }
}
