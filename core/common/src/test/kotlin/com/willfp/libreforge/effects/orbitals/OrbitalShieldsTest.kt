package com.willfp.libreforge.effects.orbitals

import com.willfp.libreforge.effects.impl.DamageContributionSum
import com.willfp.libreforge.triggers.impl.TriggerToggleSneak
import org.bukkit.Location
import org.bukkit.event.entity.EntityDamageEvent
import kotlin.test.*
import org.mockito.Mockito.*
import java.util.UUID

class OrbitalShieldsTest {
 private fun event(initial:Double=10.0):EntityDamageEvent {
  val event=mock(EntityDamageEvent::class.java);var base=initial
  `when`(event.damage).thenAnswer { base };`when`(event.finalDamage).thenAnswer { base*0.6 }
  doAnswer { base=it.getArgument(0);null }.`when`(event).setDamage(anyDouble())
  return event
 }
 @Test fun `three reservations prevent a fourth same tick hit and every confirmed break is unique`() {
  val player=UUID.randomUUID();val charges=OrbitalCharges { 0 };charges.start(player,3,8_000,20_000)
  val boundary=OrbitalDamageBoundary(charges);val events=List(4){event()}
  val leases=events.map { boundary.block(player,it) }
  assertTrue(leases.take(3).all { it!=null });assertNull(leases.last());assertEquals(6.0,events.last().finalDamage)
  leases.take(3).forEachIndexed { index,lease -> assertEquals(index,boundary.finish(lease!!));assertNull(boundary.finish(lease)) }
  assertNull(charges.current(player));assertNull(charges.start(player,3,8_000,20_000))
 }
 @Test fun `already cancelled zero damage and repeated same event never reserve another charge`() {
  val player=UUID.randomUUID();val charges=OrbitalCharges { 0 };charges.start(player,3,8_000,20_000);val boundary=OrbitalDamageBoundary(charges)
  val cancelled=event();`when`(cancelled.isCancelled).thenReturn(true);assertNull(boundary.block(player,cancelled))
  val zero=event(0.0);assertNull(boundary.block(player,zero));assertEquals(3,charges.current(player)!!.available.size)
  val hit=event();val lease=boundary.block(player,hit)!!;assertNull(boundary.block(player,hit));assertEquals(0,boundary.finish(lease))
  verify(hit,never()).setCancelled(anyBoolean())
 }
 @Test fun `late cancellation or damage overwrite refunds the charge with no confirmed break`() {
  for(cancel in listOf(true,false)) {
   val player=UUID.randomUUID();val charges=OrbitalCharges { 0 };charges.start(player,3,8_000,20_000);val boundary=OrbitalDamageBoundary(charges)
   val hit=event();val lease=boundary.block(player,hit)!!
   if(cancel) `when`(hit.isCancelled).thenReturn(true) else hit.damage=4.0
   assertNull(boundary.finish(lease));assertEquals(3,charges.current(player)!!.available.size)
  }
 }
 @Test fun `native additive damage finalizer before or after block cannot resurrect or multiply zero damage`() {
  for(finalizeFirst in listOf(true,false)) {
   val player=UUID.randomUUID();val charges=OrbitalCharges { 0 };charges.start(player,3,8_000,20_000);val boundary=OrbitalDamageBoundary(charges)
   val hit=event();val native=DamageContributionSum();native.offer("normal",1.5)
   if(finalizeFirst) assertTrue(native.apply(hit))
   val lease=boundary.block(player,hit)!!
   if(!finalizeFirst) assertFalse(native.apply(hit))
   assertEquals(0.0,hit.finalDamage);assertEquals(0,boundary.finish(lease));assertEquals(2,charges.current(player)!!.available.size)
  }
 }
 @Test fun `slot quit death provider reload cleanup and item reactivation retain original cooldown`() {
  var time=0L;val player=UUID.randomUUID();val charges=OrbitalCharges { time }
  val first=charges.start(player,3,8_000,20_000)!!;val lease=charges.reserve(player,Any())!!
  charges.close(player);charges.clear();assertNull(charges.start(player,3,8_000,20_000));assertNull(charges.finish(lease,true))
  time=20_000;val next=charges.start(player,3,8_000,20_000)!!;assertNotEquals(first.token,next.token)
  time=28_000;assertNull(charges.reserve(player,Any()));charges.close(player);assertNull(charges.start(player,3,8_000,20_000))
 }
 @Test fun `failed invocation can roll back only its own cooldown and bounds are enforced`() {
  val player=UUID.randomUUID();val charges=OrbitalCharges { 0 };val state=charges.start(player,3,8_000,20_000)!!
  charges.abort(player,UUID.randomUUID());assertNull(charges.start(player,3,8_000,20_000));charges.abort(player,state.token)
  assertNotNull(charges.start(player,3,8_000,20_000));assertNull(charges.start(UUID.randomUUID(),99,8_000,20_000))
 }
 @Test fun `three orbital positions preserve radius height and smooth angular motion`() {
  val origin=Location(null,0.0,64.0,0.0)
  val positions=(0..2).map { OrbitalRig.frame(origin,it,3,0.0,1.25,1.1,0.09) }
  assertEquals(3,positions.map { it.x to it.z }.distinct().size)
  for(position in positions) { assertEquals(1.25,kotlin.math.hypot(position.x,position.z),1e-9);assertEquals(65.1,position.y,1e-9) }
  val next=OrbitalRig.frame(origin,0,3,2.0,1.25,1.1,0.09);assertTrue(kotlin.math.hypot(next.x-positions[0].x,next.z-positions[0].z)<0.3)
 }
 @Test fun `pilot compiles against native sneak parameters without requiring an undeclared item parameter`() {
  val field=EffectOrbitalShields::class.java.getDeclaredField("parameters").apply{isAccessible=true}
  @Suppress("UNCHECKED_CAST") val parameters=field.get(EffectOrbitalShields) as Set<com.willfp.libreforge.triggers.TriggerParameter>
  assertTrue(TriggerToggleSneak.parameters.containsAll(parameters))
 }
 @Test fun `confirmed visual break and repeated cleanup remove each display exactly once`() {
  val displays=List(3){mock(org.bukkit.entity.ItemDisplay::class.java)};var spawned=0
  val world=mock(org.bukkit.World::class.java){call ->
   if(call.method.name=="spawn") displays[spawned++] else org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
  }
  `when`(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true)
  val player=mock(org.bukkit.entity.Player::class.java)
  `when`(player.world).thenReturn(world);`when`(player.location).thenReturn(Location(world,0.0,64.0,0.0))
  for(display in displays) { `when`(display.isValid).thenReturn(true);`when`(display.location).thenReturn(Location(world,1.0,65.0,0.0)) }
  val profile=OrbitalProfile(org.bukkit.Material.SHIELD,0.55f,1.1,0.09,org.bukkit.Particle.CRIT,"","","",0.6f,1.0f)
  val rig=OrbitalRig(player,3,1.25,profile);assertEquals(3,spawned)
  rig.breakShield(1);rig.breakShield(1);rig.close();rig.close()
  displays.forEach { verify(it,times(1)).remove() }
 }

 @Test fun `only direct melee and living shooter projectiles qualify as intrinsic coverage`() {
  val event=mock(org.bukkit.event.entity.EntityDamageByEntityEvent::class.java)
  val attacker=mock(org.bukkit.entity.Player::class.java);val arrow=mock(org.bukkit.entity.Projectile::class.java)
  `when`(event.damager).thenReturn(attacker);`when`(event.cause).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK)
  assertSame(attacker,directOrbitalAttacker(event))
  `when`(event.damager).thenReturn(arrow);`when`(arrow.shooter).thenReturn(attacker)
  `when`(event.cause).thenReturn(EntityDamageEvent.DamageCause.PROJECTILE);assertSame(attacker,directOrbitalAttacker(event))
  for(cause in listOf(EntityDamageEvent.DamageCause.FALL,EntityDamageEvent.DamageCause.FIRE,EntityDamageEvent.DamageCause.POISON,
      EntityDamageEvent.DamageCause.MAGIC,EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
   `when`(event.cause).thenReturn(cause);assertNull(directOrbitalAttacker(event))
  }
  `when`(event.cause).thenReturn(EntityDamageEvent.DamageCause.PROJECTILE);`when`(arrow.shooter).thenReturn(null);assertNull(directOrbitalAttacker(event))
 }

}
