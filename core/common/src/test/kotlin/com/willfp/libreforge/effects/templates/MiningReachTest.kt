package com.willfp.libreforge.effects.templates

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiningReachTest {
    @Test fun `remote anchor at 80 or 300 blocks is rejected`() {
        for (distance in listOf(80, 300)) {
            assertFalse(isMiningAnchorInReach(0.5, 65.6, 0.5, distance, 65, 0, 4.5))
        }
    }

    @Test fun `nearby anchor keeps radius and vein expansion eligible`() {
        assertTrue(isMiningAnchorInReach(0.5, 65.6, 0.5, 3, 65, 0, 4.5))
    }

    @Test fun `block face at exact reach is accepted even if centre is further`() {
        assertTrue(isMiningAnchorInReach(0.5, 65.6, 0.5, 5, 65, 0, 4.5))
        assertFalse(isMiningAnchorInReach(0.499, 65.6, 0.5, 5, 65, 0, 4.5))
    }

    @Test fun `negative coordinates and diagonal distance are correct`() {
        assertTrue(isMiningAnchorInReach(-0.5, 65.6, -0.5, -5, 65, -1, 4.5))
        assertFalse(isMiningAnchorInReach(0.5, 65.6, 0.5, 4, 65, 4, 4.5))
    }

    @Test fun `actual attribute reach is respected`() {
        assertFalse(isMiningAnchorInReach(0.5, 65.6, 0.5, 8, 65, 0, 4.5))
        assertTrue(isMiningAnchorInReach(0.5, 65.6, 0.5, 8, 65, 0, 8.0))
    }

    @Test fun `invalid distances fail closed`() {
        for (reach in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0)) {
            assertFalse(isMiningAnchorInReach(0.5, 65.6, 0.5, 1, 65, 0, reach))
        }
        assertFalse(isMiningAnchorInReach(Double.NaN, 65.6, 0.5, 1, 65, 0, 4.5))
    }
}
