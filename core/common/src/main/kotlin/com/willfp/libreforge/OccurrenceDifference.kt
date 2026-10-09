package com.willfp.libreforge

/** Subtract occurrences in source order, then stably sort the same output buffer. */
internal fun <T> orderedOccurrenceDifference(
    source: Collection<T>,
    other: Collection<T>,
    comparator: Comparator<in T>
): List<T> {
    if (source.isEmpty()) return emptyList()
    val result = ArrayList<T>()
    if (other.isEmpty()) {
        result.addAll(source)
    } else {
        val counts = HashMap<T, Int>(other.size)
        for (element in other) counts[element] = (counts[element] ?: 0) + 1
        for (element in source) {
            val count = counts[element]
            if (count != null && count > 0) counts[element] = count - 1
            else result.add(element)
        }
    }
    result.sortWith(comparator)
    return result
}
