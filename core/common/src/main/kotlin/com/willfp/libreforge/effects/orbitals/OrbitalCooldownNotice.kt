package com.willfp.libreforge.effects.orbitals

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.util.PlayerUtils
import com.willfp.eco.util.StringUtils
import com.willfp.eco.util.formatEco
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/** Optional provider adapter: no compile/runtime dependency on EcoSkills. */
internal object OrbitalCooldownNotice {
    fun send(player: Player, config: Config, seconds: Long) {
        val message=(config.getStringOrNull("cooldown_message") ?: "&7Disponible en &#FFDA29%seconds% &7s")
            .replace("%seconds%", seconds.toString()).formatEco(player,true)
        val duration=config.getInt("cooldown_message_duration",1000).coerceIn(50,10000).toLong()
        val provider=Bukkit.getPluginManager().getPlugin("EcoSkills")?.takeIf { it.isEnabled }
        if(provider != null) {
            try {
                val api=Class.forName("com.willfp.ecoskills.api.EcoSkillsActionBarAPI",true,provider.javaClass.classLoader)
                if(api.getMethod("showTemporaryActionBar",Player::class.java,String::class.java,java.lang.Long.TYPE)
                    .invoke(null,player,message,duration)==true) return
            } catch (_: ReflectiveOperationException) { /* Old/absent adapter: ordinary actionbar fallback. */ }
              catch (_: LinkageError) { /* Optional integration must not prevent activation. */ }
        }
        PlayerUtils.getAudience(player).sendActionBar(StringUtils.toComponent(message))
    }
}
