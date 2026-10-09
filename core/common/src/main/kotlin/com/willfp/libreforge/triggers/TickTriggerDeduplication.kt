package com.willfp.libreforge.triggers

import java.util.UUID

/** Keeps the existing hash-only policy; the scheduler owns the tick boundary. */
internal class TickTriggerDeduplication {
    private val hashes = mutableMapOf<UUID, MutableSet<Int>>()

    fun accept(dispatcher: UUID, hash: Int): Boolean =
        hashes.getOrPut(dispatcher) { mutableSetOf() }.add(hash)

    fun clear() = hashes.clear()
}
