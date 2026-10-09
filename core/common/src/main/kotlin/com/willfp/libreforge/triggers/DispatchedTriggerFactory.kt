package com.willfp.libreforge.triggers

import com.willfp.eco.core.EcoPlugin
import com.willfp.libreforge.Dispatcher

/*

Prevents multiple identical triggers from being triggered in the same tick.

 */

class DispatchedTriggerFactory(
    private val plugin: EcoPlugin
) {
    private val dispatcherTriggers = TickTriggerDeduplication()


    fun create(dispatcher: Dispatcher<*>, trigger: Trigger, data: TriggerData): DispatchedTrigger? {
        if (!trigger.isEnabled) {
            return null
        }

        val hash = (trigger.hashCode() shl 5) xor data.hashCode()
        if (!dispatcherTriggers.accept(dispatcher.uuid, hash)) {
            return null
        }

        val dispatchData = if (data.dispatcher == dispatcher) data else data.copy(dispatcher = dispatcher)
        return DispatchedTrigger(dispatcher, trigger, dispatchData)
    }

    internal fun startTicking() {
        plugin.scheduler.runTimer(1, 1) {
            dispatcherTriggers.clear()
        }
    }
}
