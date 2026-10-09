package com.willfp.libreforge

import com.willfp.libreforge.effects.impl.DamageMultiplierBypass
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

internal object ActiveAbilityPvpListener : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun handle(event: EntityDamageByEntityEvent) {
        if (!plugin.configYml.getBool("effects.pvp-abilities.enabled")) return
        val source = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return
        if (!DamageMultiplierBypass.matchesActivePlayerDamage(source, victim)) return

        val base = event.damage
        event.damage = base * plugin.configYml.getDouble("effects.pvp-abilities.damage-multiplier")

        val cap = (victim.getAttribute(Attribute.MAX_HEALTH)?.value ?: victim.maxHealth) *
            plugin.configYml.getDouble("effects.pvp-abilities.max-health-fraction")
        val beforeCap = event.finalDamage
        if (beforeCap > cap && beforeCap > 0.0) {
            event.damage *= cap / beforeCap
        }

        if (plugin.configYml.getBool("effects.pvp-abilities.debug")) {
            plugin.logger.info(
                "[PvP] ability ${source.name} -> ${victim.name}: base=$base, calculated=${event.finalDamage}, cap=$cap"
            )
        }
    }
}
