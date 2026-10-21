# Orbitales: integración de perfiles — 2026-10-21

Corregida la integración sin cambios de mecánicas, API, pagos, XP ni otros plugins.
Fuente canónica: personal /root/development/projects/eco-suite/LibreForge, base 3c40fa86.
Causa: config.yml usa removeUnused; perfiles no declarados en defaults desaparecen.
Solución nativa: OrbitalProfilesYml/ExtendableConfig, removeUnused=true, blacklist `profiles`.
Los perfiles se leen ahora de plugins/libreforge/orbital-shields.yml, raíz `profiles`.
Defaults incluidos en el JAR; el archivo se crea al primer acceso al efecto si falta.
El handler de eco registra el archivo y gestiona sus recargas. No se desactiva limpieza global.
No hay migración automática desde config.yml: integrar el YAML adjunto en la nueva ruta;
si existen perfiles propios, conservarlos/moverlos allí antes de activar el candidato.
No volver a fusionar libreforge-profile.yml del handoff anterior en config.yml.
El bastón existente y los argumentos profile: escudo_piloto permanecen idénticos.

Compilación: personal, ejecutor limitado, toolchain existente; correcta.
Prueba real del actualizador EcoUpdatableConfig del JAR eco instalado, en directorios temporales:
creación desde defaults del candidato; piloto + otro_perfil y campo personalizado conservados;
varios ciclos update; clave obsoleta externa eliminada; última actualización idempotente.
PluginLike/ConfigHandler sustituidos por proxies sin servidor; parser, updater y escritura reales.
Compatibilidad ABI y closure verificadas; no se repitió batería completa de combate.
Runtime compuesto sobre candidato SHA256 627c9cf88bb3e4b44f8cd12285c88205696d9320da680d1799acad119d6724ee.
Solo cambia OrbitalShields.class; añade OrbitalProfilesYml.class y orbital-shields.yml.
Todos los demás entries, incluido config.yml, idénticos byte a byte.
JAR: LibreForge-2026.27-orbitals-config-fix.jar
SHA256: 0daf09baad1120c98d9576d5635c593f0ed594182ecd5a7029aebe2b8f95fa28

Integración pendiente por chat vEnchanting en ambos Dev: respaldar, sustituir LibreForge,
poner orbital-shields.yml en plugins/libreforge/, activar según coordinación y comprobar perfil persistente.
El resto de JAR y bastón no requieren cambios. Luego comprobar órbita, sonidos y bloqueos en juego.
No instalado ni activado desde este chat; no lifecycle. Producción/HomeSV/Test intactos.
