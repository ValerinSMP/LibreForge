package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.ViolationContext
import com.willfp.libreforge.arguments
import com.willfp.libreforge.dealDamage
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.getDoubleFromExpression
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter

data class DamageVictimCompileData(val origin: DamageOrigin = DamageOrigin.NORMAL)

object EffectDamageVictim : Effect<DamageVictimCompileData>("damage_victim") {
    override val description = "Deals damage to the victim."
    override val categories = setOf("combat")

    override val parameters = setOf(
        TriggerParameter.VICTIM
    )

    override val arguments = arguments {
        optional("prevent_recursive",description="Reject secondary damage and suppress repeated attacker procs.",type=ArgType.BOOLEAN,default="false")
        optional("check_antigrief",description="Validate the source against target protections.",type=ArgType.BOOLEAN,default="false")
        require(
            "damage",
            "You must specify the amount of damage!",
            description = "The amount of damage to deal. Supports expressions.",
            type = ArgType.EXPRESSION,
            example = "%level% * 2"
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

    override fun makeCompileData(config: Config, context: ViolationContext): DamageVictimCompileData =
        DamageVictimCompileData(damageOrigin(config))

    override fun onTrigger(config: Config, data: TriggerData, compileData: DamageVictimCompileData): Boolean {
        val recursiveGuard=config.getBool("prevent_recursive")
        if(recursiveGuard&&DamageMultiplierBypass.current!=null)return false
        if(recursiveGuard&&(data.event as? org.bukkit.event.Cancellable)?.isCancelled==true)return false
        val victim = data.victim ?: return false
        val player = data.player
        val damage = config.getDoubleFromExpression("damage", data)
        val source = if (config.getBool("use_source")) player else null
        val provenance = DamageProvenance(compileData.origin, source?.uniqueId)

        val apply={ DamageMultiplierBypass.withProvenance(provenance) {
            victim.dealDamage(
                damage,
                source = source,
                trueDamage = config.getBool("true_damage"),
                checkAntigrief = config.getBool("check_antigrief")
            )
            true
        } }
        return if(recursiveGuard&&player!=null)RepeatedDamage.run(player.uniqueId,player.uniqueId,victim.uniqueId,apply)else apply()
    }
}
