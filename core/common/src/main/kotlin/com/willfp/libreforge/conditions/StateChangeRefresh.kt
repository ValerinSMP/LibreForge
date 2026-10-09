package com.willfp.libreforge.conditions

import com.willfp.libreforge.plugin
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.updateEffects
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/** Coalesce state events until the server has committed the new state. Main thread only. */
internal class StateChangeQueue<K> {
    private val pending = mutableSetOf<K>()

    fun enqueue(key: K, schedule: (() -> Unit) -> Unit, valid: () -> Boolean, refresh: () -> Unit) {
        if (!pending.add(key)) return
        try {
            schedule {
                pending.remove(key)
                if (valid()) refresh()
            }
        } catch (failure: Exception) {
            pending.remove(key)
            throw failure
        }
    }
}

/** Global cap avoids console spam even when diagnostic mode is enabled for all players. */
internal class DiagnosticWindow {
    private var start = Long.MIN_VALUE
    private var count = 0

    fun allow(now: Long, limit: Int): Boolean {
        if (limit <= 0) return false
        if (start == Long.MIN_VALUE || now - start >= 1_000_000_000L) {
            start = now
            count = 0
        }
        if (count >= limit) return false
        count++
        return true
    }
}

private val stateChanges = StateChangeQueue<Player>()
private val diagnosticWindow = DiagnosticWindow()

internal fun refreshAfterStateChange(player: Player) {
    stateChanges.enqueue(
        key = player,
        schedule = { action -> plugin.scheduler.run { action() } },
        valid = { player.isOnline && Bukkit.getPlayer(player.uniqueId) === player },
        refresh = {
            val debug = plugin.configYml.getBool("diagnostics.state-refresh.enabled")
            val filter = plugin.configYml.getString("diagnostics.state-refresh.player")
            val trace = debug && !filter.isNullOrBlank() &&
                    (filter == "*" || filter.equals(player.name, ignoreCase = true)) &&
                    diagnosticWindow.allow(System.nanoTime(), 5)
            val started = if (trace) System.nanoTime() else 0L
            player.toDispatcher().updateEffects()
            if (trace) {
                plugin.logger.info("State refresh: player=${player.name} sneaking=${player.isSneaking} " +
                        "sprinting=${player.isSprinting} duration_us=${(System.nanoTime() - started) / 1000}")
            }
        }
    )
}
