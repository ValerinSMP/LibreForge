package com.willfp.libreforge.diagnostics

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

internal data class PerformanceKey(val origin: String, val operation: String)
internal data class PerformanceRow(
    val key: PerformanceKey, val calls: Long, val failures: Long,
    val wall: Long, val ownWall: Long, val cpu: Long, val ownCpu: Long, val cpuCalls: Long,
    val maximum: Long, val p50Upper: Long, val p95Upper: Long, val p99Upper: Long
)
internal data class PerformanceSnapshot(
    val id: Long, val elapsed: Long, val rows: List<PerformanceRow>, val overflow: Long,
    val depthDropped: Long, val inFlight: Long, val cpuSupported: Boolean, val counters: Map<String, Long>
)

/** In-memory aggregates only; clocks are injected to test nested and concurrent scopes. */
internal class PerformanceSession(
    val id: Long, val durationNanos: Long, private val clock: () -> Long,
    private val cpuClock: () -> Long, val cpuSupported: Boolean, private val maxKeys: Int = 4096
) {
    private val started = clock()
    private val stack = ThreadLocal.withInitial { ArrayDeque<Frame>() }
    private val rows = LinkedHashMap<PerformanceKey, MutableRow>()
    private val overflowKey = PerformanceKey("overflow", "other")
    private val counters = LinkedHashMap<String, Long>()
    private var overflow = 0L
    private var depthDropped = 0L
    private var inFlight = 0L
    @Volatile private var closed = false

    internal class Frame(val key: PerformanceKey, val wall: Long, val cpu: Long) {
        var childWall = 0L
        var childCpu = 0L
        var childrenCpuValid = true
    }
    private class MutableRow {
        var calls = 0L; var failures = 0L; var wall = 0L; var ownWall = 0L
        var cpu = 0L; var ownCpu = 0L; var cpuCalls = 0L; var maximum = 0L
        val histogram = LongArray(64)
        fun percentile(p: Double): Long {
            if (calls == 0L) return 0
            val target = ceil(calls * p).toLong().coerceAtLeast(1)
            var count = 0L
            for (i in histogram.indices) {
                count += histogram[i]
                if (count >= target) return if (i >= 62) Long.MAX_VALUE else (1L shl i) - 1
            }
            return 0
        }
    }
    @Synchronized fun count(name: String, amount: Long) {
        if (closed || expired()) return
        val safeName = name.take(200)
        val key = if (safeName in counters || counters.size < 128) safeName else "counter-overflow"
        counters[key] = (counters[key] ?: 0) + amount
    }
    fun expired(): Boolean = clock() - started >= durationNanos
    fun elapsed(): Long = (clock() - started).coerceAtLeast(0)
    fun begin(key: PerformanceKey): Frame? {
        if (closed || expired()) return null
        val frames = stack.get()
        if (frames.size >= 64) {
            synchronized(this) { depthDropped++ }
            return null
        }
        val safeKey = PerformanceKey(key.origin.take(200), key.operation.take(200))
        val bounded = synchronized(this) {
            if (closed) return null
            inFlight++
            val accepted = if (safeKey in rows || rows.size < maxKeys) safeKey else { overflow++; overflowKey }
            rows.getOrPut(accepted) { MutableRow() }
            accepted
        }
        val frame = Frame(bounded, clock(), if (cpuSupported) cpuClock() else -1)
        frames.addLast(frame)
        return frame
    }
    fun finish(frame: Frame, failed: Boolean) {
        val wall = (clock() - frame.wall).coerceAtLeast(0)
        val endCpu = if (cpuSupported) cpuClock() else -1
        val cpu = if (frame.cpu >= 0 && endCpu >= frame.cpu) endCpu - frame.cpu else -1
        val frames = stack.get()
        check(frames.peekLast() === frame) { "Diagnostic scopes must close on their originating thread in LIFO order" }
        frames.removeLast()
        frames.peekLast()?.let {
            it.childWall += wall
            if (cpu >= 0) it.childCpu += cpu else it.childrenCpuValid = false
        }
        if (frames.isEmpty()) stack.remove()
        synchronized(this) {
            inFlight--
            if (closed) return
            val row = rows.getOrPut(frame.key) { MutableRow() }
            row.calls++; if (failed) row.failures++
            row.wall += wall; row.ownWall += (wall - frame.childWall).coerceAtLeast(0)
            if (cpu >= 0 && frame.childrenCpuValid) {
                row.cpu += cpu; row.ownCpu += (cpu - frame.childCpu).coerceAtLeast(0); row.cpuCalls++
            }
            row.maximum = maxOf(row.maximum, wall)
            val bucket = if (wall == 0L) 0 else (64 - java.lang.Long.numberOfLeadingZeros(wall)).coerceAtMost(63)
            row.histogram[bucket]++
        }
    }
    @Synchronized fun snapshot(close: Boolean = false): PerformanceSnapshot {
        if (close) closed = true
        return PerformanceSnapshot(id, elapsed(), rows.map { (key, row) ->
            PerformanceRow(key, row.calls, row.failures, row.wall, row.ownWall, row.cpu,
                row.ownCpu, row.cpuCalls, row.maximum, row.percentile(.50), row.percentile(.95), row.percentile(.99))
        }, overflow, depthDropped, inFlight, cpuSupported, counters.toMap())
    }
}

/** No Bukkit dependency, and no key/clock evaluation while disabled. */
internal class PerformanceRecorder(
    private val clock: () -> Long = System::nanoTime,
    private val cpuClock: () -> Long = { -1 },
    private val cpuSupported: () -> Boolean = { false }
) {
    private val sequence = AtomicLong()
    @Volatile var session: PerformanceSession? = null
        private set
    @Synchronized fun start(seconds: Int): PerformanceSession? {
        require(seconds in 60..180)
        if (session != null) return null
        return PerformanceSession(sequence.incrementAndGet(), seconds * 1_000_000_000L, clock, cpuClock, cpuSupported()).also { session = it }
    }
    @Synchronized fun stop(): PerformanceSnapshot? {
        val previous = session ?: return null
        session = null
        return previous.snapshot(true)
    }
    inline fun <T> measure(key: () -> PerformanceKey, action: () -> T): T {
        val active = session ?: return action()
        if (active.expired()) return action()
        val frame = active.begin(key()) ?: return action()
        var failed = true
        try {
            val result = action()
            failed = false
            return result
        } finally { active.finish(frame, failed) }
    }
}
