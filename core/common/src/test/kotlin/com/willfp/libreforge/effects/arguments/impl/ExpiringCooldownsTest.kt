package com.willfp.libreforge.effects.arguments.impl

import java.util.UUID
import kotlin.test.*

class ExpiringCooldownsTest {
    private val player = UUID(0, 1)
    @Test fun `active shared groups survive maintenance and reconnect identity`() {
        val store = ExpiringCooldowns()
        store.put("shared", player, 1000)
        store.sweep(999, 256)
        assertEquals(1000L, store.get("shared", UUID(0, 1)))
        assertNull(store.get("other", player))
        store.sweep(1000, 256)
        assertNull(store.get("shared", player))
        assertEquals(0, store.size)
    }
    @Test fun `replacement survives weak iterator and expired keys eventually removed`() {
        val store = ExpiringCooldowns()
        repeat(1000) { store.put("g", UUID(0, it.toLong()), 10) }
        assertTrue(store.sweep(5, 7) <= 7)
        store.put("g", player, 10000)
        repeat(300) { assertTrue(store.sweep(10, 7) <= 7) }
        assertEquals(1, store.size)
        assertEquals(10000L, store.get("g", player))
    }
    @Test fun `new entries between passes and empty maintenance are safe`() {
        val store = ExpiringCooldowns()
        assertEquals(0, store.sweep(0, 1))
        store.put("g", player, -1)
        repeat(3) { store.sweep(0, 1) }
        assertEquals(0, store.size)
        assertFailsWith<IllegalArgumentException> { store.sweep(0, 0) }
    }
}
