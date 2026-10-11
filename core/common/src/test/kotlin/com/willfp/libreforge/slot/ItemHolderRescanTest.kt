package com.willfp.libreforge.slot

import com.willfp.libreforge.Holder
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.mockito.Mockito.*
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class ItemHolderRescanTest {
    private val slot = object : SlotType("test_slot") {
        override fun addToSlot(player: Player, item: ItemStack) = false
        override fun getItems(entity: LivingEntity) = emptyList<ItemStack>()
        override fun getItemSlots(player: Player) = emptyList<Int>()
    }
    private class Finder : ItemHolderFinder<Holder>() {
        var result = emptyList<Holder>()
        var finds = 0
        val validated = mutableListOf<Holder>()
        var rejected: Holder? = null
        var failure: RuntimeException? = null
        override fun find(item: ItemStack): List<Holder> { finds++; return result }
        override fun isValidInSlot(holder: Holder, slot: SlotType): Boolean {
            validated += holder
            failure?.let { throw it }
            return holder !== rejected
        }
    }
    private fun read(type: Material): ReadSlot {
        val item = mock(ItemStack::class.java)
        `when`(item.type).thenReturn(type)
        `when`(item.amount).thenReturn(1)
        // The pass has already read Bukkit items; keep this test focused on rescan.
        val read = mock(ReadSlot::class.java)
        `when`(read.items).thenReturn(listOf(item))
        `when`(read.types).thenReturn(arrayOf<Material?>(type))
        return read
    }
    private fun scan(finder: Finder, read: ReadSlot, previous: Any? = null): Any {
        val method = ItemHolderFinder::class.java.declaredMethods.single { it.name == "rescan" }
        method.isAccessible = true
        return method.invoke(finder, slot, read, previous, null)
    }
    private fun provided(scan: Any): List<*> {
        val field = scan.javaClass.getDeclaredField("provided")
        field.isAccessible = true
        return field.get(scan) as List<*>
    }
    @Test fun `unchanged full scan reuses state but repeats recognition and validation`() {
        val finder = Finder()
        val holder = mock(Holder::class.java)
        finder.result = listOf(holder)
        val first = scan(finder, read(Material.STONE))
        val second = scan(finder, read(Material.STONE), first)
        assertSame(first, second)
        assertEquals(2, finder.finds)
        assertEquals(listOf(holder, holder), finder.validated)
    }
    @Test fun `empty recognition reuses state without dropping recognition callbacks`() {
        val finder = Finder()
        val first = scan(finder, read(Material.STONE))
        assertSame(first, scan(finder, read(Material.STONE), first))
        assertEquals(2, finder.finds)
        assertTrue(provided(first).isEmpty())
    }
    @Test fun `changed material updates scan even when both have no holders`() {
        val finder = Finder()
        val first = scan(finder, read(Material.STONE))
        val second = scan(finder, read(Material.DIRT), first)
        assertNotSame(first, second)
        assertSame(second, scan(finder, read(Material.DIRT), second))
        assertEquals(3, finder.finds)
    }
    @Test fun `same material with changed holders preserves duplicates and validation order`() {
        val finder = Finder()
        val a = mock(Holder::class.java); val rejected = mock(Holder::class.java); val b = mock(Holder::class.java)
        finder.rejected = rejected
        finder.result = listOf(a, rejected, a)
        val first = scan(finder, read(Material.STONE))
        finder.result = listOf(b, rejected, b)
        val second = scan(finder, read(Material.STONE), first)
        assertNotSame(first, second)
        assertEquals(listOf(a, rejected, a, b, rejected, b), finder.validated)
        assertEquals(listOf(b, b), provided(second).map { (it as SlotItemProvidedHolder<*>).holder })
    }
    @Test fun `failed validation propagates original exception during otherwise unchanged scan`() {
        val finder = Finder();finder.result = listOf(mock(Holder::class.java))
        val first = scan(finder, read(Material.STONE))
        val failure = IllegalStateException("validation")
        finder.failure = failure
        val thrown = assertFailsWith<InvocationTargetException> { scan(finder, read(Material.STONE), first) }
        assertSame(failure, thrown.cause)
        assertEquals(2, finder.finds)
    }
}
