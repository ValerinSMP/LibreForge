package com.willfp.libreforge.integrations.huskintegration.husksync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostSyncEffectsTest {
    @Test fun `restore runs after deferred equipment refresh`() {
        val scheduled = mutableListOf<() -> Unit>()
        val calls = mutableListOf<String>()
        refreshAfterSync({ scheduled.add(it) }, { true }, { calls.add("holders") }, { calls.add("potions") })
        assertTrue(calls.isEmpty())
        scheduled.single()()
        assertEquals(listOf("holders", "potions"), calls)
    }

    @Test fun `disconnect before deferred callback does not mutate player`() {
        var task: (() -> Unit)? = null
        var online = true
        refreshAfterSync({ task = it }, { online }, { error("offline refresh") }, { error("offline restore") })
        online = false
        task!!()
    }

    @Test fun `erased resistance returns without reequip`() {
        val active = mutableSetOf<String>() // HuskSync restored a snapshot without the passive potion.
        restoreMissingEffects(listOf("resistance"), { it in active }, { active.add(it) })
        assertEquals(setOf("resistance"), active)
    }

    @Test fun `existing potions are never replaced`() {
        restoreMissingEffects(listOf("resistance"), { true }, { error("existing potion overwritten") })
    }

    @Test fun `duplicate holders and duplicate sync do not reapply twice`() {
        val active = mutableSetOf<String>()
        var applied = 0
        repeat(2) {
            restoreMissingEffects(listOf("resistance", "resistance"), { it in active }, {
                applied++
                active.add(it)
            })
        }
        assertEquals(1, applied)
    }

    @Test fun `equipment removed by snapshot is not restored from stale registrations`() {
        val registered = mutableListOf("resistance")
        refreshAfterSync({ it() }, { true }, { registered.clear() }, {
            restoreMissingEffects(registered, { false }, { error("removed equipment restored") })
        })
    }

    @Test fun `empty registrations leave unrelated potions intact`() {
        val active = mutableSetOf("speed")
        restoreMissingEffects(emptyList<String>(), { it in active }, { active.add(it) })
        assertEquals(setOf("speed"), active)
    }
}
