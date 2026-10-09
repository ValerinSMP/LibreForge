package com.willfp.libreforge.conditions

/** Evaluate every condition before running any failure callback, without a success-path list. */
internal inline fun <T> evaluateConditionsAndTriggerFailures(
    conditions: Iterable<T>,
    isMet: (T) -> Boolean,
    onFailure: (T) -> Unit
): Boolean {
    var failed: MutableList<T>? = null
    for (condition in conditions) {
        if (!isMet(condition)) {
            val failures = failed ?: mutableListOf<T>().also { failed = it }
            failures.add(condition)
        }
    }
    val failures = failed ?: return true
    for (condition in failures) onFailure(condition)
    return false
}
