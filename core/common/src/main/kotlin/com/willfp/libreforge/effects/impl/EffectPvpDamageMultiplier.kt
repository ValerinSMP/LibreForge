package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.getDoubleFromExpression
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import com.willfp.libreforge.triggers.tryAsLivingEntity
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent

/** Multiplies only damage dealt by one player to another, including player projectiles. */
object EffectPvpDamageMultiplier : Effect<NoCompileData>("pvp_damage_multiplier") {
    override val description = "Multiplies damage taken from another player."
    override val categories = setOf("combat")
    override val supportsDelay = false
    override val parameters = setOf(TriggerParameter.EVENT)

    override val arguments = arguments {
        require(
            "multiplier",
            "You must specify the damage multiplier!",
            description = "The damage multiplier to apply. Supports expressions.",
            type = ArgType.EXPRESSION,
            example = "0.8"
        )
    }

    override fun onTrigger(config: Config, data: TriggerData, compileData: NoCompileData): Boolean {
        val event = data.event as? EntityDamageByEntityEvent ?: return false
        return applyPvpDamageMultiplier(event, config.getDoubleFromExpression("multiplier", data))
    }
}

internal fun applyPvpDamageMultiplier(event: EntityDamageByEntityEvent, multiplier: Double): Boolean {
    val damage = calculatePvpDamage(event.damager, event.entity, event.damage, multiplier) ?: return false
    event.damage = damage
    return true
}

internal fun calculatePvpDamage(source: Entity, victim: Entity, damage: Double, multiplier: Double): Double? {
    if (!isPlayerVsPlayerDamage(source, victim)) return null
    return damage * multiplier
}

internal fun isPlayerVsPlayerDamage(source: Entity, victim: Entity): Boolean {
    val playerSource = source.tryAsLivingEntity() as? Player ?: return false
    val playerVictim = victim as? Player ?: return false
    return playerSource.uniqueId != playerVictim.uniqueId
}
