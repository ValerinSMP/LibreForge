package com.willfp.libreforge

import kotlin.random.Random
import kotlin.test.*

class OccurrenceDifferenceTest {
    private data class Block(val id: String, val weight: Int, val occurrence: Int = 0) {
        // Force collisions to cover equality rather than hash-only matching.
        override fun hashCode() = 1
    }
    private val comparator = compareBy<Block> { it.weight }

    @Test fun `stable sorting preserves order of equal weights and physical occurrences`() {
        val a = Block("armor", 2, 0)
        val b = Block("talisman", 2)
        val c = Block("armor", 2, 1)
        val low = Block("skill", 1)
        assertEquals(listOf(low, a, b, c),
            orderedOccurrenceDifference(listOf(a, b, low, c), emptyList(), comparator))
    }

    @Test fun `subtraction removes first matching occurrences only`() {
        val first = Block("armor", 2)
        val equalButDistinct = first.copy()
        val result = orderedOccurrenceDifference(listOf(first, equalButDistinct), listOf(first), comparator)
        assertEquals(1, result.size)
        assertSame(equalButDistinct, result.single())
    }

    @Test fun `added removed and reloaded match original refresh pipeline`() {
        val a = Block("armor", 2)
        val b = Block("armor", 2, 1)
        val c = Block("skill", 1)
        val before = listOf(c, a, b)
        val after = listOf(c, b, Block("talisman", 3))
        val added = orderedOccurrenceDifference(after, before, comparator)
        val removed = orderedOccurrenceDifference(before, after, comparator)
        val reload = orderedOccurrenceDifference(after, added, comparator)
        assertEquals((after without before).sortedWith(comparator), added)
        assertEquals((before without after).sortedWith(comparator), removed)
        assertEquals((after without added).sortedWith(comparator), reload)
    }

    @Test fun `unchanged input retains every reload occurrence`() {
        val blocks = listOf(Block("armor", 1), Block("armor", 1), Block("skill", 2))
        val added = orderedOccurrenceDifference(blocks, blocks, comparator)
        assertTrue(added.isEmpty())
        assertEquals(blocks, orderedOccurrenceDifference(blocks, added, comparator))
    }

    @Test fun `empty inputs and unrelated removals match original`() {
        val a = Block("a", 1)
        assertTrue(orderedOccurrenceDifference(emptyList(), listOf(a), comparator).isEmpty())
        assertTrue(orderedOccurrenceDifference(emptyList(), emptyList(), comparator).isEmpty())
        assertEquals(listOf(a), orderedOccurrenceDifference(listOf(a), listOf(Block("b", 1)), comparator))
    }

    @Test fun `does not mutate either input and retains object identity`() {
        val a = Block("a", 2)
        val b = Block("b", 1)
        val source = mutableListOf(a, b)
        val other = mutableListOf(Block("missing", 0))
        val result = orderedOccurrenceDifference(source, other, comparator)
        assertEquals(listOf(a, b), source)
        assertEquals(listOf(Block("missing", 0)), other)
        assertSame(b, result[0])
        assertSame(a, result[1])
    }

    @Test fun `randomized transitions preserve multiplicity and all callback lists`() {
        val random = Random(20261005)
        val pool = List(12) { Block("holder${it % 3}", it % 4, it / 3) }
        repeat(500) {
            val before = List(random.nextInt(35)) { pool.random(random) }
            val after = List(random.nextInt(35)) { pool.random(random) }
            val added = orderedOccurrenceDifference(after, before, comparator)
            val removed = orderedOccurrenceDifference(before, after, comparator)
            val reload = orderedOccurrenceDifference(after, added, comparator)
            assertEquals((after without before).sortedWith(comparator), added)
            assertEquals((before without after).sortedWith(comparator), removed)
            assertEquals((after without added).sortedWith(comparator), reload)
        }
    }
}
