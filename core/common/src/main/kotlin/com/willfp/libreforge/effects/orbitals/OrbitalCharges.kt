package com.willfp.libreforge.effects.orbitals

import java.util.IdentityHashMap
import java.util.UUID

/** MAIN-only bounded temporary state. Reservations prevent several hits from spending the same shield. */
internal class OrbitalCharges(private val now: () -> Long) {
    internal class Session(val token: UUID, val expires: Long, val count: Int) {
        val available = (0 until count).toMutableSet()
        val pending = IdentityHashMap<Any, Int>()
        var spent = 0
    }
    internal data class Lease(val player: UUID, val token: UUID, val event: Any, val index: Int)
    private data class Cooldown(val token: UUID, val until: Long, var lastNotice: Long? = null)
    private val active = mutableMapOf<UUID, Session>()
    private val cooldowns = mutableMapOf<UUID, Cooldown>()
    fun start(player: UUID, count: Int, duration: Long, cooldown: Long): Session? {
        if (count !in 1..6 || duration !in 1..30_000 || cooldown !in 1..120_000) return null
        cooldowns.entries.removeIf { it.value.until <= now() }
        if (player in active || player in cooldowns || active.size >= 64 || cooldowns.size >= 2048) return null
        val session = Session(UUID.randomUUID(), now() + duration, count)
        active[player] = session; cooldowns[player] = Cooldown(session.token, now() + cooldown)
        return session
    }
    fun remainingMillis(player: UUID): Long { return ((cooldowns[player]?.until ?: return 0L) - now()).coerceAtLeast(0L) }
    fun claimNotice(player: UUID, enabled: Boolean = true): Boolean {
        if (!enabled) return false
        val cooldown = cooldowns[player] ?: return false
        val time = now()
        if (time >= cooldown.until || cooldown.lastNotice?.let { time - it < 1000L } == true) return false
        cooldown.lastNotice = time
        return true
    }
    fun current(player: UUID) = active[player]
    fun reserve(player: UUID, event: Any): Lease? {
        val state = active[player] ?: return null
        if (state.expires <= now() || state.pending.containsKey(event)) return null
        val index = state.available.firstOrNull() ?: return null
        state.available.remove(index); state.pending[event] = index
        return Lease(player, state.token, event, index)
    }
    /** Returns a broken-shield index only for an acknowledged blocked hit, once. */
    fun finish(lease: Lease, blocked: Boolean): Int? {
        val state = active[lease.player]?.takeIf { it.token == lease.token } ?: return null
        if (state.pending.remove(lease.event) != lease.index) return null
        if (!blocked) { state.available.add(lease.index); return null }
        state.spent++
        if (state.spent == state.count) active.remove(lease.player)
        return lease.index
    }
    fun close(player: UUID) { active.remove(player) } // Slot/quit/reload cannot reset the cooldown.
    fun clear() { active.clear() }
    fun abort(player: UUID, token: UUID) {
        if (active[player]?.token == token) active.remove(player)
        if (cooldowns[player]?.token == token) cooldowns.remove(player)
    }
}
