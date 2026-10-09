package com.willfp.libreforge.effects.templates

/** Reads never create entries; the last removal also releases the key. */
internal class ActiveModifierLists<K, V> {
    private val entries = mutableMapOf<K, MutableList<V>>()
    val size: Int get() = entries.size
    operator fun get(key: K): List<V> = entries[key] ?: emptyList()
    fun add(key: K, value: V) { entries.getOrPut(key) { mutableListOf() }.add(value) }
    fun remove(key: K, value: V) {
        val values = entries[key] ?: return
        values.remove(value)
        if (values.isEmpty()) entries.remove(key)
    }
    fun removeIf(key: K, predicate: (V) -> Boolean) {
        val values = entries[key] ?: return
        values.removeAll(predicate)
        if (values.isEmpty()) entries.remove(key)
    }
}
