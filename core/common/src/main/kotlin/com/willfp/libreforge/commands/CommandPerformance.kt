package com.willfp.libreforge.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.libreforge.diagnostics.PerformanceMonitor
import com.willfp.libreforge.plugin
import org.bukkit.command.CommandSender
import java.util.Locale

internal object CommandPerformance : Subcommand(plugin, "performance", "libreforge.command.performance", false) {
    private fun message(key: String, fallback: String): String =
        plugin.langYml.getStringOrNull("messages.performance-$key")?.takeIf { it.isNotBlank() } ?: fallback
    override fun onExecute(sender: CommandSender, args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            "start" -> {
                val seconds = args.getOrNull(1)?.toIntOrNull() ?: if (args.size == 1) 120 else 0
                if (seconds !in 60..180) {
                    sender.sendMessage(message("duration", "Duration must be between 60 and 180 seconds.")); return
                }
                sender.sendMessage(if (PerformanceMonitor.start(seconds))
                    message("started", "Performance capture started for %seconds% seconds.").replace("%seconds%", seconds.toString())
                else message("busy", "A performance capture or report export is already running."))
            }
            "stop" -> sender.sendMessage(if (PerformanceMonitor.stop() != null)
                message("stopped", "Capture stopped. Report will be exported; use /libreforge performance report.")
            else message("inactive", "No performance capture is running."))
            "status" -> {
                sender.sendMessage(com.willfp.libreforge.effects.templates.MiningBatches.status())
                val active = PerformanceMonitor.recorder.session
                sender.sendMessage(if (active == null) message("inactive", "No performance capture is running.") else
                    message("status", "Capture %id%: %seconds%s elapsed; CPU available: %cpu%.")
                        .replace("%id%", active.id.toString()).replace("%seconds%", (active.elapsed() / 1_000_000_000).toString())
                        .replace("%cpu%", active.cpuSupported.toString()))
            }
            "report" -> {
                sender.sendMessage(com.willfp.libreforge.effects.templates.MiningBatches.status())
                val snapshot = PerformanceMonitor.last ?: PerformanceMonitor.recorder.session?.snapshot()
                if (snapshot == null) { sender.sendMessage(message("no-report", "No performance report available.")); return }
                val metric = args.getOrNull(1)?.lowercase() ?: if (snapshot.cpuSupported) "cpu" else "wall"
                if (metric !in listOf("cpu", "wall")) { sender.sendMessage("/libreforge performance report [cpu|wall]"); return }
                sender.sendMessage(message("header", "Top operations by own %metric% time (instrumented coverage only):").replace("%metric%", metric))
                for (row in snapshot.rows.sortedByDescending { if (metric == "cpu") if (it.cpuCalls == it.calls && it.calls > 0) it.ownCpu else -1 else it.ownWall }.take(10)) {
                    sender.sendMessage("${row.key.origin.take(100)} | ${row.key.operation.take(100)} | calls=${row.calls} cpu_ms=${if (row.cpuCalls == row.calls && row.calls > 0) String.format(Locale.ROOT, "%.3f", row.ownCpu / 1_000_000.0) else "N/A"} own_wall_ms=${String.format(Locale.ROOT, "%.3f", row.ownWall / 1_000_000.0)} max_ms=${String.format(Locale.ROOT, "%.3f", row.maximum / 1_000_000.0)}")
                }
                sender.sendMessage("overflow=${snapshot.overflow} depth_dropped=${snapshot.depthDropped} in_flight=${snapshot.inFlight}; file=${PerformanceMonitor.lastFile ?: "pending / active session"}")
            }
            else -> sender.sendMessage("/libreforge performance <start [60-180]|stop|status|report [cpu|wall]>")
        }
    }
    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> = when (args.size) {
        1 -> listOf("start", "stop", "status", "report").filter { it.startsWith(args[0], true) }
        2 -> when (args[0].lowercase()) {
            "start" -> listOf("60", "120", "180").filter { it.startsWith(args[1]) }
            "report" -> listOf("cpu", "wall").filter { it.startsWith(args[1], true) }
            else -> emptyList()
        }
        else -> emptyList()
    }
}
