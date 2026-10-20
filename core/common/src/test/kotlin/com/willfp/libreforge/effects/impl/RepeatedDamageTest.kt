package com.willfp.libreforge.effects.impl

import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.mockito.Mockito.*
import java.util.UUID
import kotlin.test.*

class RepeatedDamageTest {
 private fun hit(source:UUID,target:UUID):EntityDamageByEntityEvent {
  val actor=mock(LivingEntity::class.java);`when`(actor.uniqueId).thenReturn(source)
  val victim=mock(LivingEntity::class.java);`when`(victim.uniqueId).thenReturn(target)
  val event=mock(EntityDamageByEntityEvent::class.java);`when`(event.entity).thenReturn(victim);`when`(event.damager).thenReturn(actor);return event
 }
 @Test fun `replay skips sender rolls and already baked numerical effects but retains receiver callbacks`(){
  val source=UUID.randomUUID();val target=UUID.randomUUID();val event=hit(source,target)
  val data=mock(TriggerData::class.java);`when`(data.event).thenReturn(event)
  assertFalse(RepeatedDamage.matches(event));assertFalse(RepeatedDamage.skipNumeric("damage_multiplier",data))
  RepeatedDamage.run(source,source,target){
   assertTrue(RepeatedDamage.matches(event));assertTrue(RepeatedDamage.skipSender(source,data));assertFalse(RepeatedDamage.skipSender(target,data))
   for(id in listOf("damage_multiplier","total_damage_multiplier","pvp_damage_multiplier","crit_multiplier","add_damage","make_skill_crit"))assertTrue(RepeatedDamage.skipNumeric(id,data))
   for(id in listOf("cancel_event","give_health","potion_effect"))assertFalse(RepeatedDamage.skipNumeric(id,data))
   assertEquals(DamageOrigin.PASSIVE,DamageMultiplierBypass.current?.origin)
   assertFalse(DamageMultiplierBypass.isActive) // Keep the legacy receiver bridge unchanged.
  }
  assertFalse(RepeatedDamage.matches(event));assertNull(DamageMultiplierBypass.current)
 }
 @Test fun `event identity excludes unrelated nested damage and restores outer and empty scopes after exceptions`(){
  val source=UUID.randomUUID();val target=UUID.randomUUID();val outer=hit(source,target);val unrelated=hit(source,target)
  RepeatedDamage.run(source,source,target){
   assertTrue(RepeatedDamage.matches(outer));assertFalse(RepeatedDamage.matches(unrelated))
   assertFailsWith<IllegalStateException>{RepeatedDamage.run(source,source,target){assertTrue(RepeatedDamage.matches(unrelated));error("nested")}}
   assertTrue(RepeatedDamage.matches(outer));assertFalse(RepeatedDamage.matches(unrelated))
  }
  assertFalse(RepeatedDamage.matches(outer));assertNull(DamageMultiplierBypass.current)
 }
 @Test fun `wrong target source and non damage event cannot claim a replay`(){
  val source=UUID.randomUUID();val target=UUID.randomUUID()
  RepeatedDamage.run(source,source,target){
   assertFalse(RepeatedDamage.matches(hit(UUID.randomUUID(),target)))
   assertFalse(RepeatedDamage.matches(hit(source,UUID.randomUUID())))
   assertFalse(RepeatedDamage.matches(null));assertTrue(RepeatedDamage.matches(hit(source,target)))
  }
 }
}
