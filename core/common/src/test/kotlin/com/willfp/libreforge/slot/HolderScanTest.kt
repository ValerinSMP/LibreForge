package com.willfp.libreforge.slot

import kotlin.test.*

class HolderScanTest {
    private data class Item(val name: String, val broken: Boolean, val holders: List<String>)
    private data class Provided(val holder: String, val item: Item, val slot: String)

    @Test fun `scan matches original pipeline and preserves slot item and duplicate order`() {
        val first = Item("first", false, listOf("a", "rejected", "a"))
        val second = Item("second", false, listOf("a", "b"))
        val broken = Item("broken", true, listOf("b"))
        val slots = linkedMapOf("armor" to listOf(first, broken), "custom" to listOf(second, first))
        val expected = slots.flatMap { (slot, items) ->
            items.flatMap { item ->
                if (item.broken) emptyList() else item.holders.filter { it != "rejected" }
                    .map { Provided(it, item, slot) }
            }
        }
        val actual = mutableListOf<Provided>()
        val scratch = mutableListOf<String>()
        for ((slot, items) in slots) {
            appendMatchingHolders(items, actual, scratch, { it.broken }, { it.holders },
                { it != "rejected" }, { holder, item -> Provided(holder, item, slot) })
        }
        assertEquals(expected, actual)
        assertSame(first, actual.first().item)
        assertSame(second, actual[2].item)
    }

    @Test fun `broken items never invoke recognition and validation precedes all construction`() {
        val calls = mutableListOf<String>()
        val items = listOf(Item("broken", true, listOf("x")), Item("valid", false, listOf("a", "b", "a")))
        val output = mutableListOf<String>()
        appendMatchingHolders(items, output, mutableListOf(),
            { calls += "broken:${it.name}"; it.broken },
            { calls += "find:${it.name}"; it.holders },
            { calls += "valid:$it"; it != "b" },
            { holder, _ -> calls += "provide:$holder"; holder })
        assertEquals(listOf("broken:broken", "broken:valid", "find:valid", "valid:a", "valid:b", "valid:a", "provide:a", "provide:a"), calls)
        assertEquals(listOf("a", "a"), output)
    }

    @Test fun `scratch cannot leak previous item or slot results`() {
        val output = mutableListOf<String>()
        val scratch = mutableListOf<String>()
        fun scan(items: List<Item>) = appendMatchingHolders(items, output, scratch,
            { it.broken }, { it.holders }, { it != "rejected" }, { h, _ -> h })
        scan(listOf(Item("first", false, listOf("a"))))
        scan(emptyList())
        scan(listOf(Item("empty", false, emptyList()), Item("reject", false, listOf("rejected"))))
        scan(listOf(Item("second", false, listOf("b"))))
        assertEquals(listOf("a", "b"), output)
        assertEquals(listOf("b"), scratch)
    }

    @Test fun `validation failure propagates and constructs no holders for that item`() {
        val failure = IllegalStateException("original")
        val output = mutableListOf<String>()
        assertSame(failure, assertFailsWith<IllegalStateException> {
            appendMatchingHolders(listOf(Item("test", false, listOf("a", "b"))), output, mutableListOf(),
                { it.broken }, { it.holders }, { if (it == "b") throw failure; true }, { h, _ -> h })
        })
        assertTrue(output.isEmpty())
    }

    @Test fun `construction failure retains original prefix and exception`() {
        val failure = IllegalArgumentException("original")
        val output = mutableListOf<String>()
        assertSame(failure, assertFailsWith<IllegalArgumentException> {
            appendMatchingHolders(listOf(Item("test", false, listOf("a", "b", "c"))), output, mutableListOf(),
                { it.broken }, { it.holders }, { true }, { h, _ -> if (h == "b") throw failure else h })
        })
        assertEquals(listOf("a"), output)
    }
}
