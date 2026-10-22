package com.willfp.libreforge.filters.impl
import com.willfp.libreforge.effects.impl.*
import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.junit.Test
import org.mockito.Mockito.*
import java.util.UUID
import kotlin.test.*
class FilterActivePlayerAbilityTest {
 @Test fun genuineForeignActivePlayerAbilityPassesButPhysicalMagicPassiveSelfOrUnknownDoNot(){
  val source=mock(Player::class.java);val target=mock(Player::class.java)
  val id=UUID.randomUUID();`when`(source.uniqueId).thenReturn(id)
  val event=mock(EntityDamageByEntityEvent::class.java);`when`(event.damager).thenReturn(source);`when`(event.entity).thenReturn(target)
  `when`(event.cause).thenReturn(EntityDamageEvent.DamageCause.MAGIC)
  val data=mock(TriggerData::class.java);`when`(data.event).thenReturn(event);`when`(data.player).thenReturn(target)
  assertFalse(activePlayerAbilityMatches(data))
  for(origin in listOf(DamageOrigin.NORMAL,DamageOrigin.PASSIVE))DamageMultiplierBypass.withProvenance(DamageProvenance(origin,id)){assertFalse(activePlayerAbilityMatches(data))}
  DamageMultiplierBypass.withProvenance(DamageProvenance(DamageOrigin.ACTIVE,null)){assertFalse(activePlayerAbilityMatches(data))}
  DamageMultiplierBypass.withProvenance(DamageProvenance(DamageOrigin.ACTIVE,id)){
   assertTrue(activePlayerAbilityMatches(data))
   `when`(event.isCancelled).thenReturn(true);assertFalse(activePlayerAbilityMatches(data));`when`(event.isCancelled).thenReturn(false)
   `when`(event.entity).thenReturn(source);`when`(data.player).thenReturn(source);assertFalse(activePlayerAbilityMatches(data))
  }
 }
 @Test fun environmentWithoutAttributedAttackerNeverPasses(){
  val data=mock(TriggerData::class.java);`when`(data.event).thenReturn(mock(EntityDamageEvent::class.java))
  assertFalse(activePlayerAbilityMatches(data))
 }
}
