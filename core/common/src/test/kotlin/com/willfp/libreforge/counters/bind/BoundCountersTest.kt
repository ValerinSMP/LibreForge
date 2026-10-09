package com.willfp.libreforge.counters.bind

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.map.listMap
import com.willfp.libreforge.conditions.ConditionList
import com.willfp.libreforge.counters.Accumulator
import com.willfp.libreforge.counters.Counter
import com.willfp.libreforge.effects.arguments.EffectArgumentList
import com.willfp.libreforge.filters.FilterList
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.entity.Player
import java.lang.reflect.Proxy
import kotlin.random.Random
import kotlin.test.*

class BoundCountersTest {
    // Binding never reads configuration or calls Bukkit. Fail if that contract changes.
    private val config = Proxy.newProxyInstance(
        Config::class.java.classLoader, arrayOf(Config::class.java)
    ) { _, method, _ -> error("Unexpected config access: ${method.name}") } as Config

    private fun trigger(id: String) = object : Trigger(id) {
        override val parameters = emptySet<TriggerParameter>()
    }

    private fun counter(trigger: Trigger) = Counter(
        trigger, ConditionList(emptyList()), FilterList(emptyList()), config,
        EffectArgumentList(emptyList()), null, null
    )

    private fun accumulator() = object : Accumulator {
        override fun accept(player: Player, count: Double) = error("Unexpected accumulation")
    }

    @Test fun `unbound trigger has no counters and lookup does not create bindings`() {
        val mining = trigger("test_empty")
        assertTrue(BoundCounters.bindingsFor(mining).isEmpty())
        assertFalse(BoundCounters.anyCanBeTriggeredBy(mining))
    }

    @Test fun `mining selects mining bindings and preserves repeated accumulators`() {
        val mining = trigger("test_mining")
        val fishing = trigger("test_fishing")
        val mine = counter(mining)
        val fish = counter(fishing)
        val first = accumulator()
        val second = accumulator()
        try {
            mine.bind(first)
            fish.bind(accumulator())
            mine.bind(first)
            mine.bind(second)
            val selected = BoundCounters.bindingsFor(mining)
            assertEquals(listOf(first, first, second), selected.map { it.accumulator })
            assertTrue(selected.all { it.counter === mine })
            assertEquals(listOf(fish), BoundCounters.bindingsFor(fishing).map { it.counter })
        } finally {
            mine.unbind()
            fish.unbind()
        }
    }

    @Test fun `bind and unbind invalidate cached results without changing old snapshots`() {
        val action = trigger("test_cache")
        val count = counter(action)
        val first = accumulator()
        val second = accumulator()
        try {
            assertFalse(BoundCounters.anyCanBeTriggeredBy(action))
            count.bind(first)
            val before = BoundCounters.bindingsFor(action)
            count.bind(second)
            assertEquals(listOf(first, second), BoundCounters.bindingsFor(action).map { it.accumulator })
            assertEquals(listOf(first), before.map { it.accumulator })
            count.unbind()
            assertFalse(BoundCounters.anyCanBeTriggeredBy(action))
            assertTrue(BoundCounters.bindingsFor(action).isEmpty())
            count.bind(second)
            assertEquals(listOf(second), BoundCounters.bindingsFor(action).map { it.accumulator })
        } finally {
            count.unbind()
        }
    }

    @Test fun `trigger equality uses id and hash collisions do not mix actions`() {
        val first = trigger("Aa")
        val second = trigger("BB")
        assertEquals(first.hashCode(), second.hashCode())
        val a = counter(first)
        val b = counter(second)
        try {
            a.bind(accumulator())
            b.bind(accumulator())
            assertEquals(listOf(a), BoundCounters.bindingsFor(trigger("Aa")).map { it.counter })
            assertEquals(listOf(b), BoundCounters.bindingsFor(trigger("BB")).map { it.counter })
        } finally {
            a.unbind()
            b.unbind()
        }
    }

    @Test fun `random registrations removals and reloads preserve legacy selection and order`() {
        val random = Random(20261010)
        val triggers = List(5) { trigger("test_random_$it") }
        val counters = List(25) { counter(triggers[it % triggers.size]) }
        val legacy = listMap<Counter, BoundCounter>()
        try {
            repeat(500) {
                val count = counters.random(random)
                if (random.nextInt(4) == 0) {
                    count.unbind()
                    legacy.remove(count)
                } else {
                    val accumulator = accumulator()
                    count.bind(accumulator)
                    legacy[count].add(BoundCounter(count, accumulator))
                }
                for (action in triggers) {
                    val expected = legacy.values.flatten().filter { it.counter.canBeTriggeredBy(action) }
                    assertEquals(expected, BoundCounters.bindingsFor(action))
                    assertEquals(expected.isNotEmpty(), BoundCounters.anyCanBeTriggeredBy(action))
                }
            }
        } finally {
            counters.forEach { it.unbind() }
        }
    }
}
