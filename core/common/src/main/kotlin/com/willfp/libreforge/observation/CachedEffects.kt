package com.willfp.libreforge.observation

import com.willfp.libreforge.HolderStates
import org.bukkit.Bukkit
import java.util.Collections
import java.util.UUID

/** Pure copied description of published readiness, never proof that an impact passed its filters. */
class CachedEffectEntry internal constructor(
    val sourceNamespace: String, val sourceId: String, val effectId: String,
    triggers: List<String>, worlds: List<String>, val literalMultiplier: Double?, val hasEventRestrictions: Boolean
) {
    val triggers: List<String> = Collections.unmodifiableList(ArrayList(triggers))
    val worlds: List<String> = Collections.unmodifiableList(ArrayList(worlds))
}
class CachedEffectsSnapshot internal constructor(
    val available: Boolean, val pending: Boolean, val truncated: Boolean,
    entries: List<CachedEffectEntry>
) {
    val entries: List<CachedEffectEntry> = Collections.unmodifiableList(ArrayList(entries))
}
object CachedEffects {
    /** MAIN only, cache-only; no profile I/O, expressions, conditions, provider calls or dispatch. */
    @JvmStatic fun getCachedObservation(uuid: UUID): CachedEffectsSnapshot {
        check(Bukkit.isPrimaryThread()) { "Effect observation requires the main thread" }
        val published = HolderStates.peekPublishedEffects(uuid)
            ?: return CachedEffectsSnapshot(false, false, false, emptyList())
        val entries = ArrayList<CachedEffectEntry>()
        var visited = 0
        var truncated = published.first.size > 1024
        outer@ for (provided in published.first.take(1024)) {
            for (element in provided.effect.effects) {
                if (++visited > 1024 || entries.size >= 128) { truncated = true; break@outer }
                if (element.effect.id !in setOf("damage_multiplier", "make_skill_crit", "crit_multiplier")) continue
                val raw = element.config.getStringOrNull("multiplier")
                val literal = raw?.toDoubleOrNull()?.takeIf { it.isFinite() }
                val key = provided.holder.holder.id
                entries.add(CachedEffectEntry(key.namespace, key.key, element.effect.id,
                    provided.effect.triggers.map { it.id },
                    (provided.holder.holder.conditions + provided.effect.conditions + element.conditions)
                        .filter { it.condition.id == "in_world" && !it.isInverted }
                        .mapNotNull { it.config.getStringOrNull("world") }, literal,
                    provided.effect.filters.isNotEmpty() || element.filters.isNotEmpty() ||
                        element.conditions.isNotEmpty() || provided.effect.mutators.isNotEmpty() || element.mutators.isNotEmpty()))
            }
        }
        return CachedEffectsSnapshot(true, published.second, truncated, entries)
    }
}
