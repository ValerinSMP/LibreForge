package com.willfp.libreforge.effects.impl

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DamageMultiplierBypassTest {
    @Test
    fun `damage origins default safely and accept declared values`() {
        assertEquals(DamageOrigin.NORMAL, damageOrigin(null))
        assertEquals(DamageOrigin.NORMAL, damageOrigin("unknown"))
        assertEquals(DamageOrigin.PASSIVE, damageOrigin("passive"))
        assertEquals(DamageOrigin.ACTIVE, damageOrigin("ACTIVE"))
        assertEquals(DamageOrigin.ACTIVE, damageOrigin(null, ability = true))
        assertEquals(DamageOrigin.ACTIVE, damageOrigin(null, damageMode = "ability_only"))
    }

    @Test
    fun `source player id is present only when use source is enabled`() {
        val playerId = UUID.randomUUID()

        assertNull(DamageMultiplierBypass.sourcePlayerId(false, playerId))
        assertEquals(playerId, DamageMultiplierBypass.sourcePlayerId(true, playerId))
        assertNull(DamageMultiplierBypass.sourcePlayerId(true, null))
    }

    @Test
    fun `legacy run does not enable the damage bypass`() {
        DamageMultiplierBypass.run {
            assertFalse(DamageMultiplierBypass.isActive)
            assertEquals(DamageProvenance(DamageOrigin.NORMAL, null), DamageMultiplierBypass.current)
        }
    }

    @Test
    fun `nested provenance does not inherit and restores the outer call`() {
        val outer = DamageProvenance(DamageOrigin.ACTIVE, UUID.randomUUID())
        val inner = DamageProvenance(DamageOrigin.PASSIVE, null)

        DamageMultiplierBypass.withProvenance(outer) {
            assertEquals(outer, DamageMultiplierBypass.current)

            DamageMultiplierBypass.withProvenance(inner) {
                assertEquals(inner, DamageMultiplierBypass.current)
                assertNull(DamageMultiplierBypass.current?.sourcePlayerId)
            }

            assertEquals(outer, DamageMultiplierBypass.current)
        }

        assertNull(DamageMultiplierBypass.current)
    }

    @Test
    fun `failed nested provenance restores the outer call`() {
        val outer = DamageProvenance(DamageOrigin.NORMAL, UUID.randomUUID())

        DamageMultiplierBypass.withProvenance(outer) {
            assertFailsWith<IllegalStateException> {
                DamageMultiplierBypass.withProvenance(DamageProvenance(DamageOrigin.ACTIVE, null)) {
                    error("expected")
                }
            }

            assertEquals(outer, DamageMultiplierBypass.current)
        }

        assertNull(DamageMultiplierBypass.current)
    }
}
