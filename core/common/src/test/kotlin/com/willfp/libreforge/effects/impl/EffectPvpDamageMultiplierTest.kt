package com.willfp.libreforge.effects.impl

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EffectPvpDamageMultiplierTest {
    @Test
    fun `reduces a direct player hit`() {
        assertEquals(8.0, calculatePvpDamage(player(), player(), 10.0, 0.8))
    }

    @Test
    fun `reduces a player projectile hit`() {
        assertEquals(8.0, calculatePvpDamage(projectile(player()), player(), 10.0, 0.8))
    }

    @Test
    fun `preserves non player and self damage`() {
        val player = player()
        assertNull(calculatePvpDamage(livingEntity(), player, 10.0, 0.8))
        assertNull(calculatePvpDamage(player, player, 10.0, 0.8))
    }

    private fun player(id: UUID = UUID.randomUUID()): Player = proxy(Player::class.java) { method ->
        if (method.name == "getUniqueId") id else defaultValue(method.returnType)
    }

    private fun projectile(shooter: Player): Projectile = proxy(Projectile::class.java) { method ->
        if (method.name == "getShooter") shooter else defaultValue(method.returnType)
    }

    private fun livingEntity(): LivingEntity = proxy(LivingEntity::class.java) { method -> defaultValue(method.returnType) }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type: Class<T>, value: (java.lang.reflect.Method) -> Any?): T = Proxy.newProxyInstance(
        type.classLoader,
        arrayOf(type)
    ) { _, method, _ -> value(method) } as T

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Byte::class.javaPrimitiveType -> 0.toByte()
        Short::class.javaPrimitiveType -> 0.toShort()
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0F
        Double::class.javaPrimitiveType -> 0.0
        Char::class.javaPrimitiveType -> '\u0000'
        else -> null
    }
}
