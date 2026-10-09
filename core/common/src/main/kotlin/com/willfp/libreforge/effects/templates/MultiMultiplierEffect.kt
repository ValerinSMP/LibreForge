package com.willfp.libreforge.effects.templates

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.map.listMap
import com.willfp.eco.core.map.nestedListMap
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.IdentifiedModifier
import com.willfp.libreforge.effects.Identifiers
import com.willfp.libreforge.get
import com.willfp.libreforge.toDispatcher
import org.bukkit.entity.Player
import java.util.UUID

abstract class MultiMultiplierEffect<T : Any>(id: String) : Effect<NoCompileData>(id) {
    override val arguments = arguments {
        require(
            "multiplier",
            "You must specify the multiplier!",
            description = "The multiplier to apply. Supports expressions.",
            type = ArgType.EXPRESSION
        )
    }

    private val globalModifiers = ActiveModifierLists<UUID, IdentifiedModifier>()
    private val modifiers = mutableMapOf<UUID, ActiveModifierLists<T, IdentifiedModifier>>()

    /**
     * The key to look for in arguments, e.g. "stat" or "skill".
     */
    abstract val key: String

    override fun onEnable(
        dispatcher: Dispatcher<*>,
        config: Config,
        identifiers: Identifiers,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ) {
        if (config.has(key)) {
            val elements = config.getStrings(key).mapNotNull { getElement(it) }

            for (element in elements) {
                modifiers.getOrPut(dispatcher.uuid) { ActiveModifierLists() }.add(element, IdentifiedModifier(identifiers.uuid) {
                    config.getDoubleFromExpression("multiplier", dispatcher.get())
                })
            }
        } else {
            globalModifiers.add(dispatcher.uuid, IdentifiedModifier(identifiers.uuid) {
                config.getDoubleFromExpression("multiplier", dispatcher.get())
            })
        }
    }

    override fun onDisable(dispatcher: Dispatcher<*>, identifiers: Identifiers, holder: ProvidedHolder) {
        globalModifiers.removeIf(dispatcher.uuid) { it.uuid == identifiers.uuid }

        for (element in getAllElements()) {
            modifiers[dispatcher.uuid]?.removeIf(element) { it.uuid == identifiers.uuid }
        }
        if (modifiers[dispatcher.uuid]?.size == 0) modifiers.remove(dispatcher.uuid)
    }

    protected fun getMultiplier(dispatcher: Dispatcher<*>, element: T): Double {
        var multiplier = 1.0

        for (modifier in globalModifiers[dispatcher.uuid]) {
            multiplier *= modifier.modifier
        }

        for (modifier in modifiers[dispatcher.uuid]?.get(element) ?: emptyList()) {
            multiplier *= modifier.modifier
        }

        return multiplier
    }

    /**
     * Get an element by [key], for example a stat.
     */
    abstract fun getElement(key: String): T?

    /**
     * Get all elements.
     */
    abstract fun getAllElements(): Collection<T>
}
