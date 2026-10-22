package com.willfp.libreforge.observation

import org.bukkit.Bukkit
import org.junit.Test
import org.mockito.Mockito
import java.util.UUID
import kotlin.test.*

class CachedEffectsTest {
    @Test fun coldObservationNeverAsksProvidersOrCreatesState() {
        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<Boolean> { Bukkit.isPrimaryThread() }.thenReturn(true)
            val uuid = UUID.randomUUID()
            repeat(3) {
                val result = CachedEffects.getCachedObservation(uuid)
                assertFalse(result.available); assertFalse(result.pending); assertTrue(result.entries.isEmpty())
            }
            assertNull(com.willfp.libreforge.HolderStates.peekPublishedEffects(uuid))
        }
    }
    @Test fun wrongThreadRejectedWithoutProviderAccess() {
        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<Boolean> { Bukkit.isPrimaryThread() }.thenReturn(false)
            assertFailsWith<IllegalStateException> { CachedEffects.getCachedObservation(UUID.randomUUID()) }
        }
    }
    @Test fun snapshotsDeepCopyCollectionsWithoutClaimingImpactEligibility() {
        val triggers = mutableListOf("melee_attack")
        val worlds = mutableListOf("world_crimson")
        val entry = CachedEffectEntry("ecoarmor", "crimson", "damage_multiplier", triggers, worlds, 1.35, true)
        val entries = mutableListOf(entry)
        val snapshot = CachedEffectsSnapshot(true, true, false, entries)
        triggers.clear(); worlds.clear(); entries.clear()
        assertEquals(listOf("melee_attack"), snapshot.entries.single().triggers)
        assertEquals(listOf("world_crimson"), snapshot.entries.single().worlds)
        assertEquals(1.35, snapshot.entries.single().literalMultiplier)
        assertFailsWith<UnsupportedOperationException> { (snapshot.entries as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (entry.triggers as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (entry.worlds as MutableList).clear() }
        assertTrue(snapshot.pending); assertTrue(entry.hasEventRestrictions)
    }
}
