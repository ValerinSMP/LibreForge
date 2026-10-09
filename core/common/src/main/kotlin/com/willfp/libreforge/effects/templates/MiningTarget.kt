package com.willfp.libreforge.effects.templates

import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.attribute.Attribute

/** Reused by individual effects and the opt-in preflight for a whole mining chain. */
internal fun isMiningTargetReachable(data: TriggerData): Boolean {
    val player = data.player ?: return false
    val target = data.block?.location ?: data.location ?: return false
    if (target.world != player.world) return false
    val eye = player.eyeLocation
    val reach = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE)?.value ?: return false
    if (!isMiningAnchorInReach(eye.x, eye.y, eye.z, target.blockX, target.blockY, target.blockZ, reach)) return false
    val block = data.block ?: target.block
    return !block.hasMetadata("blockbreakevent-ignore")
}

internal fun miningPreflight(enabled: Boolean, validTarget: () -> Boolean): Boolean =
    !enabled || validTarget()
