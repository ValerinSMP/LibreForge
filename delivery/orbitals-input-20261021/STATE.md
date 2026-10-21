# Orbitales: controles y barra temporal — 2026-10-21
Implementados sneak_start (default), left_click, right_click, shift_left_click y shift_right_click.
Config args.activation por efecto; usar trigger toggle_sneak para sneak_start y physical_click para clics.
Los combos son clic mientras se sostiene Shift; no pulso/soltado de Shift. Botones sin inversión por material.
Una emisión por jugador/tick; filtra offhand, interacciones de bloque/entidad denegadas y ataques cancelados.
PlayerInteractEvent usa sus dos Results: permite la cancelación predicha del aire, nunca DENY de uso del objeto.
Sin cambios de significado en alt_click ni controles de otros efectos.
Aviso optativo args.send_cooldown_message=true; args.cooldown_message admite %seconds%.
args.cooldown_message_duration=1000 ms (50..10000); restante monotónico redondeado hacia arriba;
un aviso/s como máximo. OrbitalCharges sigue siendo única autoridad, sin renovación al fallar.
API opcional EcoSkillsActionBarAPI.showTemporaryActionBar(Player,String,long): boolean, MAIN solamente.
Reutiliza blacklist/whitelist y envío compatible; restaura datos actuales al vencer (ticks de 50 ms, sujeto al TPS).
Respeta barra desactivada/permisos/mundo y pausas externas más recientes; no toca la pausa legacy de 2700 ms.
Sin EcoSkills/adaptador: envío actionbar ordinario, con duración/fade nativos del cliente.
Escala 0.70 conservada en defaults y ejemplo; no sustituir perfiles personalizados.

Builds en personal mediante ejecutor limitado. 16 pruebas LibreForge y 1 prueba integral simulada de barra aprobadas.
La prueba de barra cubre salud actual, no replay; aviso no amplía pausa a 2700 ms; pausa externa; barra apagada; hilo.
Pruebas JVM/Mockito, no gameplay ni medición de carga/fluidez. Compatibilidad/closure de candidato comprobadas.
Compuesto sobre los dos JAR Dev: conserva byte a byte APIs legacy, compras y puente XP fuera de actionbar.
Artefactos/hashes: runtime-manifest.json en staging ecosuite-orbitals-input-20261021.
No instalado/activo ni lifecycle. Integración y prueba de los cinco controles/barra pendientes en ambos Dev.
Esta entrega es checkpoint de controles: el nuevo prompt Mythic migra después la representación; no activar ambos candidatos.
