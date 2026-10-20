package com.willfp.libreforge.effects.impl

import org.bukkit.event.entity.EntityDamageEvent
import kotlin.test.*
import org.mockito.Mockito.*

class AdditiveDamageGroupsTest {
 @Test fun `native event scope finalizes once refuses late offers and respects cancelled event`(){
  mockStatic(org.bukkit.Bukkit::class.java).use{bukkit->
   bukkit.`when`<Boolean>{org.bukkit.Bukkit.isPrimaryThread()}.thenReturn(true)
   val manager=mock(org.bukkit.plugin.PluginManager::class.java)
   bukkit.`when`<org.bukkit.plugin.PluginManager>{org.bukkit.Bukkit.getPluginManager()}.thenReturn(manager)
   val owner=mock(org.bukkit.plugin.Plugin::class.java);`when`(owner.isEnabled).thenReturn(true)
   val event=mock(org.bukkit.event.entity.EntityDamageByEntityEvent::class.java)
   `when`(event.damager).thenReturn(mock(org.bukkit.entity.Player::class.java));`when`(event.damage).thenReturn(10.0)
   val data=mock(com.willfp.libreforge.triggers.TriggerData::class.java)
   `when`(data.event).thenReturn(event);`when`(data.holder).thenReturn(mock(com.willfp.libreforge.ProvidedHolder::class.java))
   AdditiveDamageGroups.bind("test",owner){true}
   try{
    assertFalse(AdditiveDamageGroups.offer("test",data,1.5))
    AdditiveDamageGroups.begin(event);assertTrue(AdditiveDamageGroups.offer("test",data,1.5))
    AdditiveDamageGroups.finish(event);AdditiveDamageGroups.finish(event)
    verify(event,times(1)).setDamage(15.0);assertFalse(AdditiveDamageGroups.offer("test",data,1.5))
    clearInvocations(event);AdditiveDamageGroups.begin(event);assertTrue(AdditiveDamageGroups.offer("test",data,1.5))
    `when`(event.isCancelled).thenReturn(true);AdditiveDamageGroups.finish(event);verify(event,never()).setDamage(anyDouble())
   }finally{AdditiveDamageGroups.unbind("test",owner)}
  }
 }
 @Test fun `normal bonuses add on a shared base instead of multiplying each other`(){
  val sum=DamageContributionSum();assertTrue(sum.offer("a",1.5));assertTrue(sum.offer("b",1.4))
  val event=mock(EntityDamageEvent::class.java);`when`(event.damage).thenReturn(20.0)
  assertTrue(sum.apply(event));verify(event).setDamage(38.0);verify(event,never()).setDamage(42.0)
 }
 @Test fun `same holder repeated in multiple attack triggers contributes once using highest eligible branch`(){
  val sum=DamageContributionSum();repeat(8){assertTrue(sum.offer("holder",1.2))};assertTrue(sum.offer("holder",1.4));assertTrue(sum.offer("holder",1.1))
  val event=mock(EntityDamageEvent::class.java);`when`(event.damage).thenReturn(10.0)
  assertTrue(sum.apply(event));verify(event).setDamage(14.0)
 }
 @Test fun `late common base includes earlier native stats criticals flat damage and other consumers without recomputing armor`(){
  val sum=DamageContributionSum();sum.offer("normal",1.5)
  val event=mock(EntityDamageEvent::class.java);`when`(event.damage).thenReturn(35.0)
  assertTrue(sum.apply(event));verify(event).setDamage(52.5);verify(event,never()).getFinalDamage()
 }
 @Test fun `cancelled zero and overflowing hits never write damage or bypass a cancellation`(){
  for(base in listOf(0.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY,Double.MAX_VALUE)){
   val sum=DamageContributionSum();sum.offer("a",2.0)
   val event=mock(EntityDamageEvent::class.java);`when`(event.damage).thenReturn(base)
   assertFalse(sum.apply(event));verify(event,never()).setDamage(anyDouble())
  }
  val sum=DamageContributionSum();sum.offer("a",2.0)
  val event=mock(EntityDamageEvent::class.java);`when`(event.isCancelled).thenReturn(true)
  assertFalse(sum.apply(event));verify(event,never()).setDamage(anyDouble())
 }
 @Test fun `no arbitrary damage cap and resource bound above the complete normal catalogue`(){
  val sum=DamageContributionSum()
  repeat(780){assertTrue(sum.offer(it,1.01))}
  val event=mock(EntityDamageEvent::class.java);`when`(event.damage).thenReturn(10.0)
  assertTrue(sum.apply(event))
  assertFalse(sum.offer("nan",Double.NaN));assertFalse(sum.offer("debuff",.5))
 }
}
