package com.willfp.libreforge

import kotlin.test.*

class NonDestructiveBreakageTest {
    @Test fun `healthy custom item avoids expensive custom identity lookup`() {
        assertFalse(nonDestructiveBrokenItem(100, { true }, { 50 }, { error("lookup on healthy item") }))
    }
    @Test fun `non damageable and metadata without damage avoid all expensive checks`() {
        assertFalse(nonDestructiveBrokenItem(0, { error("damage flag") }, { error("damage") }, { error("lookup") }))
        assertFalse(nonDestructiveBrokenItem(100, { false }, { error("damage") }, { error("lookup") }))
    }
    @Test fun `custom maximum and max minus one boundary preserve non destructive breakage`() {
        var lookups = 0
        val lookup = { lookups++; true }
        assertFalse(nonDestructiveBrokenItem(7, { true }, { 5 }, lookup))
        assertTrue(nonDestructiveBrokenItem(7, { true }, { 6 }, lookup))
        assertTrue(nonDestructiveBrokenItem(7, { true }, { 7 }, lookup))
        assertEquals(2, lookups)
    }
    @Test fun `vanilla broken items remain outside custom breakage`() {
        var calls = 0
        assertFalse(nonDestructiveBrokenItem(100, { true }, { 99 }, { calls++; false }))
        assertEquals(1, calls)
    }
    @Test fun `zero damage semantics are retained even for maximum one`() {
        assertFalse(nonDestructiveBrokenItem(1, { false }, { 0 }, { error("lookup") }))
        assertTrue(nonDestructiveBrokenItem(1, { true }, { 0 }, { true }))
    }
    @Test fun `actual getter respects custom max durability and returns before lookup for healthy equipment`() {
        val item = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack::class.java)
        val meta = org.mockito.Mockito.mock(org.bukkit.inventory.meta.Damageable::class.java)
        org.mockito.Mockito.`when`(item.itemMeta).thenReturn(meta)
        org.mockito.Mockito.`when`(meta.hasMaxDamage()).thenReturn(true)
        org.mockito.Mockito.`when`(meta.maxDamage).thenReturn(7)
        org.mockito.Mockito.`when`(meta.hasDamage()).thenReturn(true)
        org.mockito.Mockito.`when`(meta.damage).thenReturn(5)
        assertFalse(item.isBrokenItem)
        org.mockito.Mockito.verify(item, org.mockito.Mockito.times(1)).itemMeta
        org.mockito.Mockito.verify(item, org.mockito.Mockito.never()).type
    }
    @Test fun `actual getter accepts null and non damageable items without inspecting material`() {
        assertFalse((null as org.bukkit.inventory.ItemStack?).isBrokenItem)
        val item = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack::class.java)
        org.mockito.Mockito.`when`(item.itemMeta).thenReturn(org.mockito.Mockito.mock(org.bukkit.inventory.meta.ItemMeta::class.java))
        assertFalse(item.isBrokenItem)
        org.mockito.Mockito.verify(item, org.mockito.Mockito.never()).type
    }
}
