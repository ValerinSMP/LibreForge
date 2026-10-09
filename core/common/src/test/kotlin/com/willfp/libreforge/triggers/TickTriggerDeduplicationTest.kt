package com.willfp.libreforge.triggers

import java.util.UUID
import kotlin.random.Random
import kotlin.test.*

class TickTriggerDeduplicationTest {
    private val a = UUID(0, 1)
    private val b = UUID(0, 2)

    @Test fun `first hash accepted duplicates rejected for same dispatcher`() {
        val seen = TickTriggerDeduplication()
        assertTrue(seen.accept(a, 12))
        assertFalse(seen.accept(a, 12))
        assertTrue(seen.accept(a, 13))
    }

    @Test fun `dispatchers remain independent`() {
        val seen = TickTriggerDeduplication()
        assertTrue(seen.accept(a, 12))
        assertTrue(seen.accept(b, 12))
        assertFalse(seen.accept(b, 12))
    }

    @Test fun `clear allows same hashes next tick for all dispatchers`() {
        val seen = TickTriggerDeduplication()
        seen.accept(a, 12)
        seen.accept(b, 13)
        seen.clear()
        assertTrue(seen.accept(a, 12))
        assertTrue(seen.accept(b, 13))
        seen.clear()
        seen.clear()
        assertTrue(seen.accept(a, 12))
    }

    @Test fun `integer extremes and negative hashes preserve policy`() {
        val seen = TickTriggerDeduplication()
        for (hash in listOf(Int.MIN_VALUE, Int.MAX_VALUE, -1, 0, 1)) {
            assertTrue(seen.accept(a, hash))
            assertFalse(seen.accept(a, hash))
        }
    }

    @Test fun `different integer keys with equal hash buckets remain distinct`() {
        val seen = TickTriggerDeduplication()
        for (hash in 0..1000) assertTrue(seen.accept(a, hash * 16))
        for (hash in 0..1000) assertFalse(seen.accept(a, hash * 16))
    }

    @Test fun `colliding trigger hashes still rejected without changing mechanics`() {
        val seen = TickTriggerDeduplication()
        val first = (1 shl 5) xor 0
        val second = (2 shl 5) xor 96
        assertEquals(first, second)
        assertTrue(seen.accept(a, first))
        assertFalse(seen.accept(a, second))
    }

    @Test fun `randomized tick traces match original list implementation`() {
        val seen = TickTriggerDeduplication()
        val old = mutableMapOf<UUID, MutableList<Int>>()
        val random = Random(20261005)
        val dispatchers = List(8) { UUID(0, it.toLong()) }
        repeat(10000) {
            if (random.nextInt(20) == 0) {
                seen.clear()
                old.clear()
            } else {
                val dispatcher = dispatchers.random(random)
                val hash = random.nextInt(-30, 30)
                val list = old.getOrPut(dispatcher) { mutableListOf() }
                val accepted = hash !in list
                if (accepted) list.add(hash)
                assertEquals(accepted, seen.accept(dispatcher, hash))
            }
        }
    }
}
