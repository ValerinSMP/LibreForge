package com.willfp.libreforge.triggers.impl

import com.willfp.libreforge.effects.impl.DamageMultiplierBypass
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent

/** Fires only when a player blocks a marked active PvP ability with a shield. */
object TriggerActiveAbilityShieldBlock : Trigger("active_ability_shield_block") {
    override val description = "Fires when the player blocks a marked active PvP ability with a shield."
    override val categories = setOf("combat")
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.VICTIM,
        TriggerParameter.EVENT,
        TriggerParameter.LOCATION,
        TriggerParameter.VALUE
    )

    @EventHandler(ignoreCancelled = true)
    fun handle(event: EntityDamageByEntityEvent) {
        if (event.cause == EntityDamageEvent.DamageCause.THORNS) return
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return
        if (!victim.isBlocking || !DamageMultiplierBypass.matchesActivePlayerDamage(attacker, victim)) return

        dispatch(
            victim.toDispatcher(),
            TriggerData(
                player = victim,
                victim = attacker,
                location = attacker.location,
                event = event,
                value = event.damage
            )
        )
    }
}
