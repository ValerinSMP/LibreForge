package com.willfp.libreforge

import kotlin.test.Test
import kotlin.test.assertEquals

/** Regression coverage of the existing multiplicity-preserving refresh subtraction. */
class RefreshMultiplicityTest {
    @Test fun `removing one repeated effect preserves other occurrences`() {
        assertEquals(listOf("armor", "talisman"), listOf("armor", "armor", "talisman") without listOf("armor"))
    }
    @Test fun `unchanged effects produce no additions or removals`() {
        val effects = listOf("armor", "armor", "talisman")
        assertEquals(emptyList<String>(), effects without effects)
    }
    @Test fun `subtraction preserves configured ordering`() {
        assertEquals(listOf("a", "c"), listOf("a", "b", "c", "b") without listOf("b", "b"))
    }
    @Test fun `missing entries do not remove different effects`() {
        assertEquals(listOf("a", "a"), listOf("a", "a") without listOf("b"))
    }
}
