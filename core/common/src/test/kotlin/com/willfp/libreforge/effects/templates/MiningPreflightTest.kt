package com.willfp.libreforge.effects.templates

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class MiningPreflightTest {
    @Test fun `unrelated chains do not inspect mining target`() {
        assertTrue(miningPreflight(false) { error("unrelated effect checked") })
    }

    @Test fun `rejected target cannot run feedback or consume mana`() {
        var mana = 100
        var feedback = 0
        if (miningPreflight(true) { false }) {
            mana -= 35
            feedback++
        }
        assertEquals(100, mana)
        assertEquals(0, feedback)
    }

    @Test fun `valid target runs synchronously without scheduling`() {
        val calls = mutableListOf<String>()
        if (miningPreflight(true) { calls.add("validate"); true }) {
            calls.add("mine")
            calls.add("sound")
        }
        assertEquals(listOf("validate", "mine", "sound"), calls)
    }

    @Test fun `remote and invalid reach are rejected in preflight`() {
        assertFalse(miningPreflight(true) { isMiningAnchorInReach(0.5, 65.6, 0.5, 80, 65, 0, 4.5) })
        assertTrue(miningPreflight(true) { isMiningAnchorInReach(0.5, 65.6, 0.5, 3, 65, 0, 4.5) })
    }
}
