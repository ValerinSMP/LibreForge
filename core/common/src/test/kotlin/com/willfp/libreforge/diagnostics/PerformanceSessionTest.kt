package com.willfp.libreforge.diagnostics

import kotlin.test.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PerformanceSessionTest {
    @Test fun `disabled recorder does not read keys clocks or CPU and preserves action`() {
        val recorder = PerformanceRecorder(clock = { error("clock") }, cpuClock = { error("cpu") })
        var calls = 0
        assertEquals(42, recorder.measure({ error("key") }) { calls++; 42 })
        assertEquals(1, calls)
    }
    @Test fun `nested scopes preserve values and have disjoint own time`() {
        var wall = 0L; var cpu = 0L
        val recorder = PerformanceRecorder({ wall }, { cpu }, { true }); recorder.start(60)
        val parent = PerformanceKey("EcoItems:sword", "pipeline")
        val child = PerformanceKey("EcoItems:sword", "particle")
        assertEquals(7, recorder.measure({ parent }) {
            wall += 10; cpu += 5
            recorder.measure({ child }) { wall += 30; cpu += 15 }
            wall += 20; cpu += 10; 7
        })
        val rows = recorder.stop()!!.rows.associateBy { it.key }
        assertEquals(60, rows[parent]!!.wall); assertEquals(30, rows[parent]!!.ownWall)
        assertEquals(30, rows[child]!!.ownWall); assertEquals(15, rows[parent]!!.ownCpu)
        assertEquals(30, rows.values.sumOf { it.ownCpu })
    }
    @Test fun `exceptions are recorded without changing exception or leaving parent scope`() {
        var wall = 0L
        val recorder = PerformanceRecorder({ wall }); recorder.start(60)
        val failure = IllegalStateException("original")
        assertSame(failure, assertFailsWith<IllegalStateException> {
            recorder.measure({ PerformanceKey("a", "failed") }) { wall += 100; throw failure }
        })
        recorder.measure({ PerformanceKey("a", "after") }) { wall += 20 }
        val rows = recorder.stop()!!.rows
        assertEquals(1, rows.first { it.key.operation == "failed" }.failures)
        assertEquals(20, rows.first { it.key.operation == "after" }.ownWall)
    }
    @Test fun `expiration refuses new labels and preserves callback`() {
        var wall = 0L
        val recorder = PerformanceRecorder({ wall }); recorder.start(60)
        wall = 60_000_000_000
        assertEquals(11, recorder.measure({ error("expired label") }) { 11 })
        assertTrue(recorder.session!!.expired()); assertTrue(recorder.stop()!!.rows.isEmpty())
    }
    @Test fun `cardinality has overflow bucket and counts overflow`() {
        var wall = 0L
        val session = PerformanceSession(1, 60_000_000_000, { wall }, { -1 }, false, 2)
        repeat(6) { i -> session.begin(PerformanceKey("a", "$i"))!!.let { wall++; session.finish(it, false) } }
        val snapshot = session.snapshot(true)
        assertEquals(3, snapshot.rows.size); assertEquals(4, snapshot.overflow)
        assertEquals(4, snapshot.rows.single { it.key.origin == "overflow" }.calls)
    }
    @Test fun `nested unclosed keys cannot exceed cardinality bound`() {
        var wall = 0L
        val session = PerformanceSession(1, 60_000_000_000, { wall }, { -1 }, false, 2)
        val frames = (0..10).map { session.begin(PerformanceKey("a", "$it"))!! }
        frames.reversed().forEach { wall++; session.finish(it, false) }
        assertEquals(3, session.snapshot().rows.size)
    }
    @Test fun `CPU unavailable is not reported as zero CPU consumption`() {
        var wall = 0L
        val recorder = PerformanceRecorder({ wall }, { -1 }, { true }); recorder.start(60)
        recorder.measure({ PerformanceKey("a", "b") }) { wall += 100 }
        assertEquals(0, recorder.stop()!!.rows.single().cpuCalls)
    }
    @Test fun `closing session omits in flight callbacks and cannot contaminate next session`() {
        var wall = 0L
        val recorder = PerformanceRecorder({ wall }); val old = recorder.start(60)!!
        val frame = old.begin(PerformanceKey("old", "work"))!!
        val snapshot = recorder.stop()!!; assertEquals(1, snapshot.inFlight)
        recorder.start(60)
        wall += 100; old.finish(frame, false)
        recorder.measure({ PerformanceKey("new", "work") }) { wall += 10 }
        assertEquals(listOf("new"), recorder.stop()!!.rows.map { it.key.origin })
    }
    @Test fun `concurrent scopes are aggregated without cross thread nesting`() {
        val session = PerformanceSession(1, 60_000_000_000, System::nanoTime, { -1 }, false)
        val started = CountDownLatch(2); val release = CountDownLatch(1)
        val threads = (1..2).map {
            Thread {
                val frame = session.begin(PerformanceKey("a", "work"))!!
                started.countDown(); check(release.await(5, TimeUnit.SECONDS)); session.finish(frame, false)
            }.apply { start() }
        }
        assertTrue(started.await(5, TimeUnit.SECONDS)); release.countDown(); threads.forEach { it.join(5000); assertFalse(it.isAlive) }
        val row = session.snapshot(true).rows.single()
        assertEquals(2, row.calls); assertEquals(row.wall, row.ownWall)
    }
    @Test fun `histogram percentile upper bounds contain true sample and maximum is exact`() {
        var wall = 0L
        val session = PerformanceSession(1, 60_000_000_000, { wall }, { -1 }, false)
        repeat(100) { session.begin(PerformanceKey("a", "b"))!!.let { wall += 1000; session.finish(it, false) } }
        val row = session.snapshot().rows.single()
        assertEquals(1000, row.maximum); assertEquals(1023, row.p95Upper)
    }
    @Test fun `depth is bounded and reports omitted nested scopes`() {
        val session = PerformanceSession(1, 60_000_000_000, { 0 }, { -1 }, false)
        val frames = (1..64).map { session.begin(PerformanceKey("a", "$it"))!! }
        assertNull(session.begin(PerformanceKey("a", "65")))
        frames.reversed().forEach { session.finish(it, false) }
        assertEquals(1, session.snapshot().depthDropped)
    }
    @Test fun `event counters are bounded and cannot change after close`() {
        val session = PerformanceSession(1, 60_000_000_000, { 0 }, { -1 }, false)
        repeat(200) { session.count("event:$it", 1) }
        session.count("event:0", 3)
        val snapshot = session.snapshot(true)
        assertEquals(129, snapshot.counters.size)
        assertEquals(72L, snapshot.counters["counter-overflow"])
        assertEquals(4L, snapshot.counters["event:0"])
        session.count("event:0", 100)
        assertEquals(snapshot.counters, session.snapshot().counters)
    }
    @Test fun `stop is idempotent and second start is refused during recording`() {
        val recorder = PerformanceRecorder({ 0 })
        assertNotNull(recorder.start(60)); assertNull(recorder.start(120))
        assertNotNull(recorder.stop()); assertNull(recorder.stop()); assertNotNull(recorder.start(180))
        recorder.stop()
    }
    @Test fun `oversized labels are truncated and reused rather than exceeding memory bounds`() {
        val session = PerformanceSession(1, 60_000_000_000, { 0 }, { -1 }, false, 1)
        repeat(5) { session.begin(PerformanceKey("a".repeat(500), "b".repeat(500)))!!.let { session.finish(it, false) } }
        val snapshot = session.snapshot()
        assertEquals(1, snapshot.rows.size); assertEquals(0L, snapshot.overflow)
        assertEquals(200, snapshot.rows.single().key.origin.length)
        assertEquals(5L, snapshot.rows.single().calls)
    }
    @Test fun `large mixed holder and operation set is retained within explicit bound`() {
        val session = PerformanceSession(1, 60_000_000_000, { 0 }, { -1 }, false)
        repeat(4000) { index ->
            val frame = session.begin(PerformanceKey("holder:${index / 20}", "operation:${index % 20}"))!!
            session.finish(frame, false)
        }
        assertEquals(4000, session.snapshot().rows.size)
        assertEquals(0L, session.snapshot().overflow)
        repeat(200) { index ->
            val frame = session.begin(PerformanceKey("extra", "$index"))!!
            session.finish(frame, false)
        }
        val report = session.snapshot(true)
        assertEquals(4097, report.rows.size)
        assertEquals(104L, report.overflow)
    }
}
