package com.willfp.libreforge.effects.templates

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block

/** Same flooring as Location.clone().add(...), without a Location per candidate. */
internal fun miningOffsetBlock(world: World, origin: Location, x: Double, y: Double, z: Double): Block =
    world.getBlockAt(Location.locToBlock(origin.x + x), Location.locToBlock(origin.y + y), Location.locToBlock(origin.z + z))
