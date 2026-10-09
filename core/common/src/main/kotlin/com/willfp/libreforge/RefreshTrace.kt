package com.willfp.libreforge

/** A bounded, per-refresh diagnostic snapshot; never a persistent player cache. */
internal class RefreshTrace(private val clock: () -> Long = System::nanoTime) {
    private val durations = mutableMapOf<String, Long>()
    private val phases = mutableMapOf<String, Long>()
    private var holderRebuilds = 0

    fun holderRebuilt() { holderRebuilds++ }

    fun <T> phase(key: String, action: () -> T): T {
        val started = clock()
        try {
            return action()
        } finally {
            if (phases.containsKey(key) || phases.size < 16) {
                phases[key] = (phases[key] ?: 0) + (clock() - started).coerceAtLeast(0)
            }
        }
    }

    fun phaseSummary(): String = phases.entries.joinToString(", ") { "${it.key}=${it.value / 1000}us" }
    fun rebuildCount(): Int = holderRebuilds

    fun <T> measure(key: String, action: () -> T): T {
        val started = clock()
        try {
            return action()
        } finally {
            if (durations.containsKey(key) || durations.size < 64) {
                durations[key] = (durations[key] ?: 0) + (clock() - started).coerceAtLeast(0)
            }
        }
    }

    fun summary(): String = durations.entries.sortedByDescending { it.value }.take(5)
        .joinToString(", ") { "${it.key}=${it.value / 1000}us" }
}

internal object RefreshTracing {
    private val current = ThreadLocal<RefreshTrace>()

    fun begin(enabled: Boolean): RefreshTrace? {
        val previous = current.get()
        if (enabled) current.set(RefreshTrace()) else current.remove()
        return previous
    }

    fun restore(previous: RefreshTrace?) {
        if (previous == null) current.remove() else current.set(previous)
    }

    fun summary() = current.get()?.summary().orEmpty()
    fun phaseSummary() = current.get()?.phaseSummary().orEmpty()
    fun rebuildCount() = current.get()?.rebuildCount() ?: 0
    fun holderRebuilt() { current.get()?.holderRebuilt() }

    fun <T> scoped(enabled: Boolean, action: () -> T): T {
        val previous = begin(enabled)
        try {
            return action()
        } finally {
            restore(previous)
        }
    }

    fun <T> phase(key: String, action: () -> T): T {
        val trace = current.get() ?: return action()
        return trace.phase(key, action)
    }

    fun <T> measure(key: () -> String, action: () -> T): T {
        val trace = current.get() ?: return action()
        return trace.measure(key(), action)
    }
}
