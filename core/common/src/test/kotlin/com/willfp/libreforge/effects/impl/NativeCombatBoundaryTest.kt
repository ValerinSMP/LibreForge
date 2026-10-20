package com.willfp.libreforge.effects.impl

import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import org.bukkit.entity.Player
import org.bukkit.entity.AbstractArrow
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.util.Vector
import java.util.UUID
import com.willfp.libreforge.effects.IdentifiedModifier
import com.willfp.libreforge.triggers.impl.TriggerShieldBlock

class NativeCombatBoundaryTest {
 @Test fun `shield boundary requires actual blocked amount and rejects invalid or cancelled results`() {
  val event=mock(EntityDamageByEntityEvent::class.java)
  `when`(event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(true)
  `when`(event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(-7.5)
  assertEquals(7.5,TriggerShieldBlock.blockedDamage(event),0.0)
  `when`(event.isCancelled).thenReturn(true);assertEquals(0.0,TriggerShieldBlock.blockedDamage(event),0.0)
  `when`(event.isCancelled).thenReturn(false)
  for(value in listOf(0.0,2.0,Double.NaN,Double.NEGATIVE_INFINITY)){
   `when`(event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(value);assertEquals(0.0,TriggerShieldBlock.blockedDamage(event),0.0)
  }
  `when`(event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(false);assertEquals(0.0,TriggerShieldBlock.blockedDamage(event),0.0)
 }
 @Test fun `rapid bow uses native normalized threshold without granting earlier full velocity`() {
  val listener=EffectRapidBows
  val field=listener.javaClass.getDeclaredField("modifiers").apply{isAccessible=true}
  @Suppress("UNCHECKED_CAST") val modifiers=field.get(listener) as MutableMap<UUID,MutableList<Any>>
  val ctor=Class.forName("com.willfp.libreforge.effects.impl.EffectRapidBows\$BowModifier").declaredConstructors.first().apply{isAccessible=true}
  val player=mock(Player::class.java);val arrow=mock(AbstractArrow::class.java);val event=mock(EntityShootBowEvent::class.java)
  val id=UUID.randomUUID();`when`(player.uniqueId).thenReturn(id);`when`(event.entity).thenReturn(player);`when`(event.projectile).thenReturn(arrow)
  try{
   modifiers[id]=mutableListOf(ctor.newInstance(IdentifiedModifier(UUID.randomUUID()){75.0},true))
   `when`(event.force).thenReturn(.20f);`when`(arrow.velocity).thenReturn(Vector(.6,0.0,0.0));listener.handle(event);verify(arrow,never()).setVelocity(any())
   `when`(event.force).thenReturn(.25f);`when`(arrow.velocity).thenReturn(Vector(.75,0.0,0.0));listener.handle(event);verify(arrow).setVelocity(Vector(3.0,0.0,0.0))
   clearInvocations(arrow);`when`(event.force).thenReturn(0f);listener.handle(event);verify(arrow,never()).setVelocity(any())
   `when`(event.force).thenReturn(Float.NaN);listener.handle(event);verify(arrow,never()).setVelocity(any())
  }finally{modifiers.remove(id)}
 }
}
