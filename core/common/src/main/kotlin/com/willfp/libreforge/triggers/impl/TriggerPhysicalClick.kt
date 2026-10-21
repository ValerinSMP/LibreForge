package com.willfp.libreforge.triggers.impl

import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.*
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.player.*
import org.bukkit.inventory.EquipmentSlot
import java.util.UUID

/** Physical buttons, unlike alt_click: weapons never invert left and right. */
internal fun physicalClick(event: Event): String? = when (event) {
    is PlayerInteractEvent -> if (event.hand != EquipmentSlot.HAND || event.useItemInHand() == Event.Result.DENY ||
        (event.clickedBlock != null && event.useInteractedBlock() == Event.Result.DENY)) null else when(event.action) {
        Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> "left_click"
        Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> "right_click"
        else -> null
    }
    is PlayerInteractEntityEvent -> if (!event.isCancelled && event.hand == EquipmentSlot.HAND) "right_click" else null
    is EntityDamageByEntityEvent -> if (!event.isCancelled && event.cause == DamageCause.ENTITY_ATTACK && event.damager is Player) "left_click" else null
    else -> null
}

internal class PhysicalGestures {
    private var tick = Int.MIN_VALUE
    private val players = mutableSetOf<UUID>()
    fun claim(player: UUID, currentTick: Int): Boolean {
        if (tick != currentTick) { players.clear(); tick = currentTick }
        return players.size < 2048 && players.add(player)
    }
}

object TriggerPhysicalClick : Trigger("physical_click") {
    override val description = "Fires once per tick on main-hand physical left or right clicks, including entities."
    override val categories = setOf("interaction")
    override val parameters = setOf(TriggerParameter.PLAYER, TriggerParameter.EVENT, TriggerParameter.LOCATION, TriggerParameter.ITEM)
    private val gestures = PhysicalGestures()
    /** Air's predicted block cancellation is not an item denial. Explicit denials stay denied. */
    internal fun accepts(event: Event) = physicalClick(event) != null
    @EventHandler(priority=EventPriority.MONITOR)
    fun interact(event: PlayerInteractEvent) { fire(event.player, event) }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    fun entity(event: PlayerInteractEntityEvent) { fire(event.player, event) }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    fun atEntity(event: PlayerInteractAtEntityEvent) { fire(event.player, event) }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    fun attack(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        fire(player, event)
    }
    private fun fire(player: Player, event: Event) {
        if (!accepts(event) || !gestures.claim(player.uniqueId, Bukkit.getCurrentTick())) return
        dispatch(player.toDispatcher(), TriggerData(player=player, event=event, location=player.location, item=player.inventory.itemInMainHand))
    }
}
