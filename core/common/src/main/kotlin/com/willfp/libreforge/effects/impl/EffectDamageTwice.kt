package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.plugin
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import com.willfp.libreforge.triggers.tryAsLivingEntity
import org.bukkit.event.entity.EntityDamageByEntityEvent

object EffectDamageTwice : Effect<NoCompileData>("damage_twice") {
    override val description = "Causes the victim to take the triggering hit's damage a second time."
    override val categories = setOf("combat")
    override val additionalInfo = listOf("Requires a trigger that provides VICTIM.")

    override val supportsDelay = false

    override val parameters = setOf(
        TriggerParameter.VICTIM,
        TriggerParameter.EVENT
    )

    override fun onTrigger(config: Config, data: TriggerData, compileData: NoCompileData): Boolean {
        val event = data.event as? EntityDamageByEntityEvent ?: return false
        val victim = data.victim ?: return false

        if (RepeatedDamage.matches(event)) return false
        val world = victim.world.uid
        val sender = event.damager.tryAsLivingEntity()?.uniqueId ?: event.damager.uniqueId

        plugin.scheduler.run {
            if (event.isCancelled || !victim.isValid || victim.isDead || victim.world.uid != world ||
                !event.damage.isFinite() || event.damage <= 0 || data.player?.let { !it.isOnline || it.isDead || it.world.uid != world } == true) return@run
            // Read the finalized original base on the native next-tick scheduler.
            // Replaying that base must not reroll sender crit/strength/enchant procs.
            RepeatedDamage.run(event.damager.uniqueId, sender, victim.uniqueId) {
                victim.noDamageTicks = 0
                victim.damage(event.damage, event.damager)
            }
        }

        return true
    }
}
