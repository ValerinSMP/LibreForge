package com.willfp.libreforge.effects.arguments.impl

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Bounded maintenance, independent of login/logout; active deadlines are never evicted. */
internal class ExpiringCooldowns {
    private data class Key(val group: String, val dispatcher: UUID)
    private val deadlines = ConcurrentHashMap<Key, Long>()
    private var cursor: Iterator<Map.Entry<Key, Long>>? = null
    val size: Int get() = deadlines.size
    fun get(group: String, dispatcher: UUID): Long? = deadlines[Key(group, dispatcher)]
    fun put(group: String, dispatcher: UUID, deadline: Long) {
        deadlines[Key(group, dispatcher)] = deadline
    }
    fun sweep(now: Long, budget: Int): Int {
        require(budget > 0)
        val iterator = cursor ?: deadlines.entries.iterator().also { cursor = it }
        var visited = 0
        while (visited < budget && iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value <= now) deadlines.remove(entry.key, entry.value)
            visited++
        }
        if (!iterator.hasNext()) cursor = null
        return visited
    }
}
