package com.willfp.libreforge.conditions

import kotlin.test.*

class StateChangeRefreshTest {
    @Test fun `refresh observes committed state rather than event old state`() {
        val queue = StateChangeQueue<String>()
        var state = false
        var observed: Boolean? = null
        val tasks = mutableListOf<() -> Unit>()
        queue.enqueue("player", { tasks += it }, { true }) { observed = state }
        assertNull(observed)
        state = true
        tasks.single()()
        assertEquals(true, observed)
        tasks.clear()
        queue.enqueue("player", { tasks += it }, { true }) { observed = state }
        state = false
        tasks.single()()
        assertEquals(false, observed)
    }

    @Test fun `rapid toggles coalesce and use final state`() {
        val queue = StateChangeQueue<String>()
        val tasks = mutableListOf<() -> Unit>()
        var calls = 0
        repeat(20) { queue.enqueue("player", { tasks += it }, { true }) { calls++ } }
        assertEquals(1, tasks.size)
        tasks.single()()
        assertEquals(1, calls)
    }

    @Test fun `disconnected or replaced session is skipped and pending entry released`() {
        val queue = StateChangeQueue<String>()
        val tasks = mutableListOf<() -> Unit>()
        queue.enqueue("player", { tasks += it }, { false }) { fail("stale session refreshed") }
        tasks.single()()
        tasks.clear()
        queue.enqueue("player", { tasks += it }, { true }) {}
        assertEquals(1, tasks.size)
    }

    @Test fun `different players are independent`() {
        val queue = StateChangeQueue<String>()
        val tasks = mutableListOf<() -> Unit>()
        listOf("a", "b").forEach { queue.enqueue(it, { task -> tasks += task }, { true }) {} }
        assertEquals(2, tasks.size)
    }

    @Test fun `failed scheduling does not lock future refreshes`() {
        val queue = StateChangeQueue<String>()
        assertFailsWith<IllegalStateException> {
            queue.enqueue("a", { error("disabled") }, { true }) {}
        }
        var called = false
        queue.enqueue("a", { it() }, { true }) { called = true }
        assertTrue(called)
    }

    @Test fun `diagnostics are capped and resume in next window`() {
        val window = DiagnosticWindow()
        repeat(5) { assertTrue(window.allow(100, 5)) }
        assertFalse(window.allow(100, 5))
        assertTrue(window.allow(1_000_000_100, 5))
        assertFalse(window.allow(1_000_000_100, 0))
    }
}
