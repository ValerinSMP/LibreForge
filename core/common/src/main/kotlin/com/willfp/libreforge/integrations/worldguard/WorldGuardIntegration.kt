package com.willfp.libreforge.integrations.worldguard

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import com.sk89q.worldguard.protection.flags.Flags
import com.willfp.eco.core.EcoPlugin
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.filters.Filters
import com.willfp.libreforge.integrations.LoadableIntegration
import com.willfp.libreforge.integrations.worldguard.impl.ConditionInRegion
import com.willfp.libreforge.integrations.worldguard.impl.FilterRegion
import com.willfp.libreforge.integrations.worldguard.impl.TriggerEnterRegion
import com.willfp.libreforge.integrations.worldguard.impl.TriggerLeaveRegion
import com.willfp.libreforge.triggers.Triggers
import org.bukkit.Location
import org.bukkit.entity.Player

object WorldGuardIntegration : LoadableIntegration {
    override fun load(plugin: EcoPlugin) {
        Conditions.register(ConditionInRegion)
        Triggers.register(TriggerEnterRegion)
        Triggers.register(TriggerLeaveRegion)
        Filters.register(FilterRegion)
    }

    override fun getPluginName(): String {
        return "WorldGuard"
    }

    fun canTransmit(player: Player, from: Location, to: Location): Boolean {
        val localPlayer = WorldGuardPlugin.inst().wrapPlayer(player)
        val query = WorldGuard.getInstance().platform.regionContainer.createQuery()
        val fromAllowsPvp = query.testState(BukkitAdapter.adapt(from), localPlayer, Flags.PVP)
        val toAllowsPvp = query.testState(BukkitAdapter.adapt(to), localPlayer, Flags.PVP)

        return !fromAllowsPvp || toAllowsPvp
    }
}
