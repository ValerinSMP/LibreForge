package com.willfp.libreforge.integrations.huskintegration.husksync

/** Recompute the equipment first; old passive registrations may no longer be valid. */
internal fun refreshAfterSync(
    schedule: (() -> Unit) -> Unit,
    isOnline: () -> Boolean,
    refreshHolders: () -> Unit,
    restorePotions: () -> Unit
) {
    schedule {
        if (isOnline()) {
            refreshHolders()
            restorePotions()
        }
    }
}

/** Never replace potions restored by another plugin, including stronger potions. */
internal fun <T> restoreMissingEffects(
    types: Collection<T>,
    hasEffect: (T) -> Boolean,
    restore: (T) -> Unit
) {
    for (type in types.toSet()) {
        if (!hasEffect(type)) restore(type)
    }
}
