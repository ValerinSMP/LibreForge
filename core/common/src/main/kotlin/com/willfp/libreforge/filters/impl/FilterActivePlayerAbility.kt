package com.willfp.libreforge.filters.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.effects.impl.DamageMultiplierBypass
import com.willfp.libreforge.filters.Filter
import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent

/** Receiver-only genuine synchronous provenance; MAGIC and a guessed projectile type are not evidence. */
object FilterActivePlayerAbility : Filter<NoCompileData,Boolean>("active_player_ability") {
    override val valueType = ArgType.BOOLEAN
    override val description = "Matches attributed active ability damage from another player to this receiver."
    override fun getValue(config: Config,data: TriggerData?,key: String) = config.getBool(key)
    override fun isMet(data: TriggerData, value: Boolean, compileData: NoCompileData): Boolean =
        activePlayerAbilityMatches(data) == value
}

internal fun activePlayerAbilityMatches(data: TriggerData): Boolean {
    val event = data.event as? EntityDamageByEntityEvent ?: return false
    val receiver = event.entity as? Player ?: return false
    if (data.player !== receiver || event.isCancelled) return false
    val sender = event.damager as? Player
        ?: (event.damager as? Projectile)?.shooter as? Player
        ?: return false
    return DamageMultiplierBypass.matchesActivePlayerDamage(sender, receiver)
}
