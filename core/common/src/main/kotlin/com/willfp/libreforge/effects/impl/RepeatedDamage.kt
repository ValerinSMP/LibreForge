package com.willfp.libreforge.effects.impl

import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.event.Event
import org.bukkit.event.entity.EntityDamageByEntityEvent
import java.util.UUID

/** Scoped replay of already computed native damage; receiver callbacks remain enabled.
 * Source/target permissions remain the caller responsibility; no damage or i-frame mutation here. */
object RepeatedDamage {
 private data class Frame(val source:UUID,val sender:UUID,val target:UUID,var event:EntityDamageByEntityEvent?=null)
 private val frame=ThreadLocal<Frame?>()
 private val numeric=setOf("damage_multiplier","total_damage_multiplier","pvp_damage_multiplier","crit_multiplier","add_damage","make_skill_crit")
 fun matches(event:Event?):Boolean {
  val current=frame.get()?:return false
  val damage=event as? EntityDamageByEntityEvent?:return false
  if(current.event!=null)return current.event===damage
  if(damage.entity.uniqueId!=current.target||damage.damager.uniqueId!=current.source)return false
  current.event=damage;return true
 }
 fun skipSender(uuid:UUID,data:TriggerData):Boolean = matches(data.event)&&frame.get()?.let{it.sender==uuid&&uuid!=it.target}==true
 fun skipNumeric(id:String,data:TriggerData)=id in numeric&&matches(data.event)
 fun <T> run(source:UUID,sender:UUID,target:UUID,body:()->T):T {
  val previous=frame.get();frame.set(Frame(source,sender,target))
  try{return DamageMultiplierBypass.withProvenance(DamageProvenance(DamageOrigin.PASSIVE,sender),body)}
  finally{if(previous==null)frame.remove() else frame.set(previous)}
 }
}
