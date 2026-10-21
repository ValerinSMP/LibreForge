package com.willfp.libreforge.effects.orbitals

import com.willfp.libreforge.triggers.impl.physicalClick
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.player.PlayerToggleSneakEvent

internal object OrbitalInput {
    val modes = setOf("sneak_start", "left_click", "right_click", "shift_left_click", "shift_right_click")
    fun matches(mode: String, player: Player, event: Event?): Boolean {
        if (mode == "sneak_start") return event is PlayerToggleSneakEvent && event.player === player && !event.isCancelled && event.isSneaking
        val click = event?.let { physicalClick(it) } ?: return false
        return if (mode.startsWith("shift_")) player.isSneaking && mode.removePrefix("shift_") == click else mode == click
    }
}
