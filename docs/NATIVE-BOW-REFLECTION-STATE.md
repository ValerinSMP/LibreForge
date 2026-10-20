# Cierre nativo coordinado — 20/10/2026

Cambios sobre native-bursts, sin instalar/lifecycle:
- RapidBows: normalización Paper force0–1 opt-in normalized_force=true (defaultfalse conserva consumidores), identidad del modifier sin acumulación y retirada de buckets vacíos; no cambia crítico ni fuerza declarada del disparo.
- DamageVictim: check_antigrief/prevent_recursive opt-in, ambos defaultfalse; réplica normal con guardia que evita bucles y nueva matemática/procs del emisor. ABI existente conservada.
- ShieldBlock: VALUE es bloqueo real finito del modifier BLOCKING; escudo levantado sin bloqueo no emite callback. Firmas y parámetros anteriores siguen; revisar receptores de shield_block (EcoSuite y otros holders) en Test por este arreglo compartido. No se cambia globalmente daño normal ni costes.
- TriggerKill: EVENT adicional únicamente para el dispatch con EntityDeathByEntityEvent real; force original no se rehace ni obtiene testigo inventado. Consumers existentes conservados; Vampirismo opta por filtro genuino en EcoEnchants. Consultar Mythic/death/drop/XP reales en Test.
-168 JVM/mocks pasan;2 nuevas de fuerza/bloqueo real, regresiones166 conservadas. JAR compuesto remapea Kotlin y mantiene clases ajenas/counter/STATE fuera. Cierre coordinado131clases/5676referencias y3smoke JVM. Sin Paper/MySQL/juego ni benchmark.
- Runtime LibreForge-2026.27-native-consolidated-all.jar SHA256 cc3a4c063e8f286aba82cf7469756d079c18b3ac245bd50e912840516bb25426. Requiere EE e8ca5859…/eco5ca36aa8…/Skills0c936eec… coordinados. Contrato final en EcoEnchants docs/NATIVE-CONSOLIDATED-HANDOFF.md.
- BoundCounter/ContextualAccumulator/STATE/DEVELOPMENT_HANDOFF ajenos se preservan; no publicar ni empaquetar todo el shadowJar. Incidencia PvPManager/inventario no corregida por estos cambios.
