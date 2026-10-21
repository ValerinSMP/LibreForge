package com.willfp.libreforge.effects.orbitals

import org.bukkit.event.entity.EntityDamageEvent
import java.util.UUID

/** Does not cancel or heal. Later cancellation/overwrite rolls back the reservation before visual break. */
internal class OrbitalDamageBoundary(private val charges: OrbitalCharges) {
    @Suppress("DEPRECATION")
    fun block(player: UUID, event: EntityDamageEvent): OrbitalCharges.Lease? {
        if (event.isCancelled || !event.finalDamage.isFinite() || event.finalDamage <= 0 ||
            !event.damage.isFinite() || event.damage <= 0) return null
        val original = EntityDamageEvent.DamageModifier.values().filter { event.isApplicable(it) }.associateWith { event.getDamage(it) }
        val lease = charges.reserve(player, event) ?: return null
        try {
            event.damage = 0.0 // Bukkit recalculates armor/absorption; no health or maximum-health writes.
            if (event.damage == 0.0 && event.finalDamage == 0.0) return lease
        } catch (failure: RuntimeException) {
            charges.finish(lease, false)
            original.forEach { (modifier, value) -> event.setDamage(modifier, value) }
            throw failure
        }
        charges.finish(lease, false)
        original.forEach { (modifier, value) -> event.setDamage(modifier, value) }
        return null
    }
    fun finish(lease: OrbitalCharges.Lease): Int? {
        val event = lease.event as EntityDamageEvent
        return charges.finish(lease, !event.isCancelled && event.damage == 0.0 && event.finalDamage == 0.0)
    }
}

/** Intrinsic coverage stays separate from permissions and provenance; no environmental/periodic/area damage. */
internal fun directOrbitalAttacker(event: org.bukkit.event.entity.EntityDamageByEntityEvent): org.bukkit.entity.LivingEntity? = when {
    event.cause==EntityDamageEvent.DamageCause.ENTITY_ATTACK && event.damager is org.bukkit.entity.LivingEntity -> event.damager as org.bukkit.entity.LivingEntity
    event.cause==EntityDamageEvent.DamageCause.PROJECTILE && event.damager is org.bukkit.entity.Projectile -> (event.damager as org.bukkit.entity.Projectile).shooter as? org.bukkit.entity.LivingEntity
    else -> null
}
