package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.Prerequisite
import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.arguments
import com.willfp.libreforge.dealDamage
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.getDoubleFromExpression
import com.willfp.libreforge.getIntFromExpression
import com.willfp.libreforge.plugin
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import com.willfp.libreforge.triggers.impl.TriggerKill


object EffectBleed : Effect<DamageOrigin>("bleed") {
    override val description = "Deals damage to the victim repeatedly over a set number of ticks."
    override val categories = setOf("combat")

    override val parameters = setOf(
        TriggerParameter.VICTIM
    )

    override val arguments = arguments {
        require(
            "amount",
            "You must specify the amount of bleed ticks!",
            description = "The number of times damage is dealt. Supports expressions.",
            type = ArgType.EXPRESSION,
            example = "3 + %level%"
        )
        require(
            "damage",
            "You must specify the amount of damage to deal!",
            description = "The damage dealt per interval. Supports expressions.",
            type = ArgType.EXPRESSION,
            example = "%level% * 0.5"
        )
        require(
            "interval",
            "You must specify the tick delay between damages!",
            description = "The number of ticks between each damage application. Supports expressions.",
            type = ArgType.EXPRESSION
        )
        optional(
            "true_damage",
            description = "If true, damage bypasses armor and resistance effects.",
            type = ArgType.BOOLEAN,
            default = "false"
        )
        optional(
            "use_source",
            description = "If true, the player is attributed as the damage source.",
            type = ArgType.BOOLEAN,
            default = "false"
        )
        optional(
            "damage_origin",
            description = "Classifies this damage as normal, passive, or active.",
            type = ArgType.STRING,
            default = "NORMAL",
            choices = listOf("NORMAL", "PASSIVE", "ACTIVE")
        )
    }

    override fun onTrigger(config: Config, data: TriggerData, compileData: DamageOrigin): Boolean {
        val victim = data.victim ?: return false

        val damage = config.getDoubleFromExpression("damage", data)
        val interval = config.getIntFromExpression("interval", data)
        val amount = config.getIntFromExpression("amount", data)
        val trueDamage = config.getBool("true_damage")
        val source = if (config.getBool("use_source")) data.player else null
        val provenance = DamageProvenance(compileData, source?.uniqueId)

        val targets = com.willfp.libreforge.CombatTargets.context(data)
        var current = 0

        plugin.runnableFactory.create { task ->
            if (targets?.allows(victim) == false) { task.cancel(); return@create }
            current++

            val killed = damage >= victim.health

            if (killed) {
                if (Prerequisite.HAS_PAPER.isMet) {
                    victim.killer = data.player
                }

                if (data.player != null) {
                    TriggerKill.force(
                        data.player,
                        victim
                    )
                }
            }

            com.willfp.libreforge.CombatTargets.with(targets) {
                DamageMultiplierBypass.withProvenance(provenance) {
                    victim.dealDamage(damage, source, trueDamage)
                }
            }

            if (current >= amount || killed) {
                task.cancel()
            }
        }.runTaskTimer(interval.toLong(), interval.toLong())

        return true
    }

    override fun makeCompileData(config: Config, context: com.willfp.libreforge.ViolationContext) = damageOrigin(config)
}
