package com.willfp.libreforge.integrations.mythicmobs.impl.listener

import com.willfp.eco.core.fast.fast
import com.willfp.libreforge.plugin
import com.willfp.libreforge.triggers.impl.TriggerKill
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Tameable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import java.util.UUID

object MythicMobsKillListener : Listener {
    private val handledDeaths = mutableSetOf<UUID>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun handle(event: MythicMobDeathEvent) {
        val victim = event.entity as? LivingEntity ?: return
        if (!handledDeaths.add(victim.uniqueId)) {
            return
        }

        plugin.scheduler.runLater(1) {
            handledDeaths.remove(victim.uniqueId)
        }

        val player = when (val killer = event.killer) {
            is Player -> killer
            is Projectile -> killer.shooter as? Player
            is Tameable -> killer.owner as? Player
            else -> null
        } ?: return

        TriggerKill.force(player, victim)
    }
}
