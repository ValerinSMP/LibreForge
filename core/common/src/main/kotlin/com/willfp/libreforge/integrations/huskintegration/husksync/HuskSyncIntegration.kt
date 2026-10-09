package com.willfp.libreforge.integrations.huskintegration.husksync

import com.willfp.eco.core.EcoPlugin
import com.willfp.libreforge.effects.impl.EffectPermanentPotionEffect
import com.willfp.libreforge.forceRefreshHolders
import com.willfp.libreforge.integrations.LoadableIntegration
import com.willfp.libreforge.toDispatcher
import net.william278.husksync.event.BukkitSyncCompleteEvent
import net.william278.husksync.user.BukkitUser
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

object HuskSyncIntegration : LoadableIntegration {
    override fun getPluginName() = "HuskSync"

    override fun load(plugin: EcoPlugin) {
        plugin.server.pluginManager.registerEvents(HuskSyncEffectListener(plugin), plugin)
    }
}

private class HuskSyncEffectListener(private val plugin: EcoPlugin) : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onSyncComplete(event: BukkitSyncCompleteEvent) {
        val player = (event.user as? BukkitUser)?.player ?: return
        // Defer to the main thread after HuskSync completes and unlocks the player.
        refreshAfterSync(
            schedule = { action -> plugin.scheduler.run { action() } },
            isOnline = { player.isOnline },
            refreshHolders = { player.toDispatcher().forceRefreshHolders() },
            restorePotions = { EffectPermanentPotionEffect.restoreMissingEffects(player) }
        )
    }
}
