package com.willfp.libreforge.effects.impl

import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.diagnostics.performanceScope
import org.bukkit.Bukkit
import org.bukkit.event.*
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.plugin.Plugin
import java.util.IdentityHashMap
import java.util.function.Predicate

/** Selective opt-in; ordinary damage_multiplier consumers never enter this path. */
object AdditiveDamageGroups : Listener {
 private data class Policy(val owner:Plugin,val accepts:Predicate<TriggerData>)
 private data class Contribution(val group:String,val holder:com.willfp.libreforge.ProvidedHolder)
 private val policies=mutableMapOf<String,Policy>()
 private val pending=IdentityHashMap<EntityDamageEvent,DamageContributionSum?>()
 private var listenerOwner:Plugin?=null
 @JvmStatic fun bind(group:String,owner:Plugin,accepts:Predicate<TriggerData>){
  check(Bukkit.isPrimaryThread());require(group.length in 1..96)
  check(group !in policies||policies[group]?.owner===owner){"Damage group already owned"}
  check(group in policies||policies.size<8){"Damage group registry full"}
  policies[group]=Policy(owner,accepts);reseatBoundary()
 }
 @JvmStatic fun unbind(group:String,owner:Plugin){
  check(Bukkit.isPrimaryThread())
  if(policies[group]?.owner!==owner)return
  policies.remove(group);pending.clear();reseatBoundary()
 }
 /** Called after actual native listener registration, never per holder/hit. */
 @JvmStatic fun reseatBoundary(){
  if(policies.isEmpty()){if(listenerOwner!=null)HandlerList.unregisterAll(this);listenerOwner=null;pending.clear();return}
  val owner=policies.values.firstOrNull{it.owner.isEnabled}?.owner?:return
  HandlerList.unregisterAll(this);Bukkit.getPluginManager().registerEvents(this,owner);listenerOwner=owner
 }
 @JvmStatic fun offer(group:String,data:TriggerData,multiplier:Double):Boolean {
  if(!Bukkit.isPrimaryThread()||listenerOwner?.isEnabled!=true||DamageMultiplierBypass.current!=null)return false
  val policy=policies[group]?:return false
  if(!policy.owner.isEnabled||!policy.accepts.test(data))return false
  val event=data.event as? EntityDamageEvent?:return false
  if(event.isCancelled||!multiplier.isFinite()||multiplier<1)return false
  if(!pending.containsKey(event))return false // Closed or non-native event: never create a late collector.
  var sum=pending[event]
  if(sum==null){sum=DamageContributionSum();pending[event]=sum}
  return sum.offer(Contribution(group,data.holder),multiplier)
 }
 @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=false)
 fun begin(event:EntityDamageEvent){
  if(!Bukkit.isPrimaryThread()||DamageMultiplierBypass.current!=null||pending.size>=1024||pending.containsKey(event))return
  val attack=event as? org.bukkit.event.entity.EntityDamageByEntityEvent?:return
  val source=attack.damager
  if(source !is org.bukkit.entity.Player&&(source !is org.bukkit.entity.Projectile||source.shooter !is org.bukkit.entity.Player))return
  pending[event]=null
 }
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=false)
 fun finish(event:EntityDamageEvent){
  val sum=pending.remove(event)?:return
  performanceScope({"libreforge:damage"},{"groups:normal:finalize"}){sum.apply(event)}
 }
}

/** The same calculation used by the live finalizer; no armor/critical/secondary damage model. */
internal class DamageContributionSum {
 private val bonuses=mutableMapOf<Any,Double>()
 private var total=0.0
 fun offer(key:Any,multiplier:Double):Boolean {
  if(!multiplier.isFinite()||multiplier<1)return false
  if(key !in bonuses&&bonuses.size>=1024)return false
  val before=bonuses[key]?:0.0;val bonus=maxOf(before,multiplier-1)
  val next=total+(bonus-before)
  if(!next.isFinite())return false
  bonuses[key]=bonus;total=next;return true
 }
 fun apply(event:EntityDamageEvent):Boolean {
  if(event.isCancelled||bonuses.isEmpty()||total==0.0)return false
  val base=event.damage;val result=base*(1+total)
  if(!base.isFinite()||base<=0||!result.isFinite()||result<0)return false
  event.damage=result;return true
 }
}
