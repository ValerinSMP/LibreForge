package com.willfp.libreforge.effects.templates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FairMiningQueueTest {
    private var time = 0L
    private val queue = FairMiningQueue<Int, Int> { time }
    private val executed = mutableListOf<Pair<Int, Int>>()
    private val discarded = mutableListOf<Pair<String, List<Int>>>()
    private fun add(owner: Int, items: List<Int>, tick: Long = 0,
                    valid: () -> Boolean = { true }, action: (Int) -> Unit = { executed.add(owner to it) },
                    total: Int = 8192, player: Int = 256): Boolean =
        queue.enqueue(owner, items, tick, total, player, valid, action,
            { rest, reason -> discarded.add(reason to rest) })
    private fun drain(tick: Long = 1, limit: Int = 24, budget: Long = 100, age: Long = 40) =
        queue.drain(tick, limit, budget, age)

    @Test fun `40 active players share one count limit with fair turns across ticks`() {
        repeat(40) { add(it, (0..7).toList()) }
        drain()
        assertEquals((0..23).map { it to 0 }, executed)
        assertEquals(296, queue.pending)
        drain()
        assertEquals((24..39).map { it to 0 } + (0..7).map { it to 1 }, executed.drop(24))
        assertEquals(40, queue.players)
        repeat(12) { drain() }
        assertEquals(0, queue.pending)
        assertEquals(0, queue.players)
        repeat(40) { owner -> assertEquals((0..7).toList(), executed.filter { it.first == owner }.map { it.second }) }
    }

    @Test fun `100 online identities cannot bypass global admission and rejected jobs are atomic`() {
        repeat(100) { assertTrue(add(it, listOf(1, 2), total = 200)) }
        assertFalse(add(100, listOf(3), total = 200))
        assertEquals(200, queue.pending)
        assertEquals(1, queue.rejected)
        drain(limit = 250)
        assertEquals(200, executed.size)
        assertEquals(0, queue.pending)
    }

    @Test fun `player cap and FIFO apply across separate jobs`() {
        assertTrue(add(1, listOf(1, 2), player = 3))
        assertFalse(add(1, listOf(3, 4), player = 3))
        assertTrue(add(1, listOf(5), player = 3))
        drain()
        assertEquals(listOf(1 to 1, 1 to 2, 1 to 5), executed)
    }

    @Test fun `wall budget waits for callback completion then stops`() {
        add(1, listOf(1, 2, 3), action = { executed.add(1 to it); time += 120 })
        drain(budget = 100)
        assertEquals(listOf(1 to 1), executed)
        assertEquals(2, queue.pending)
        drain(budget = 100)
        assertEquals(listOf(1 to 1, 1 to 2), executed)
    }

    @Test fun `expiry discards old work without executing and fresh jobs still run`() {
        add(1, listOf(1, 2), tick = 0)
        add(1, listOf(3), tick = 20)
        drain(tick = 40)
        assertEquals(listOf(1 to 3), executed)
        assertEquals(listOf("expired" to listOf(1, 2)), discarded)
        assertEquals(2, queue.discarded)
    }

    @Test fun `invalid session cancels remaining job and preserves another owner`() {
        add(1, listOf(1, 2), valid = { false })
        add(2, listOf(3))
        drain()
        assertEquals(listOf(2 to 3), executed)
        assertEquals(listOf("invalid" to listOf(1, 2)), discarded)
    }

    @Test fun `disconnect cancel releases admission and cannot run later`() {
        add(1, listOf(1, 2)); add(2, listOf(3))
        queue.cancel(1)
        assertEquals(1, queue.pending)
        drain()
        assertEquals(listOf(2 to 3), executed)
        assertEquals(listOf("cancelled" to listOf(1, 2)), discarded)
    }

    @Test fun `guards are rechecked after every block so broken tools stop the remaining work`() {
        var usable = true
        add(1, listOf(1, 2, 3), valid = { usable }, action = { executed.add(1 to it); usable = false })
        drain()
        assertEquals(listOf(1 to 1), executed)
        assertEquals(listOf("invalid" to listOf(2, 3)), discarded)
        assertEquals(0, queue.pending)
    }

    @Test fun `callback cancellation cannot resurrect owner or duplicate discard`() {
        add(1, listOf(1, 2, 3), action = { executed.add(1 to it); queue.cancel(1) })
        drain()
        assertEquals(listOf(1 to 1), executed)
        assertEquals(listOf("cancelled" to listOf(2, 3)), discarded)
        assertEquals(0, queue.pending)
        assertEquals(0, queue.players)
    }

    @Test fun `reentrant new session with same owner receives exactly one turn`() {
        add(1, listOf(1, 2), action = {
            executed.add(1 to it)
            queue.cancel(1)
            add(1, listOf(3, 4))
        })
        add(2, listOf(5, 6))
        drain()
        assertEquals(listOf(1 to 1, 2 to 5, 1 to 3, 2 to 6, 1 to 4), executed)
        assertEquals(0, queue.pending)
    }

    @Test fun `reentrant enqueue retains FIFO of existing owner`() {
        add(1, listOf(1, 2), action = { executed.add(1 to it); if (it == 1) add(1, listOf(3)) })
        drain()
        assertEquals(listOf(1 to 1, 1 to 2, 1 to 3), executed)
        assertEquals(0, queue.pending)
    }

    @Test fun `callback exception discards rest of job and does not stop other players`() {
        add(1, listOf(1, 2, 3), action = { throw IllegalStateException("fixture") })
        add(2, listOf(4))
        drain()
        assertEquals(1, queue.failures)
        assertEquals(listOf("exception" to listOf(2, 3)), discarded)
        assertEquals(listOf(2 to 4), executed)
        assertEquals(0, queue.pending)
    }

    @Test fun `reload clear during callback releases every job without resurrection`() {
        add(1, listOf(1, 2), action = { executed.add(1 to it); queue.clear("reset") })
        add(2, listOf(3))
        drain()
        assertEquals(listOf(1 to 1), executed)
        assertEquals(0, queue.pending)
        assertEquals(0, queue.players)
        assertEquals(2, queue.discarded)
        add(3, listOf(4)); drain()
        assertEquals(listOf(1 to 1, 3 to 4), executed)
    }

    @Test fun `disabled budget and empty admission cannot perform work`() {
        assertTrue(add(1, emptyList()))
        assertEquals(0, queue.players)
        add(2, listOf(3))
        drain(limit = 0); drain(budget = 0)
        assertTrue(executed.isEmpty())
        assertEquals(1, queue.pending)
    }
}
