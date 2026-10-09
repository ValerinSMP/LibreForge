package com.willfp.libreforge.effects.templates

import kotlin.test.*

class ActiveModifierListsTest {
    @Test fun `nested multiplier lifecycle releases outer and inner keys without affecting others`() {
        val nested = mutableMapOf<Int, ActiveModifierLists<String, Int>>()
        fun add(player: Int, element: String, occurrence: Int) {
            nested.getOrPut(player) { ActiveModifierLists() }.add(element, occurrence)
        }
        fun disable(player: Int, occurrence: Int) {
            for (element in listOf("skill", "mana")) {
                nested[player]?.removeIf(element) { it == occurrence }
            }
            if (nested[player]?.size == 0) nested.remove(player)
        }
        add(1, "skill", 7); add(1, "mana", 7); add(1, "skill", 8)
        add(2, "skill", 9)
        disable(1, 7)
        assertEquals(listOf(8), nested[1]?.get("skill"))
        assertTrue(nested[1]!!["mana"].isEmpty())
        assertEquals(1, nested[1]?.size)
        disable(1, 8); disable(1, 8)
        assertFalse(nested.containsKey(1))
        assertEquals(listOf(9), nested[2]?.get("skill"))
        repeat(10000) { add(3, "skill", it); disable(3, it) }
        assertEquals(setOf(2), nested.keys)
    }
    @Test fun `single removal and queries match original lists across random traces`() {
        val store = ActiveModifierLists<Int, Int>()
        val original = mutableMapOf<Int, MutableList<Int>>()
        val random = kotlin.random.Random(20261005)
        repeat(10000) {
            val key = random.nextInt(10)
            val value = random.nextInt(5)
            if (random.nextBoolean()) {
                original.getOrPut(key) { mutableListOf() }.add(value)
                store.add(key, value)
            } else {
                original.getOrPut(key) { mutableListOf() }.remove(value)
                store.remove(key, value)
            }
            for (candidate in 0..10) assertEquals(original[candidate] ?: emptyList(), store[candidate])
            assertEquals(original.values.count { it.isNotEmpty() }, store.size)
        }
    }
    @Test fun `single removal retains second identical occurrence until last disable`() {
        val store = ActiveModifierLists<Int, Int>()
        store.add(1, 7); store.add(1, 7)
        store.remove(1, 7)
        assertEquals(listOf(7), store[1])
        store.remove(1, 8)
        assertEquals(listOf(7), store[1])
        store.remove(1, 7)
        store.remove(1, 7)
        assertEquals(0, store.size)
    }
    @Test fun `reads and repeated removals do not retain keys`() {
        val store = ActiveModifierLists<Int, Int>()
        repeat(10000) { assertTrue(store[it].isEmpty()); store.removeIf(it) { true } }
        assertEquals(0, store.size)
    }
    @Test fun `order duplicates partial removal and last removal are preserved`() {
        val store = ActiveModifierLists<Int, Int>()
        listOf(1, 2, 1, 3).forEach { store.add(0, it) }
        store.add(1, 9)
        assertEquals(listOf(1, 2, 1, 3), store[0])
        store.removeIf(0) { it == 1 }
        assertEquals(listOf(2, 3), store[0])
        store.removeIf(0) { true }
        assertEquals(1, store.size)
        assertEquals(listOf(9), store[1])
    }
    @Test fun `10000 activation cycles release all keys`() {
        val store = ActiveModifierLists<Int, Int>()
        repeat(10000) { store.add(it, it); store.removeIf(it) { value -> value == it } }
        assertEquals(0, store.size)
    }
}
