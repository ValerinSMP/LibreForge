# Piloto orbital — 2026-10-21

Implementado: efecto nativo `orbital_shields` de LibreForge, compartido por EcoItems/EcoEnchants.
Un bastón en mainhand: inicio de Shift, 3 cargas, duración 8 s, cooldown 20 s desde invocación.
Argumentos: profile, amount, radius, duration, proc_cooldown. Valores editables en YAML.
Perfil visual separado; ItemDisplay, partículas y sonidos vanilla. Una tarea compartida mientras hay instancias.
Límites: 64 instancias, 6 escudos/instancia, duración 30 s, cooldown 120 s, 8 perfiles, 2048 cooldowns retenidos.
Sin costes, SQL, XP, pagos, recompensas ni cambios en vEnchanting/vPets.

Cobertura: melee directo/proyectil con atacante vivo elegible; respeta cancelación, PvP y aliados.
Excluye daño ambiental, periódico, área/sweep, explosiones y efectos sintéticos nativos.
Reserva una carga al poner daño en cero; confirma al siguiente tick, devuelve ante cancelación posterior.
No cancela ni fabrica eventos, no cura. Un listener externo que sobrescriba el cero puede impedir el bloqueo;
la reserva se devuelve. La interacción real con el conjunto de plugins requiere prueba en Dev.
Retira displays al cambiar objeto, salir, morir, cambiar mundo o resetear/desactivar proveedor.
Cooldown conservado en memoria del nodo ante cierre/reconexión/reset del proveedor;
no persiste ante reinicio del proceso, sustitución del classloader de LibreForge ni transferencia entre nodos.

Compilado exclusivamente en personal con ejecutor limitado y toolchain existente.
Pruebas JVM: 10 orbitales + 6 AdditiveDamageGroups, 16 correctas, 0 fallos/omitidas.
Incluyen reserva única, cancelación tardía, cooldown, limpieza simulada y finalizador nativo antes/después.
Los eventos/displays son simulados: no constituye ejecución real del dispatcher Paper ni prueba en juego.
ABI pública/protegida preservada; closure/bootstrap y referencias de clases añadidas verificados.
JAR compuesto sobre Dev SHA256 1035c2610a8dd91c4fd6425a809ed2f64d0369c2c9c8b100bebcbc22082c2368.
Solo registro, hooks de cierre y clases orbitales: recursos y contadores existentes idénticos byte a byte.
Runtime: LibreForge-2026.27-orbitals-pilot.jar
SHA256: 627c9cf88bb3e4b44f8cd12285c88205696d9320da680d1799acad119d6724ee

Integración por chat vEnchanting, únicamente Dev Custom/Sync personal:
- Reemplazar el JAR LibreForge coordinadamente; no requiere recompilar EcoItems ni EcoEnchants.
- Fusionar libreforge-profile.yml en plugins/libreforge/config.yml, sin reemplazar el archivo completo.
- Instalar baston_orbital.yml en plugins/EcoItems/items/pilotos/baston_orbital.yml.
- Consola: `ecoitems give Mtynnn baston_orbital 1` (cambiar jugador si corresponde).
- enchant-reference.txt muestra la referencia nativa; no registra un segundo enchant activo.
  Para usarlo desde EcoEnchants se requiere integrar el fix policy-wrapper d1d2ee69 si sigue pendiente.
Pendiente Dev: órbita/sonidos; tres golpes/proyectiles; impacto cancelado sin consumo; duración;
cambio de objeto/mundo/reconexión; PvP/protecciones y ausencia de daño ambiental bloqueado.
No hay medición de rendimiento ni aceptación en juego. No instalado, no activo; sin lifecycle en ningún servidor.
