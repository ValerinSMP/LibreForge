package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.getDoubleFromExpression
import com.willfp.libreforge.integrations.worldguard.WorldGuardIntegration
import com.willfp.libreforge.plugin
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.Location
import org.bukkit.Bukkit
import org.bukkit.block.BlockFace
import kotlin.math.abs

object EffectTransmission : Effect<NoCompileData>("transmission") {
    override val description = "Teleports the player forward by a given distance, stopping at the first solid block in the path."
    override val categories = setOf("movement")

    override val parameters = setOf(
        TriggerParameter.PLAYER
    )

    override val arguments = arguments {
        require(
            "distance",
            "You must specify the distance to transmit!",
            description = "The maximum distance to teleport the player forward. Supports expressions.",
            type = ArgType.EXPRESSION,
            example = "10 + %level%"
        )
    }

    override fun onTrigger(config: Config, data: TriggerData, compileData: NoCompileData): Boolean {
        val player = data.player ?: return false

        val distance = config.getDoubleFromExpression("distance", data)

        val ray = player.rayTraceBlocks(distance)

        if (ray != null && ray.hitBlock != null && ray.hitBlockFace != null) {
            val hitBlock = ray.hitBlock!!
            val hitFace = ray.hitBlockFace!!

            val feetLocation = hitBlock.getRelative(hitFace).location.add(0.5, 0.5 - player.eyeHeight, 0.5)

            if (isSafeLocation(feetLocation, player)) {
                feetLocation.pitch = player.location.pitch
                feetLocation.yaw = player.location.yaw
                return player.teleport(feetLocation)
            }

            val safe = findSafePosition(hitBlock, hitFace, player)
            if (safe != null) {
                safe.pitch = player.location.pitch
                safe.yaw = player.location.yaw
                return player.teleport(safe)
            }

            player.sendMessage(plugin.langYml.getFormattedString("messages.cannot-transmit"))
            return false
        }

        val movement = player.eyeLocation.direction.clone().normalize().multiply(distance)
        val location = player.eyeLocation.clone().add(movement)

        if (!isSafeLocation(location, player)) {
            player.sendMessage(plugin.langYml.getFormattedString("messages.cannot-transmit"))
            return false
        }

        location.pitch = player.location.pitch
        location.yaw = player.location.yaw
        return player.teleport(location)
    }

    private fun isSafeLocation(loc: Location, player: org.bukkit.entity.Player): Boolean {
        val feetBlock = loc.block
        if (feetBlock.isSolid) return false
        val headBlock = feetBlock.getRelative(BlockFace.UP)
        if (headBlock.isSolid) return false
        if (abs(loc.toVector().distanceSquared(player.location.toVector())) < 0.01) return false
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard") &&
            !WorldGuardIntegration.canTransmit(player, player.location, loc)
        ) return false
        return true
    }

    private fun findSafePosition(hitBlock: org.bukkit.block.Block, hitFace: BlockFace, player: org.bukkit.entity.Player): Location? {
        if (hitFace == BlockFace.UP) {
            for (y in 0..1) {
                val candidate = hitBlock.location.add(0.5, y + 1.0 - player.eyeHeight, 0.5)
                if (isSafeLocation(candidate, player)) return candidate
            }
        }
        return null
    }
}
