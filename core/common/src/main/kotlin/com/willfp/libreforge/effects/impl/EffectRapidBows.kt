package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.map.listMap
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.IdentifiedModifier
import com.willfp.libreforge.effects.Identifiers
import com.willfp.libreforge.get
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityShootBowEvent
import java.util.UUID
import kotlin.math.min

object EffectRapidBows : Effect<NoCompileData>("rapid_bows") {
    override val description = "Allows the player to fire bows faster by a given percentage, as a permanent passive effect."
    override val categories = setOf("combat")

    override val arguments = arguments {
        optional("normalized_force",description="Interpret Paper force as its native 0..1 value.",type=ArgType.BOOLEAN,default="false")
        require(
            "percent_faster",
            "You must specify how many percent faster to make bow pulls!",
            description = "How many percent faster the bow charges. Supports expressions.",
            type = ArgType.EXPRESSION,
            example = "10 + %level%"
        )
    }

    private data class BowModifier(val effect:IdentifiedModifier,val normalized:Boolean)
    private val modifiers = listMap<UUID, BowModifier>()

    private const val MAX_FORCE = 3.0

    override fun onEnable(
        dispatcher: Dispatcher<*>,
        config: Config,
        identifiers: Identifiers,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ) {
        modifiers[dispatcher.uuid].removeIf{it.effect.uuid==identifiers.uuid}
        modifiers[dispatcher.uuid].add(BowModifier(IdentifiedModifier(identifiers.uuid) {
            config.getDoubleFromExpression("percent_faster", dispatcher.get())
        },config.getBool("normalized_force")))
    }

    override fun onDisable(dispatcher: Dispatcher<*>, identifiers: Identifiers, holder: ProvidedHolder) {
        if(dispatcher.uuid !in modifiers)return
        modifiers[dispatcher.uuid].removeIf { it.effect.uuid == identifiers.uuid }
        if(modifiers[dispatcher.uuid].isEmpty())modifiers.remove(dispatcher.uuid)
    }

    @EventHandler(
        priority = EventPriority.LOW,
        ignoreCancelled = true
    )
    fun handle(event: EntityShootBowEvent) {
        val entity = event.entity

        if(entity.uniqueId !in modifiers)return
        val active=modifiers[entity.uniqueId]
        val totalPercentFaster = active
            .sumOf { it.effect.modifier }
            .coerceAtMost(100.0)

        val multiplier = 1 - totalPercentFaster / 100

        // Paper's event force is already normalized to 0..1; legacy consumers remain opt-in.
        val bowForce = if(active.any{it.normalized})event.force.toDouble() else event.force / MAX_FORCE
        if(!bowForce.isFinite()||bowForce<=0||!totalPercentFaster.isFinite())return

        if (bowForce < multiplier) {
            return
        }

        val force = min(1.0 / bowForce, Double.MAX_VALUE)
        var velocity = event.projectile.velocity.multiply(force)

        if (velocity.length() > 3) {
            velocity = velocity.normalize().multiply(3)
        }

        event.projectile.velocity = velocity
    }
}
