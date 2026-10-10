package com.willfp.libreforge.diagnostics

import java.lang.management.ManagementFactory
import kotlin.test.*

class PerformanceOverheadTest {
    @Test fun `bounded diagnostic micro measurement records overhead without a server performance claim`() {
        val bean = ManagementFactory.getThreadMXBean()
        val recorder = PerformanceRecorder(cpuClock = { bean.currentThreadCpuTime },
            cpuSupported = { bean.isCurrentThreadCpuTimeSupported && bean.isThreadCpuTimeEnabled })
        var value = 0L
        val key = PerformanceKey("benchmark", "noop")
        repeat(30_000) { recorder.measure({ key }) { value++ } }
        val iterations = 100_000
        val offStart = System.nanoTime()
        repeat(iterations) { recorder.measure({ error("disabled label") }) { value++ } }
        val off = System.nanoTime() - offStart
        recorder.start(60)
        repeat(30_000) { recorder.measure({ key }) { value++ } }
        val onStart = System.nanoTime()
        repeat(iterations) { recorder.measure({ key }) { value++ } }
        val on = System.nanoTime() - onStart
        assertEquals(260_000L, value)
        assertEquals(130_000L, recorder.stop()!!.rows.single().calls)
        println("DIAGNOSTIC_MICRO_MEASUREMENT iterations=$iterations disabled_ns_per_call=${off / iterations} enabled_ns_per_call=${on / iterations}; includes instrumentation, JVM warmup and scheduler noise; not gameplay overhead")
    }
}
