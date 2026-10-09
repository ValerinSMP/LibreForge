package com.willfp.libreforge.effects.impl

import kotlin.random.Random
import kotlin.test.*

class PassivePotionSelectionTest {
    private data class Potion(val type: Int, val level: Int, val particles: Boolean, val icon: Boolean)
    private fun select(values: List<Potion>, type: Int) = selectPassivePotion(values,
        { it.type == type }, { it.level }, { it.particles }, { it.icon })

    @Test fun `empty and other types return null`() {
        assertNull(select(emptyList(), 1))
        assertNull(select(listOf(Potion(2, 10, true, true)), 1))
    }

    @Test fun `highest level and independent flags include weaker holders`() {
        assertEquals(PassivePotionSelection(3, true, true), select(listOf(
            Potion(1, 3, false, false), Potion(1, 1, true, false), Potion(1, 2, false, true)), 1))
    }

    @Test fun `ties negatives and integer minimum preserve max semantics`() {
        assertEquals(PassivePotionSelection(-1, false, false), select(listOf(
            Potion(1, Int.MIN_VALUE, false, false), Potion(1, -1, false, false),
            Potion(1, -1, false, false)), 1))
        assertEquals(PassivePotionSelection(Int.MIN_VALUE, false, false),
            select(listOf(Potion(1, Int.MIN_VALUE, false, false)), 1))
    }

    @Test fun `500 random selections match filter max and any without mutating inputs`() {
        val random = Random(20261005)
        repeat(500) {
            val input = List(random.nextInt(50)) {
                Potion(random.nextInt(4), random.nextInt(-10, 20), random.nextBoolean(), random.nextBoolean())
            }
            val snapshot = input.toList()
            val active = input.filter { it.type == 1 }
            val expected = active.maxByOrNull { it.level }?.let {
                PassivePotionSelection(it.level, active.any { it.particles }, active.any { it.icon })
            }
            assertEquals(expected, select(input, 1))
            assertEquals(snapshot, input)
        }
    }
}
