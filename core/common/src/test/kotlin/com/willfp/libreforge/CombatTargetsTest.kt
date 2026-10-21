package com.willfp.libreforge

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import kotlin.test.Test
import kotlin.test.*
import org.mockito.Mockito.*

class CombatTargetsTest {
    @Test fun `unscoped other plugins retain existing selection`() { assertTrue(CombatTargets.allows(mock(LivingEntity::class.java))) }
    @Test fun `nested scopes restore even on exceptions without leaking between holders`() {
        val actor = mock(Player::class.java); val target = mock(LivingEntity::class.java)
        val allow = object : CombatTargetHolder { override fun allowsCombatTarget(actor: Player, target: LivingEntity, allowDead: Boolean, defensive: Boolean) = true }
        val deny = object : CombatTargetHolder { override fun allowsCombatTarget(actor: Player, target: LivingEntity, allowDead: Boolean, defensive: Boolean) = false }
        val context = CombatTargets.Context(allow, actor); val blocked = CombatTargets.Context(deny, actor)
        CombatTargets.with(context) {
            assertTrue(CombatTargets.allows(target))
            assertFailsWith<IllegalStateException> { CombatTargets.with(blocked) { assertFalse(CombatTargets.allows(target)); error("test") } }
            assertSame(context, CombatTargets.capture()); assertTrue(CombatTargets.allows(target))
        }
        assertNull(CombatTargets.capture())
    }
    @Test fun `captured periodic context revalidates on every use`() {
        val actor = mock(Player::class.java); val target = mock(LivingEntity::class.java); var permitted = true
        val holder = object : CombatTargetHolder { override fun allowsCombatTarget(actor: Player, target: LivingEntity, allowDead: Boolean, defensive: Boolean) = permitted }
        val captured = CombatTargets.with(CombatTargets.Context(holder, actor)) { CombatTargets.capture()!! }
        assertTrue(captured.allows(target)); permitted = false; assertFalse(captured.allows(target))
        CombatTargets.with(captured) { assertFalse(CombatTargets.allows(target)) }; assertNull(CombatTargets.capture())
    }
    @Test fun `original session invalidation prevents periodic resumption after respawn or transfer`() {
        var session: Any? = "first"
        val actor = mock(Player::class.java); val target = mock(LivingEntity::class.java)
        val holder = object : CombatTargetHolder {
            override fun combatTargetSession(actor: Player) = session
            override fun allowsCombatTarget(actor: Player, target: LivingEntity, allowDead: Boolean, defensive: Boolean) = true
        }
        val captured = CombatTargets.Context(holder, actor)
        assertTrue(captured.allows(target)); session = "replacement"; assertFalse(captured.allows(target))
        session = null; assertFalse(captured.allows(target))
    }
    @Test fun `cloud filters actual secondary recipients and leaves foreign clouds untouched`() {
        val actor = mock(Player::class.java); val allowed = mock(LivingEntity::class.java); val denied = mock(LivingEntity::class.java)
        val holder = object : CombatTargetHolder { override fun allowsCombatTarget(actor: Player, target: LivingEntity, allowDead: Boolean, defensive: Boolean) = target === allowed }
        val cloud = mock(org.bukkit.entity.AreaEffectCloud::class.java); val id = java.util.UUID.randomUUID()
        `when`(cloud.uniqueId).thenReturn(id)
        val event = mock(org.bukkit.event.entity.AreaEffectCloudApplyEvent::class.java)
        `when`(event.entity).thenReturn(cloud)
        val recipients = mutableListOf(allowed, denied); `when`(event.affectedEntities).thenReturn(recipients)
        assertTrue(CombatTargets.bind(cloud, CombatTargets.Context(holder, actor)))
        CombatTargets.cloud(event); assertEquals(listOf(allowed), recipients)
        val removal = mock(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent::class.java); `when`(removal.entity).thenReturn(cloud)
        CombatTargets.removed(removal); recipients.add(denied); CombatTargets.cloud(event); assertEquals(2, recipients.size)
    }
    @Test fun `secondary damage cancellation is scoped and never uncancels a protection`() {
        val actor = mock(Player::class.java); val victim = mock(LivingEntity::class.java)
        val holder = object : CombatTargetHolder { override fun allowsCombatTarget(actor: Player, target: LivingEntity, allowDead: Boolean, defensive: Boolean) = false }
        val event = mock(org.bukkit.event.entity.EntityDamageEvent::class.java); `when`(event.entity).thenReturn(victim)
        CombatTargets.damage(event); verify(event, never()).setCancelled(true)
        CombatTargets.with(CombatTargets.Context(holder, actor)) { CombatTargets.damage(event) }
        verify(event).setCancelled(true); verify(event, never()).setCancelled(false)
    }

}
