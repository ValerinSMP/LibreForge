package com.willfp.libreforge.effects.impl

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.length
import kotlin.math.abs
import kotlin.math.acos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EffectHomingMathTest {
    private fun rotate(from: Float3, to: Float3, limit: Float): Float3? {
        val method = EffectHoming::class.java.getDeclaredMethod("rotateTowards", Float3::class.java, Float3::class.java, Float::class.javaPrimitiveType)
        method.isAccessible = true
        return method.invoke(EffectHoming, from, to, limit) as Float3?
    }
    private fun intercept(speed: Float, position: Float3, velocity: Float3): Float3 {
        val method = EffectHoming::class.java.getDeclaredMethod("predictIntercept", Float3::class.java, Float::class.javaPrimitiveType, Float3::class.java, Float3::class.java)
        method.isAccessible = true
        return method.invoke(EffectHoming, Float3(), speed, position, velocity) as Float3
    }
    @Test fun turnIsLimitedAndSpeedPreserved() {
        val result = rotate(Float3(1f,0f,0f), Float3(0f,0f,1f), Math.toRadians(15.0).toFloat())!!
        assertTrue(abs(acos(result.x) - Math.toRadians(15.0)) < 0.0001)
        assertTrue(abs(length(result) - 1f) < 0.0001)
    }
    @Test fun nearAlignedTargetIsReached() {
        assertEquals(Float3(1f,0f,0f), rotate(Float3(1f,0f,0f), Float3(1f,0f,0f), 0.2f))
    }
    @Test fun oppositeTargetDoesNotCauseUndefinedTurn() {
        assertNull(rotate(Float3(1f,0f,0f), Float3(-1f,0f,0f), 0.2f))
    }
    @Test fun stationaryTargetNeedsNoLeading() {
        assertEquals(Float3(10f,0f,0f), intercept(3f, Float3(10f,0f,0f), Float3()))
    }
    @Test fun movingTargetIsLedToIntercept() {
        val result = intercept(3f, Float3(10f,0f,0f), Float3(0f,0f,1f))
        val time = result.z
        assertTrue(time > 0f)
        assertTrue(abs(length(result) - 3f * time) < 0.001)
    }
    @Test fun fasterEscapingTargetFallsBackToCurrentPosition() {
        assertEquals(Float3(10f,0f,0f), intercept(2f, Float3(10f,0f,0f), Float3(3f,0f,0f)))
    }
    @Test fun predictionIsCappedAtTwentyTicks() {
        assertEquals(20f, intercept(3f, Float3(1000f,0f,0f), Float3(0f,0f,1f)).z)
    }
    @Test fun equalSpeedApproachingTargetUsesLinearSolution() {
        assertEquals(5f, intercept(2f, Float3(10f,0f,0f), Float3(-2f,0f,0f)).x)
    }
}
