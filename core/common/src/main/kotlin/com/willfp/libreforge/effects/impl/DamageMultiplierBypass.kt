package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import org.bukkit.entity.Player
import java.util.UUID

internal fun damageOrigin(
    value: String?,
    ability: Boolean = false,
    damageMode: String? = null
): DamageOrigin =
    if (ability || damageMode.equals("ability_only", ignoreCase = true)) DamageOrigin.ACTIVE
    else value?.uppercase()?.let { runCatching { DamageOrigin.valueOf(it) }.getOrNull() } ?: DamageOrigin.NORMAL

internal fun damageOrigin(config: Config): DamageOrigin = damageOrigin(
    config.getStringOrNull("damage_origin"),
    config.getBoolOrNull("ability") ?: false,
    config.getStringOrNull("damage_mode")
)

enum class DamageOrigin {
    NORMAL,
    PASSIVE,
    ACTIVE
}

/** The damage origin known synchronously while LibreForge creates damage. */
data class DamageProvenance(
    val origin: DamageOrigin,
    val sourcePlayerId: UUID?
)

/** Carries one damage provenance through the current call stack. */
object DamageMultiplierBypass {
    private val provenance = ThreadLocal<DamageProvenance?>()

    val current: DamageProvenance?
        get() = provenance.get()

    /**
     * Legacy ABI bridge. Phase 1 must not bypass receiver triggers or multipliers.
     */
    val isActive: Boolean
        get() = false

    @JvmStatic
    val isAbility: Boolean
        get() = current?.origin == DamageOrigin.ACTIVE

    val isAbilityOnly: Boolean
        get() = false

    fun <T> withProvenance(provenance: DamageProvenance, block: () -> T): T =
        withCurrentProvenance(provenance, block)

    /**
     * Legacy ABI bridge. Kept for consumers compiled against LibreForge, without
     * suppressing damage receivers.
     */
    fun <T> run(block: () -> T): T =
        withProvenance(DamageProvenance(DamageOrigin.NORMAL, null), block)

    fun sourcePlayerId(useSource: Boolean, playerId: UUID?): UUID? =
        playerId.takeIf { useSource }

    fun matchesActivePlayerDamage(source: Player, victim: Player): Boolean =
        source != victim && current?.let { it.origin == DamageOrigin.ACTIVE && it.sourcePlayerId == source.uniqueId } == true

    private fun <T> withCurrentProvenance(next: DamageProvenance, block: () -> T): T {
        val previous = provenance.get()
        provenance.set(next)

        try {
            return block()
        } finally {
            if (previous == null) provenance.remove() else provenance.set(previous)
        }
    }
}
