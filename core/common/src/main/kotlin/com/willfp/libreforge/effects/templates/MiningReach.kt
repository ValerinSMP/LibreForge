package com.willfp.libreforge.effects.templates

/** Distance to the block cube, not its centre: face/edge clicks retain vanilla reach. */
internal fun isMiningAnchorInReach(
    eyeX: Double, eyeY: Double, eyeZ: Double,
    blockX: Int, blockY: Int, blockZ: Int,
    reach: Double
): Boolean {
    if (!eyeX.isFinite() || !eyeY.isFinite() || !eyeZ.isFinite() || !reach.isFinite() || reach < 0) return false
    fun distance(value: Double, min: Int): Double =
        value - value.coerceIn(min.toDouble(), min.toDouble() + 1.0)
    val x = distance(eyeX, blockX)
    val y = distance(eyeY, blockY)
    val z = distance(eyeZ, blockZ)
    return x * x + y * y + z * z <= reach * reach
}
