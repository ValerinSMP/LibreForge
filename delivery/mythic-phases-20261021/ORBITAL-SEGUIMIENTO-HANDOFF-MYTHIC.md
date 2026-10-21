# Escudos al moverse: resultado y handoff para Mythic — 2026-10-21

Estado: investigación terminada; corrección runtime pendiente de soporte en Mythic. Sin edición de código, build, instalación ni lifecycle. No hay JAR nuevo ni YAML corregido validado. Fuente EcoSuite personal contrastada con Git; cambios ajenos conservados. Los tres JAR de ambos Dev corresponden exactamente a la entrega LF42113d0f/EE5df80d34/ESe021979. Mythic instalado: 5.13.0-SNAPSHOT-3603534b, SHA256 bfe5a2b57450122e49374fd10509f84252281dc11f0a5d2ea0f8c61db57f6e06.

## Confirmado contra el JAR instalado
- OrbitalTracker.getLocation toma la ubicación actual de su receptor; orbitalMove construye currentLocation con el centro actual y el desplazamiento de órbita. El objetivo @self entregado es el jugador. No encontré un centro congelado en el adaptador.
- DisplayPacketBulletTracker.spawn y tick normalizan getCurrentVelocity y calculan yaw/pitch desde esa velocidad GLOBAL. Un salto o desplazamiento del centro cambia la orientación aunque el escudo deba seguir mirando hacia fuera. Esto acredita la causa de orientación, no la causa exacta del arrastre observado.
- Para OrbitalMechanic el forward offset se fuerza a cero en spawn/tick de DISPLAY. El fix de forward-offset citado por el changelog ya está presente en este JAR: no proponerlo como solución nueva.
- Cada tick llama bullet.setRotation(yaw,pitch), bullet.setPosition(location), bullet.update() y mount.teleport(location). El DISPLAY es pasajero de un PacketArmorStand virtual creado en spawn. La ruta mount/paquetes/interpolación es candidata a investigar para la separación del centro, todavía no una causa demostrada.
- PacketItemDisplay sí tiene setInterpolationDelay/Duration; eso corresponde a interpolación de transformaciones y no acredita control de interpolación posicional del mount. El parser DISPLAY no lee interpolation/teleport-duration ni bulletMatchDirection. No trasladar DisplayOptions de mobs reales a este bullet.
- ProjectileBulletableTracker.getBullet() existe, pero BulletTracker no expone el PacketItemDisplay/mount. En DisplayPacketBulletTracker ambos son protected y no hay setter/hook público para orientación radial, anclaje o configuración de interpolación del recurso ya creado. No usar reflexión ni reimplementar un bullet desde LF.
- rotation/rot establece transformación local; no desactiva el yaw/pitch global que se recalcula cada tick. Billboarding VERTICAL ya falló en la prueba del propietario; FIXED se conserva.

Evidencia completa local en staging/ecosuite-orbital-seguimiento-20261021: bytecode actual de display-bullet, display-tracker, orbital-tracker, packet-display y packet-entity; constructor-strings; hashes y YAML actual. Vídeos/contactos señalados por el prompt revisados mediante contacto; no permiten atribuir por sí solos fallo a cliente/latencia ni a la interpolación.

## Solicitud acotada para el chat dueño de Mythic
Investigar y corregir el DISPLAY nativo del orbital; no modificar EcoSuite, vEnchanting/vPets, daño, compras o XP.
1. Confirmar fuente canónica correspondiente al JAR/hash anterior y preservar cambios ajenos antes de tocar Mythic. No actualizar upstream por rutina.
2. Añadir un modo explícito y optativo de orientación orbital radial (nombre por definir en el contrato de Mythic), compatible con el comportamiento legacy por defecto. En spawn y tick calcular la orientación desde el vector RELATIVO entre posición orbital y centro del receptor, pitch estable para este plano horizontal; no desde getCurrentVelocity global. Reutilizar el desplazamiento que OrbitalTracker ya calcula, sin duplicar geometría en LF. Conservar transformación local del modelo como offset configurable.
3. Para el arrastre, instrumentar de forma acotada coordenadas/tick del receptor, currentLocation, posición enviada al mount y estados de spawn/montaje para propietario y observador. Comparar con captura en tercera persona al caminar, correr, saltar, girar, parar. Determinar si la divergencia aparece antes del envío o en representación/interpolación; no declarar causa solo por leer bytecode.
4. Según esa evidencia, corregir la ruta existente de movimiento del DISPLAY/mount o exponer un ajuste real de anclaje/interpolación. Sin polling paralelo, respawn periódico, velocidad adelantada, desmontar/recrear pasajeros a cada tick ni nuevo motor Java de geometría. No prometer que cambiar interpolationDuration por sí solo elimina el arrastre.
5. Preservar el mismo OrbitalTracker/AutoCloseable por instancia y el callingEvent de onStart: ecosuite_visual_bind{index=0|1|2} debe seguir identificando y cerrando únicamente el escudo consumido. Si cambia el contrato público, documentar exactamente la API para ajustar el adaptador existente de EcoSuite.
6. Entregar parser/opción implementada, JAR/hash y prueba dirigida; no instalar/lifecycle. Después EcoSuite podrá configurar el nuevo modo en las tres skills, nunca antes de que exista.

## Valores y aceptación que se conservan
Orbital DISPLAY @self, interval1, SHIELD, FIXED, escala0.70, radio1.25, offsety1.1; points72 y startingpoint0/24/48; tres escudos8s, cooldown20s. Cargas/daño/protecciones/reservas, atribución y cleanup nativos intactos.
El YAML adjunto ValerinEcoVisuals-current.yml es copia del ACTUAL, SIN CORRECCIÓN y no requiere instalar. Hash ambos Dev: 1f5c50b215f0dbc739debea69975a950af2bcf810c27a4675d836d1e686fd337. No añadir claves propuestas aún inexistentes.
Pendiente aceptación real: tercera persona propio y otro observador, caminar/correr/saltar/girar/parar; centro estable relativo al jugador, caras hacia fuera sin inclinación por salto, tres índices distintos, retirada individual/fin8s, cambio de mano/mundo/muerte/salida y reload sin recursos huérfanos. Sin medidas de rendimiento ni prueba funcional nueva en este incremento.

Fuentes consultadas (no sustituyen el JAR):
https://wiki.mythiccraft.io/mythicmobs/skills/mechanics/projectile
https://wiki.mythiccraft.io/mythicmobs/skills/mechanics/orbital
https://wiki.mythiccraft.io/mythicmobs/Mobs/DisplayOptions
https://issues.mythiccraft.io/mythicmobs/i/6116 (reporte de desync de otros proyectiles; antecedente, no diagnóstico de este caso).
