package com.willfp.libreforge.effects.templates

import java.util.ArrayDeque

/** Main-thread only. One item per owner per turn; callbacks may enqueue/cancel safely. */
internal class FairMiningQueue<K : Any, T : Any>(private val clock: () -> Long = System::nanoTime) {
    private class Job<T>(val items: ArrayDeque<T>, val tick: Long, val valid: () -> Boolean,
                         val action: (T) -> Unit, val discard: (List<T>, String) -> Unit)
    private class Owner<T>(val jobs: ArrayDeque<Job<T>> = ArrayDeque(), var pending: Int = 0)
    private val owners = LinkedHashMap<K, Owner<T>>()
    private val turns = ArrayDeque<K>()
    var pending: Int = 0
        private set
    val players: Int get() = owners.size
    var rejected: Long = 0
        private set
    var discarded: Long = 0
        private set
    var processed: Long = 0
        private set
    var failures: Long = 0
        private set

    fun reject() { rejected++ }

    fun canAccept(owner: K, count: Int, totalLimit: Int, playerLimit: Int): Boolean =
        count >= 0 && count <= totalLimit - pending && count <= playerLimit - (owners[owner]?.pending ?: 0)

    fun enqueue(owner: K, items: List<T>, tick: Long, totalLimit: Int, playerLimit: Int,
                valid: () -> Boolean, action: (T) -> Unit, discard: (List<T>, String) -> Unit): Boolean {
        if (!canAccept(owner, items.size, totalLimit, playerLimit)) { reject(); return false }
        if (items.isEmpty()) return true
        val state = owners[owner] ?: Owner<T>().also { owners[owner] = it; turns.addLast(owner) }
        state.jobs.addLast(Job(ArrayDeque(items), tick, valid, action, discard))
        state.pending += items.size
        pending += items.size
        return true
    }

    fun cancel(owner: K, reason: String = "cancelled") {
        val state = owners.remove(owner) ?: return
        turns.remove(owner)
        while (state.jobs.isNotEmpty()) discard(state, state.jobs.removeFirst(), reason)
    }

    fun clear(reason: String = "reset") { owners.keys.toList().forEach { cancel(it, reason) } }

    private fun discard(state: Owner<T>, job: Job<T>, reason: String) {
        val remaining = job.items.toList()
        job.items.clear()
        state.pending -= remaining.size
        pending -= remaining.size
        discarded += remaining.size
        job.discard(remaining, reason)
    }

    /** Cooperative budget: an individual callback can exceed it; never interrupt block events. */
    fun drain(tick: Long, maxItems: Int, budgetNanos: Long, maxAgeTicks: Long,
              onFailure: (Exception) -> Unit = {}) {
        if (maxItems <= 0 || budgetNanos <= 0) return
        val started = clock()
        var attempts = 0
        while (turns.isNotEmpty() && attempts < maxItems && clock() - started < budgetNanos) {
            val owner = turns.removeFirst()
            val state = owners[owner] ?: continue
            val job = state.jobs.first()
            try {
                if (tick - job.tick >= maxAgeTicks || !job.valid()) {
                    state.jobs.removeFirst()
                    discard(state, job, if (tick - job.tick >= maxAgeTicks) "expired" else "invalid")
                } else {
                    val item = job.items.removeFirst()
                    state.pending--; pending--; attempts++; processed++
                    job.action(item)
                    if (owners[owner] === state && job.items.isEmpty()) state.jobs.removeFirst()
                }
            } catch (exception: Exception) {
                failures++
                onFailure(exception)
                if (owners[owner] === state && state.jobs.peekFirst() === job) {
                    state.jobs.removeFirst()
                    discard(state, job, "exception")
                }
            }
            // An event callback may have cancelled this owner or replaced it with a new session.
            if (owners[owner] === state) {
                if (state.jobs.isEmpty()) owners.remove(owner) else turns.addLast(owner)
            }
        }
    }
}
