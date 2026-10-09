package com.willfp.libreforge.conditions

import kotlin.random.Random
import kotlin.test.*

class FailedConditionsTest {
    @Test fun `success and empty inputs never call failure callbacks`() {
        assertTrue(evaluateConditionsAndTriggerFailures(emptyList<Int>(), { error("empty") }, { error("empty") }))
        val checked = mutableListOf<Int>()
        assertTrue(evaluateConditionsAndTriggerFailures(listOf(1, 2), { checked.add(it); true }, { error("success") }))
        assertEquals(listOf(1, 2), checked)
    }

    @Test fun `all evaluations precede callbacks and duplicate failures are retained`() {
        val trace = mutableListOf<String>()
        assertFalse(evaluateConditionsAndTriggerFailures(listOf(1, 2, 1),
            { trace.add("check$it"); it == 2 }, { trace.add("fail$it") }))
        assertEquals(listOf("check1", "check2", "check1", "fail1", "fail1"), trace)
    }

    @Test fun `evaluation exception prevents all callbacks`() {
        val failures = mutableListOf<Int>()
        assertFailsWith<IllegalStateException> {
            evaluateConditionsAndTriggerFailures(listOf(1, 2),
                { if (it == 2) error("stop"); false }, { failures.add(it) })
        }
        assertTrue(failures.isEmpty())
    }

    @Test fun `callback exception stops subsequent callbacks`() {
        val trace = mutableListOf<Int>()
        assertFailsWith<IllegalStateException> {
            evaluateConditionsAndTriggerFailures(listOf(1, 2), { trace.add(it); false }, { error("stop") })
        }
        assertEquals(listOf(1, 2), trace)
    }

    @Test fun `500 random cases match original two phase evaluation`() {
        val random = Random(20261005)
        repeat(500) {
            val input = List(random.nextInt(40)) { random.nextInt(6) }
            val expectedTrace = mutableListOf<String>()
            val failed = input.filterNot { expectedTrace.add("check$it"); it % 2 == 0 }
            failed.forEach { expectedTrace.add("fail$it") }
            val actualTrace = mutableListOf<String>()
            assertEquals(failed.isEmpty(), evaluateConditionsAndTriggerFailures(input,
                { actualTrace.add("check$it"); it % 2 == 0 }, { actualTrace.add("fail$it") }))
            assertEquals(expectedTrace, actualTrace)
        }
    }
}
