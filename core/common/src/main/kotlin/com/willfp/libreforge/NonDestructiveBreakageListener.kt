package com.willfp.libreforge

import com.willfp.libreforge.diagnostics.performanceScope

import com.willfp.eco.core.items.Items
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.inventory.meta.Damageable

val org.bukkit.inventory.ItemStack?.isBrokenItem: Boolean
    get() = performanceScope({ "libreforge:items" }, { "items:broken-check" }) {
        isBrokenItemMeasured()
    }

private fun org.bukkit.inventory.ItemStack?.isBrokenItemMeasured(): Boolean {
        if (this == null) return false
        val meta = this.itemMeta as? Damageable ?: return false
        val maxDurability = if (meta.hasMaxDamage()) meta.maxDamage else this.type.maxDurability.toInt()
        return nonDestructiveBrokenItem(maxDurability, { meta.hasDamage() }, { meta.damage }, { Items.isCustomItem(this) })
    }

object NonDestructiveBreakageListener : Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onItemDamage(event: PlayerItemDamageEvent) {
        val item = event.item
        if (!Items.isCustomItem(item)) return
        val meta = item.itemMeta as? Damageable ?: return
        val maxDurability = if (meta.hasMaxDamage()) meta.maxDamage else item.type.maxDurability.toInt()
        if (maxDurability <= 0) return

        val currentDamage = meta.damage
        val isAlreadyBroken = currentDamage >= maxDurability - 1
        val projectedDamage = currentDamage + event.damage

        if (projectedDamage >= maxDurability - 1) {
            meta.damage = maxDurability - 1
            item.itemMeta = meta
            event.isCancelled = true

            if (!isAlreadyBroken) {
                val player = event.player
                player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 0.9f)
                plugin.scheduler.run {
                    player.toDispatcher().refreshHolders()
                }
            }
        }
    }
}

/** Keep physical eligibility ahead of the expensive registry lookup; no mutable-item cache. */
internal inline fun nonDestructiveBrokenItem(
    maxDurability: Int, hasDamage: () -> Boolean, damage: () -> Int, isCustom: () -> Boolean
): Boolean {
    if (maxDurability <= 0 || !hasDamage() || damage() < maxDurability - 1) return false
    return isCustom()
}
