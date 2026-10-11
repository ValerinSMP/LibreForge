package com.willfp.libreforge.effects.templates

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MiningOffsetTest {
    private fun verifyOffsets(origin: Location, offsets: List<Triple<Double, Double, Double>>) {
        val world = Mockito.mock(World::class.java)
        val block = Mockito.mock(Block::class.java)
        val actual = mutableListOf<Triple<Int, Int, Int>>()
        Mockito.`when`(world.getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenAnswer {
            actual.add(Triple(it.getArgument(0), it.getArgument(1), it.getArgument(2)))
            block
        }
        val before = origin.clone()
        val expected = offsets.map { (x, y, z) ->
            val old = origin.clone().add(x, y, z)
            Triple(old.blockX, old.blockY, old.blockZ)
        }
        offsets.forEach { (x, y, z) -> assertSame(block, miningOffsetBlock(world, origin, x, y, z)) }
        assertEquals(expected, actual)
        assertEquals(before, origin)
    }

    @Test fun `negative and fractional positions retain Bukkit flooring`() {
        for (origin in listOf(Location(null, -0.1, -64.9, -200.5), Location(null, 0.0, 319.0, 200.0))) {
            verifyOffsets(origin, listOf(Triple(-0.9, 0.5, 0.49), Triple(0.1, -0.1, -0.51), Triple(1.0, 1.0, -1.0)))
        }
    }

    @Test fun `cube candidates retain their coordinate order`() {
        val offsets = buildList {
            for (x in -2..2) for (y in -2..2) for (z in -2..2) {
                if (x == 0 && y == 0 && z == 0) continue
                add(Triple(x.toDouble(), y.toDouble(), z.toDouble()))
            }
        }
        assertEquals(124, offsets.size)
        verifyOffsets(Location(null, -100.0, 64.0, -200.0), offsets)
    }

    @Test fun `rotated layers retain coordinates without mutating origin`() {
        val offsets = buildList {
            for (layer in 0..2) for (up in -1..1) for (right in -1..1) {
                if (layer == 0 && up == 0 && right == 0) continue
                add(Triple(layer.toDouble(), up.toDouble(), -right.toDouble()))
            }
        }
        verifyOffsets(Location(null, -1.0, -63.0, -1.0), offsets)
    }

    @Test fun `drill offsets retain vector multiplication coordinates`() {
        verifyOffsets(Location(null, -50.0, 70.0, -50.0), (1..8).map {
            Triple(-0.6 * it, 0.0 * it, 0.8 * it)
        })
    }
}
