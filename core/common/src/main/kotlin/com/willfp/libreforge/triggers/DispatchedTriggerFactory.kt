package com.willfp.libreforge.triggers

import com.willfp.eco.core.EcoPlugin
import com.willfp.libreforge.Dispatcher

/*

Prevents multiple identical triggers from being triggered in the same tick.

 */

class DispatchedTriggerFactory(
    private val plugin: EcoPlugin
) {
    // Written by triggers on any thread.
    private val dispatcherTriggers = TickTriggerDeduplication()

    fun create(
        dispatcher: Dispatcher<*>,
        trigger: Trigger,
        data: TriggerData,
        allowDuplicates: Boolean = false
    ): DispatchedTrigger? {
        if (!trigger.isEnabled) {
            return null
        }

        // Some triggers legitimately happen several times in the same tick with identical data,
        // for example killing an entire stack of entities at once.
        if (!allowDuplicates) {
            val hash = (trigger.hashCode() shl 5) xor data.hashCode()
            if (!dispatcherTriggers.accept(dispatcher.uuid, hash)) {
                return null
            }
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
