package com.willfp.libreforge.effects.impl

internal data class PassivePotionSelection(val level: Int, val particles: Boolean, val icon: Boolean)

/** Match filter/max/any semantics for immutable registered potion data in one pass. */
internal inline fun <T> selectPassivePotion(
    values: Iterable<T>,
    matches: (T) -> Boolean,
    level: (T) -> Int,
    particles: (T) -> Boolean,
    icon: (T) -> Boolean
): PassivePotionSelection? {
    var bestLevel: Int? = null
    var showParticles = false
    var showIcon = false
    for (value in values) {
        if (!matches(value)) continue
        val candidate = level(value)
        if (bestLevel == null || candidate > bestLevel) bestLevel = candidate
        showParticles = showParticles || particles(value)
        showIcon = showIcon || icon(value)
    }
    return bestLevel?.let { PassivePotionSelection(it, showParticles, showIcon) }
}
