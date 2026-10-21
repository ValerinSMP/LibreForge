package com.willfp.libreforge.effects.orbitals

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.config.ConfigType
import com.willfp.eco.core.config.ExtendableConfig

/** Native updater owns the file; profile names and fields belong to configuration authors. */
internal class OrbitalProfilesYml(plugin: EcoPlugin) : ExtendableConfig(
    "orbital-shields", true, plugin, plugin.javaClass, "", ConfigType.YAML, "profiles"
)
