package com.willfp.libreforge.slot

/** Scratch and destination belong to one scan; neither is a persistent item cache. */
internal inline fun <I, H, R> appendMatchingHolders(
    items: Iterable<I>,
    destination: MutableList<R>,
    accepted: MutableList<H>,
    isBroken: (I) -> Boolean,
    find: (I) -> List<H>,
    isValid: (H) -> Boolean,
    provide: (H, I) -> R
) {
    for (item in items) {
        if (isBroken(item)) continue
        accepted.clear()
        for (holder in find(item)) {
            if (isValid(holder)) accepted.add(holder)
        }
        // Keep all validations before construction, as in the original filter/map pipeline.
        for (holder in accepted) destination.add(provide(holder, item))
    }
}
