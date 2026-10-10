package com.willfp.libreforge.diagnostics

import com.willfp.libreforge.plugin
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.Locale

internal object PerformanceMonitor {
    private val bean = ManagementFactory.getThreadMXBean()
    val recorder = PerformanceRecorder(cpuClock = { runCatching { bean.currentThreadCpuTime }.getOrDefault(-1) },
        cpuSupported = { runCatching { bean.isCurrentThreadCpuTimeSupported && bean.isThreadCpuTimeEnabled }.getOrDefault(false) })
    private var expiry: BukkitTask? = null
    var last: PerformanceSnapshot? = null
        private set
    @Volatile var lastFile: String? = null
        private set
    @Volatile var exporting = false
        private set

    fun start(seconds: Int): Boolean {
        check(Bukkit.isPrimaryThread())
        if (exporting || recorder.start(seconds) == null) return false
        last = null; lastFile = null
        expiry = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (recorder.session?.expired() == true) stop()
        }, 20, 20)
        return true
    }
    fun stop(): PerformanceSnapshot? {
        check(Bukkit.isPrimaryThread())
        val snapshot = recorder.stop() ?: return null
        expiry?.cancel(); expiry = null
        last = snapshot
        val directory = plugin.dataFolder.toPath().resolve("diagnostics")
        val file = directory.resolve("performance-${Instant.now().toEpochMilli()}-${snapshot.id}.tsv")
        val logger = plugin.logger
        exporting = true
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                Files.createDirectories(directory)
                Files.writeString(file, render(snapshot), StandardOpenOption.CREATE_NEW)
                lastFile = file.toString()
                logger.info("LibreForge performance report: $file (${snapshot.rows.size} rows)")
            } catch (failure: Exception) {
                logger.warning("Could not export performance report: ${failure.javaClass.simpleName}")
            } finally { exporting = false }
        })
        return snapshot
    }
    fun shutdown() {
        last = recorder.stop() ?: last
        expiry?.cancel(); expiry = null
        // No filesystem I/O on the game thread at shutdown. In-memory report remains bounded.
    }
    fun render(snapshot: PerformanceSnapshot): String = buildString {
        appendLine("# LibreForge instrumented operations; this is not a whole-server CPU profiler")
        appendLine("# session=${snapshot.id} elapsed_ms=${ms(snapshot.elapsed)} cpu_available=${snapshot.cpuSupported} overflow=${snapshot.overflow} depth_dropped=${snapshot.depthDropped} in_flight_at_stop=${snapshot.inFlight}")
        appendLine("# own excludes nested instrumented scopes on the same thread; inclusive rows overlap")
        appendLine("# wall minus CPU is off-CPU time, not exact I/O wait; CPU missing/partial is N/A")
        appendLine("# percentiles are power-of-two histogram upper bounds in nanoseconds, not exact percentiles")
        appendLine("# coverage: holder state updates, flush/provider/refresh phases, trigger dispatch, effect pipeline and its delayed repeats, AOE selection/targets, item broken check")
        appendLine("# excludes: other plugins' independent timers, JVM GC/JIT workers, independent permanent-effect timers and EcoEnchants Repairing timer; use spark alongside this report")
        appendLine("# counters=" + snapshot.counters.entries.joinToString(",") { "${clean(it.key)}=${it.value}" })
        appendLine("origin\toperation\tcalls\tfailures\twall_ms\town_wall_ms\tcpu_ms\town_cpu_ms\tcpu_calls\tmax_ms\tp50_upper_ms\tp95_upper_ms\tp99_upper_ms")
        for (row in snapshot.rows.sortedByDescending { it.ownWall }) {
            val cpuComplete = row.cpuCalls == row.calls
            appendLine(listOf(clean(row.key.origin), clean(row.key.operation), row.calls, row.failures,
                ms(row.wall), ms(row.ownWall), if (cpuComplete) ms(row.cpu) else "N/A",
                if (cpuComplete) ms(row.ownCpu) else "N/A", row.cpuCalls, ms(row.maximum),
                ms(row.p50Upper), ms(row.p95Upper), ms(row.p99Upper)).joinToString("\t"))
        }
    }
    private fun clean(value: String) = value.take(200).replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
    private fun ms(nanos: Long) = String.format(Locale.ROOT, "%.6f", nanos / 1_000_000.0)
}

internal inline fun <T> performanceScope(origin: () -> String, operation: () -> String, action: () -> T): T =
    PerformanceMonitor.recorder.measure({ PerformanceKey(origin(), operation()) }, action)

internal inline fun performanceCount(name: () -> String, amount: Long = 1) {
    val active = PerformanceMonitor.recorder.session ?: return
    if (!active.expired()) active.count(name(), amount)
}
