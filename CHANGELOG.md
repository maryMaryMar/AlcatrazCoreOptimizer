# Historial de Versiones y Registro Forense de Corrección de Errores (CHANGELOG) 📜

Este documento registra la evolución cronológica, historial de modificaciones y auditorías técnicas de **AlcatrazCoreOptimizer**, detallando el significado técnico, vector de riesgo y solución implementada para cada bug corregido desde su concepción.

---

## 📌 Índice Cronológico
- [v1.1.0 (16 de Octubre de 2026)](#versión-110---16-de-octubre-de-2026) — *Gran Remasterización Forense, Purga Modular y Blindaje Zero-Bugs*
- [v1.0.9 (04 de Octubre de 2026)](#versión-109---04-de-octubre-de-2026) — *Protecciones Temporales y Candados de Cofres*
- [v1.0.8 (28 de Septiembre de 2026)](#versión-108---28-de-septiembre-de-2026) — *Parches de Siembra Halloween y Trades de Aldeanos*
- [v1.0.7 (27 de Septiembre de 2026)](#versión-107---27-de-septiembre-de-2026) — *Dispersión Determinista en Cuevas y Superficie*
- [v1.0.6 (21 de Septiembre de 2026)](#versión-106---21-de-septiembre-de-2026) — *Corrección de Concurrencia en EnumMap y Cooldowns*
- [v1.0.5 (20 de Septiembre de 2026)](#versión-105---20-de-septiembre-de-2026) — *Unificación de Paquetes de Recursos Java y Bedrock*
- [v1.0.4 (12 de Septiembre de 2026)](#versión-104---12-de-septiembre-de-2026) — *Aislamiento de Clientes Móviles Geyser/Floodgate*
- [v1.0.3 (09 de Septiembre de 2026)](#versión-103---09-de-septiembre-de-2026) — *Soporte para Módulo BossDefense*
- [v1.0.2 (07 de Septiembre de 2026)](#versión-102---07-de-septiembre-de-2026) — *Compatibilidad con AuraSkills 5.0*
- [v1.0.1 (06 de Septiembre de 2026)](#versión-101---06-de-septiembre-de-2026) — *Ajustes de Telemetría y Límites de Bloques*
- [v1.0.0 (01 de Septiembre de 2026)](#versión-100---01-de-septiembre-de-2026) — *Lanzamiento Inicial del Núcleo Unificado*

---

## Versión 1.1.0 - 16 de Octubre de 2026
**Tipo:** Remasterización Mayor y Auditoría Forense Exhaustiva (Clean-Core)  
**Entorno de Compilación:** Paper 1.21.1 / Java 21 / Maven 3.9+  

### 🧹 1. Purgado y Desacoplamiento de Módulos Obsoletos
- **Módulos eliminados:** `sellchest`, `optimizer/hoppers`, `optimizer/pregen`, `achat`, y `autosmelt`.
- **Razón:** Presentaban redundancia con la infraestructura vanilla de Paper 1.21.1, conflictos con la economía de Vault o alto overhead en el hilo principal.
- **Acción:** Eliminación de los archivos fuente, limpieza en `plugin.yml`, `config.yml`, `AlcatrazCommand.java` y desacoplamiento absoluto sin dejar referencias huérfanas en el `AlcatrazPlugin.java`.

---

### 🛡️ 2. Desglose Técnico de los 18 Errores y Vulnerabilidades Corregidas

#### Bug 1: Fallo Catastrófico en Apagado por `IllegalPluginAccessException`
- **Archivo:** `VillagerOptimizer.java`
- **¿Qué significaba?:** Cuando el servidor se detenía o se recargaba (`/reload` o parada controlada), el método `restoreAllVillagers()` intentaba invocar `Bukkit.getScheduler().runTask()` cuando el plugin ya estaba en fase de deshabilitación (`onDisable()`).
- **Vector de Riesgo:** Bukkit arrojaba inmediatamente `IllegalPluginAccessException`. Esta excepción no capturada abortaba de forma prematura la ejecución del resto del ciclo `onDisable()`, impidiendo que los reclamos (`saveClaimPDC()`), candados de cofres y saldo del evento de Halloween se guardasen en disco, perdiendo el progreso reciente de los jugadores.
- **Solución:** Se añadió una comprobación `if (!plugin.isEnabled())`: si el plugin ya está inactivo, la restauración del pathfinding de los aldeanos se ejecuta sincrónicamente en el hilo actual sin pasar por el scheduler.

#### Bug 2: Corrupción de Paleta de Chunks por Acceso Asíncrono en Paper
- **Archivo:** `HalloweenCaveSpawner.java` y `HalloweenManager.java`
- **¿Qué significaba?:** El método `placeHalloweenHead()` y `checkAndFixChunk()` realizaban operaciones de lectura y modificación del mundo (`block.setType`, `block.getState`) dentro del callback `thenAccept()` de una tarea asíncrona (`getChunkAtAsync`).
- **Vector de Riesgo:** Paper 1.21.1 prohíbe terminantemente la modificación de la paleta de bloques de un chunk desde cualquier hilo secundario. Producía `IllegalStateException: Asynchronous block access`, pudiendo corromper permanentemente los archivos de región `.mca` del mundo.
- **Solución:** Se encapsuló la llamada dentro de `Bukkit.getScheduler().runTask(plugin, ...)`, asegurando que la colocación física y el renderizado de la cabeza se ejecuten 100% en el Server Main Thread.

#### Bug 3: Fuga de Salud/Saturación en `LifeMending` sin Reparación Real
- **Archivo:** `LifeMendingTask.java`
- **¿Qué significaba?:** La tarea periódica de reparación obtenía las piezas de armadura usando `player.getInventory().getArmorContents()`, que en Bukkit devuelve un clon del arreglo de ítems. Las modificaciones a la durabilidad (`Damageable.setDamage()`) se hacían sobre el objeto clonado pero **nunca se volvían a inyectar al inventario** con `setArmorContents()`.
- **Vector de Riesgo:** El plugin cobraba constantemente puntos de comida, saturación o vida al jugador en segundo plano mientras llevaba piezas dañadas, pero los ítems en su cuerpo nunca se reparaban realmente, provocando la muerte por inanición o frustración sin beneficio.
- **Solución:** Se reconstruyó el ciclo de reparación para reasignar explícitamente los ítems actualizados con `setArmorContents()`, `setItemInMainHand()` y `setItemInOffHand()`, y se condicionó la deducción de vida/hambre (`payCost()`) únicamente si al menos un ítem fue efectivamente restaurado.

#### Bug 4: Congelamiento de I/O y Carga Forzada de Chunks al Guardar Protecciones
- **Archivo:** `ProtectionManager.java`
- **¿Qué significaba?:** Al persistir los reclamos en el PersistentDataContainer (`saveClaimPDC()`), el código ejecutaba `world.getChunkAt(cx, cz)` para cada reclamo registrado en la base de datos o memoria.
- **Vector de Riesgo:** `world.getChunkAt()` síncrono obliga al motor del mundo a cargar inmediatamente el chunk desde el disco duro a la memoria RAM. Al tener cientos de reclamos distribuidos por todo el mapa, el servidor congelaba el hilo principal durante varios segundos (Spike de MSPT > 500ms) leyendo archivos `.mca`.
- **Solución:** Se introdujo la verificación `world.isChunkLoaded(cx, cz)`. Si el chunk está descargado en ese momento, el guardado en PDC se pospone hasta que el chunk sea naturalmente cargado por un jugador, utilizando mientras tanto el archivo YAML local como fuente de verdad. Además, se eliminó la llamada redundante a `saveClaims()` dentro del getter `getClaimAt()`.

#### Bug 5: Robo de Ítems en Mitad No Protegida de Cofres Dobles
- **Archivo:** `SellWandListener.java`
- **¿Qué significaba?:** Al usar la vara de venta sobre un cofre doble ubicado en el límite exacto de un reclamo o con candado parcial, el plugin solo comprobaba los permisos sobre el bloque específico al que apuntaba el jugador.
- **Vector de Riesgo:** Un jugador malintencionado podía apuntar a la mitad del cofre que quedaba un bloque fuera del terreno protegido o en la parte no registrada en el candado, vendiendo y extrayendo todos los ítems de ambas mitades del cofre ajeno.
- **Solución:** Se extrajo la función `canAccessSingleBlock` y se implementó la resolución automática de cofres dobles (`ChestLockManager.getRelatedDoubleChestPart`), validando que el usuario tenga permisos estrictos en **ambas mitades** del contenedor antes de permitir cualquier operación de venta.

#### Bug 6: Explotación de Duplicación (Dupe) Económica con Vault
- **Archivo:** `SellWandManager.java`
- **¿Qué significaba?:** Al ejecutar `processSale()`, los ítems del inventario eran eliminados (`inv.setItem(slot, null)`) antes de que la transacción de depósito bancario de Vault (`eco.depositPlayer()`) concluyera con éxito.
- **Vector de Riesgo:** Si el plugin de economía sufría un fallo temporal, timeout en base de datos MySQL o el jugador tenía su cuenta congelada, los ítems se destruían para siempre sin otorgar dinero. En escenarios inversos sin chequeo estricto, permitía reintentos donde los ítems se procesaban múltiples veces.
- **Solución:** Se transformó la venta en una transacción puramente atómica: los ítems solo se limpian del cofre si y solo si `resp != null && resp.transactionSuccess()` devuelve verdadero.

#### Bug 7: Dupe de Criaturas Muertas y Asfixia en Liberación
- **Archivo:** `MobCatcherListener.java`
- **¿Qué significaba?:** Al atrapar una entidad, no se validaba si la criatura ya estaba en animación de muerte (`isDead()`) o sin vida (`health <= 0`). Además, al liberarla, reaparecía exactamente en las coordenadas del bloque clickeado.
- **Vector de Riesgo:** Un jugador podía atacar a un mob de alto valor (como aldeanos de tradeo o caballos modificados) y en el mismo tick de su muerte atraparlo, soltando el loot/experiencia normal pero guardando al mismo tiempo una copia viva en la esfera. Al liberar el mob, aparecía dentro de bloques sólidos sufriendo daño inmediato por asfixia.
- **Solución:** Se añadieron validaciones de integridad (`!living.isDead() && living.isValid() && living.getHealth() > 0`) y la reaparición segura en la cara adyacente (`block.getRelative(event.getBlockFace()).getLocation()`).

#### Bug 8: Destrucción Indirecta de Puertas y Carteles Protegidos
- **Archivo:** `ChestLockListener.java`
- **¿Qué significaba?:** Los candados protegían la puerta o el cartel contra rotura directa, pero ignoraban la destrucción del bloque físico de soporte sobre el cual descansaban.
- **Vector de Riesgo:** Cualquier intruso podía picar el bloque de tierra o piedra situado inmediatamente debajo de una puerta de hierro privada o detrás de un cartel `[Privado]`. La física vanilla provocaba que la puerta o cartel se desprendieran como ítem, dejando la entrada o cofre 100% desprotegido.
- **Solución:** Se implementó `isProtectedSupportingBlock(block)`, interceptando eventos de minería y explosiones (`EntityExplodeEvent`, `BlockExplodeEvent`) en los bloques adyacentes de apoyo.

#### Bug 9: Fuga de Combate PvP mediante Portales y Teletransportes de Terceros
- **Archivo:** `CombatLogListener.java`
- **¿Qué significaba?:** Solo se cancelaban comandos directos del jugador en combate, pero se ignoraban los eventos `PlayerTeleportEvent`.
- **Vector de Riesgo:** Los jugadores podían escapar del combate activo consumiendo Chorus Fruit, activando perlas de terceros o recibiendo invocaciones automáticas de otros plugins.
- **Solución:** Se agregó un listener con prioridad `HIGHEST` para `PlayerTeleportEvent` que cancela cualquier teletransporte originado por comandos, frutas de coral o plugins externos mientras dure el estado de combate.

#### Bug 10: Disparo de Hoja Sónica Atravesando Búnkeres y Reclamos
- **Archivo:** `SonicBladeListener.java`
- **¿Qué significaba?:** El cálculo de impacto utilizaba `rayTraceEntities` sin calcular previamente las colisiones sólidas del terreno.
- **Vector de Riesgo:** El rayo sónico penetraba paredes de obsidiana o bases subterráneas impermeables, matando jugadores a través de estructuras sólidas y burlando las protecciones de daño a aldeanos o animales pacíficos en claims ajenos.
- **Solución:** Se implementó `rayTraceBlocks` con `FluidCollisionMode.NEVER` para limitar el rango de búsqueda a la distancia del primer obstáculo sólido, y se añadieron verificaciones rigurosas de las banderas `PVP`, `VILLAGER_DAMAGE` y `MOB_DAMAGE` antes de infligir daño.

#### Bug 11: Griefing de Perímetro con TNT y Falso Bypass de WorldGuard
- **Archivo:** `ProtectionListener.java`
- **¿Qué significaba?:** En las explosiones de TNT/Creepers, solo se evaluaba si el centro de la detonación estaba dentro de un reclamo. Al colocar protecciones, solo se chequeaba el bloque central con WorldGuard.
- **Vector de Riesgo:** Un usuario podía colocar TNT a 1 bloque del borde exterior del reclamo de su vecino: la explosión se originaba fuera, pero destruía todos los bloques del interior. Al colocar menas, podían abarcar zonas de Spawn o Warzone de WorldGuard con solo poner el bloque central fuera del borde.
- **Solución:** Filtrado determinista bloque por bloque en la lista de explosión (`event.blockList().removeIf(...)`), e inspección de las 4 esquinas del radio de cobertura `(x ± r, z ± r)` frente a WorldGuard antes de permitir activar una piedra de protección.

#### Bug 12: Duplicación de Tokens de Halloween por Clic Simultáneo
- **Archivo:** `HalloweenListener.java`
- **¿Qué significaba?:** El método `collectHalloweenPumpkin()` ejecutaba `manager.removeHead(loc)` pero no verificaba su resultado atómico.
- **Vector de Riesgo:** Si dos jugadores interactuaban en el mismo tick con la misma cabeza o un jugador usaba macro-click rompiendo e interactuando simultáneamente, ambos eventos se disparaban antes de que el bloque pasara a aire, otorgando saldo y tokens dobles.
- **Solución:** El método valida que el valor retornado por `removeHead` no sea nulo. Si ya fue removido por otra acción en ese tick, el evento subsiguiente aborta de inmediato.

#### Bug 13: Bloqueo de Concurrencia (Thread Contention) en Detección de Bedrock
- **Archivo:** `BedrockDetector.java`
- **¿Qué significaba?:** Los métodos de chequeo de Floodgate y Geyser estaban marcados como `public static synchronized`.
- **Vector de Riesgo:** Cuando entraban múltiples usuarios al mismo tiempo, todos los hilos de red y de handshake se bloqueaban mutuamente en el monitor de la clase esperando la resolución por reflexión, causando picos de ping y timeouts de conexión.
- **Solución:** Inicialización idempotente y única de los métodos de reflexión en variables estáticas cacheadas, permitiendo ejecuciones de consulta ultra-rápidas, concurrentes y 100% lock-free.

#### Bug 14: Invocación de API de Paper Fuera del Hilo Principal en `VoidSpawn`
- **Archivo:** `VoidSpawnListener.java`
- **¿Qué significaba?:** Tras ejecutar `player.teleportAsync()`, la promesa `.thenAccept()` ejecutaba efectos de pociones, sonido y partículas directamente en el hilo de finalización de carga de chunks.
- **Vector de Riesgo:** Paper 1.21.1 puede resolver promesas asíncronas en su Thread Pool interno de chunks. Modificar entidades o spawnear partículas desde allí arrojaba errores en consola y generaba desincronización de paquetes con el cliente.
- **Solución:** Uso de `.thenAcceptAsync(..., Bukkit.getScheduler().getMainThreadExecutor(plugin))`, forzando la ejecución de los efectos en el Main Thread.

#### Bug 15: Contaminación de Chunks `.mca` por Entidades `TextDisplay` Huérfanas
- **Archivo:** `ChatBubbleManager.java`
- **¿Qué significaba?:** Los globos de chat generaban entidades `TextDisplay` sin marcar su persistencia.
- **Vector de Riesgo:** Si el servidor se apagaba inesperadamente o el chunk se descargaba mientras un bocadillo de chat estaba activo, la entidad se guardaba de forma definitiva en el archivo `.mca` del chunk, acumulando cientos de entidades fantasma invisibles a lo largo del tiempo.
- **Solución:** Llamada explícita a `td.setPersistent(false)`, garantizando que el motor de Minecraft nunca serialice estas entidades al disco.

#### Bug 16: Retención Infinita de Inmunidad a Caídas con Gancho de Agarre
- **Archivo:** `GrapplingHookManager.java`
- **¿Qué significaba?:** Al usar el gancho, el UUID del jugador se agregaba a un `Set` permanente hasta que recibiera daño de caída.
- **Vector de Riesgo:** Si el jugador usaba el gancho pero caía en agua o telarañas (sin recibir daño), la protección quedaba activa indefinidamente. Horas después, podía saltar de montañas infinitas sin sufrir daño.
- **Solución:** Se sustituyó el Set por un mapa concurrente con TTL estricto de 10 segundos (`fallProtectedUntil`), invalidando la protección de forma automática si expira el tiempo o si el jugador sale del servidor.

#### Bug 17: Paradas Masivas (*Stop-The-World*) por Invocación de `System.gc()`
- **Archivo:** `PerformanceMonitor.java`
- **¿Qué significaba?:** El comando de optimización `/alcatraz clean` ejecutaba directamente `System.gc()`.
- **Vector de Riesgo:** Forzaba a la máquina virtual Java (JVM) a suspender todos los hilos del servidor para realizar una recolección completa de memoria, congelando el juego durante cientos de milisegundos y arruinando la experiencia PvP de los usuarios en línea.
- **Solución:** Se eliminó la llamada forzada a `System.gc()`, sustituyéndola por una lectura pasiva de memoria utilizada y máxima, permitiendo que los recolectores nativos de Java 21 (ZGC o G1GC) gestionen la memoria de forma fluida y concurrente.

#### Bug 18: Error de Compilación por Método de Formato Ausente
- **Archivo:** `SellWandManager.java`
- **¿Qué significaba?:** Al notificar fallos en transacciones bancarias, se intentaba invocar `color()`, método inexistente en dicha clase.
- **Solución:** Se implementó el helper `color(String s)` basado en `ChatColor.translateAlternateColorCodes('&', s)`.

---

## Versión 1.0.9 - 04 de Octubre de 2026
- **Menas Temporales de Protección:** Incorporación de modalidad temporal de 7 días con fecha de expiración guardada en milisegundos en PDC.
- **Candados de Bloques:** Introducción de bloqueo privado para cofres, hornos y puertas mediante carteles `[Privado]`.
- **SellWand:** Corrección de lectura de precios de ítems en archivos de configuración YAML.

---

## Versión 1.0.8 - 28 de Septiembre de 2026
- **Halloween:** Blindaje de intercambios comerciales con el NPC Mercader de Halloween.
- **Trade-Lock de Aldeanos:** Parche de compatibilidad para evitar congelamiento indebido de aldeanos zombificados en fase de curación.

---

## Versión 1.0.7 - 27 de Septiembre de 2026
- **Siembra Segura de Cabezas:** Algoritmo de dispersión asíncrono para ubicar bloques de apoyo válidos (evitando agua, lava o vacío en chunks no generados).

---

## Versión 1.0.6 - 21 de Septiembre de 2026
- **Concurrencia en EnumMap:** Solución a `NullPointerException` y colisiones de iteración concurrente en mapas de banderas de reclamos.
- **Cooldowns:** Implementación de persistencia volátil en memoria para evitar bypass mediante cambio rápido de slots en la hotbar.

---

## Versión 1.0.5 - 20 de Septiembre de 2026
- **ResourcePack Unificado:** Arquitectura dual para servir texturas a clientes Java Edition sin enviar paquetes erróneos a clientes de Bedrock.

---

## Versión 1.0.4 - 12 de Septiembre de 2026
- **Integración Geyser/Floodgate:** Primera versión del detector de Bedrock mediante verificación de MSB en UUIDs de Floodgate.

---

## Versión 1.0.3 - 09 de Septiembre de 2026
- **Módulo BossDefense:** Mitigación de daño masivo y control de agro en criaturas modificadas por MythicMobs.

---

## Versión 1.0.2 - 07 de Septiembre de 2026
- **AuraSkills 5.0:** Ajuste de eventos de recolección para evitar duplicación de experiencia en habilidades de minería dentro de zonas protegidas.

---

## Versión 1.0.1 - 06 de Septiembre de 2026
- **Optimizador de Chunks de Paso Rápido:** Descarga forzada de chunks transitados rápidamente con élitras para preservar memoria RAM.
- **Telemetría:** Ajuste en el cálculo de MSPT para servidores Paper 1.21.x.

---

## Versión 1.0.0 - 01 de Septiembre de 2026
- **Lanzamiento Inicial del Núcleo Unificado:** Consolidación de sistemas de protección por menas, bóvedas virtuales, chat bubbles y armas personalizadas en un único plugin monolítico de alto rendimiento.
