package com.willfp.libreforge

import kotlin.test.*

class RefreshTraceTest {
    @Test fun `phase returning null executes exactly once in enabled and disabled scopes`() {
        for (enabled in listOf(false, true)) {
            var calls = 0
            RefreshTracing.scoped(enabled) {
                assertNull(RefreshTracing.phase("holders") { calls++; null })
            }
            assertEquals(1, calls)
        }
    }

    @Test fun `phase totals are separate from nested detail timings`() {
        var time = 0L
        val trace = RefreshTrace { time }
        trace.phase("holders") {
            trace.holderRebuilt()
            trace.measure("provider:0") { time += 2000 }
            time += 1000
        }
        trace.phase("holders") { time += 4000 }
        assertEquals("holders=7us", trace.phaseSummary())
        assertEquals("provider:0=2us", trace.summary())
        assertEquals(1, trace.rebuildCount())
    }

    @Test fun `phase and detail cardinality remain bounded and existing keys keep accumulating`() {
        var time = 0L
        val trace = RefreshTrace { time }
        repeat(80) { index ->
            trace.measure("detail$index") { time += 1000 }
            trace.phase("phase$index") { time += 1000 }
        }
        trace.measure("detail0") { time += 100_000 }
        trace.phase("phase0") { time += 2000 }
        assertTrue(trace.summary().startsWith("detail0=101us"))
        assertEquals(16, trace.phaseSummary().split(", ").size)
        assertTrue(trace.phaseSummary().startsWith("phase0=3us"))
    }

    @Test fun `scopes preserve callback order multiplicity and restore on failure`() {
        RefreshTracing.scoped(true) {
            RefreshTracing.holderRebuilt()
            val calls = mutableListOf<String>()
            val failure = IllegalArgumentException("original")
            assertSame(failure, assertFailsWith<IllegalArgumentException> {
                RefreshTracing.scoped(true) {
                    RefreshTracing.phase("disable") { listOf("a", "a", "b").forEach { calls += "disable:$it" } }
                    RefreshTracing.phase("enable") {
                        listOf("a", "a", "b").forEach { calls += "enable:$it" }
                        throw failure
                    }
                }
            })
            assertEquals(listOf("disable:a", "disable:a", "disable:b", "enable:a", "enable:a", "enable:b"), calls)
            assertEquals(1, RefreshTracing.rebuildCount())
        }
        assertEquals(0, RefreshTracing.rebuildCount())
        assertEquals("", RefreshTracing.phaseSummary())
    }

    @Test fun `phase records failed action and preserves original exception`() {
        var time = 0L
        val trace = RefreshTrace { time }
        val failure = IllegalStateException("original")
        assertSame(failure, assertFailsWith<IllegalStateException> {
            trace.phase("holders") { time = 5000; throw failure }
        })
        assertEquals("holders=5us", trace.phaseSummary())
    }

    @Test fun `unsampled nested scope cannot contaminate sampled parent`() {
        RefreshTracing.scoped(true) {
            RefreshTracing.holderRebuilt()
            RefreshTracing.scoped(false) {
                RefreshTracing.holderRebuilt()
                RefreshTracing.measure({ error("disabled key evaluated") }) { Unit }
                assertEquals("", RefreshTracing.phaseSummary())
            }
            assertEquals(1, RefreshTracing.rebuildCount())
        }
    }

    @Test fun `measurement preserves return value and call count`() {
        var time = 0L
        var calls = 0
        val trace = RefreshTrace { time }
        assertEquals(42, trace.measure("a") { calls++; time = 2000; 42 })
        assertEquals(1, calls)
        assertEquals("a=2us", trace.summary())
    }
    @Test fun `measurement propagates exceptions and records time`() {
        var time = 0L
        val trace = RefreshTrace { time }
        assertFailsWith<IllegalStateException> {
            trace.measure("a") { time = 3000; error("original") }
        }
        assertEquals("a=3us", trace.summary())
    }
    @Test fun `repeated keys aggregate and summary shows five largest`() {
        var time = 0L
        val trace = RefreshTrace { time }
        repeat(8) { index -> trace.measure("$index") { time += index * 1000 } }
        assertEquals(5, trace.summary().split(", ").size)
        assertTrue(trace.summary().startsWith("7=7us"))
    }
    @Test fun `disabled tracing never evaluates diagnostic key`() {
        val previous = RefreshTracing.begin(false)
        try {
            assertEquals(7, RefreshTracing.measure({ error("diagnostic key evaluated") }) { 7 })
        } finally { RefreshTracing.restore(previous) }
    }
    @Test fun `nested trace restores parent and cleanup removes it`() {
        val outer = RefreshTracing.begin(true)
        val parent = RefreshTracing.begin(true)
        assertNotNull(parent)
        RefreshTracing.restore(parent)
        assertSame(parent, RefreshTracing.begin(false))
        RefreshTracing.restore(outer)
        assertEquals("", RefreshTracing.summary())
    }
}
