# AlcatrazCoreOptimizer ⚡

[![Paper Version](https://img.shields.io/badge/Paper-1.21.1-blue.svg)](https://papermc.io)
[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org)
[![Version](https://img.shields.io/badge/Version-1.1.0-emerald.svg)](https://github.com/maryMaryMar/AlcatrazCoreOptimizer)
[![Build](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](#-licencia)

> **Suite Core Todo-en-Uno de Alto Rendimiento, Protecciones de Terreno en PDC, Utilidades Avanzadas y Optimización de Memoria para Paper 1.21.x (Java 21).**  
> Diseñado e implementado a medida para el servidor **PipónMC**.

---

## 📖 Índice General
- [Descripción General](#-descripción-general)
- [Arquitectura Modular (27 Módulos)](#-arquitectura-modular-27-módulos-activos)
- [Historial Completo de Versiones (v1.0.0 a v1.1.0)](#-historial-completo-de-versiones)
- [Auditoría Forense: 18 Errores Corregidos por Módulo](#-auditoría-forense-18-errores-y-vulnerabilidades-corregidas)
- [Comandos y Permisos Principales](#-comandos-y-permisos-principales)
- [Compilación y Despliegue](#-compilación-y-despliegue)
- [Licencia](#-licencia)

---

## 📖 Descripción General

**AlcatrazCoreOptimizer** es un núcleo modular diseñado para resolver simultáneamente múltiples necesidades críticas de jugabilidad, seguridad de inventarios y rendimiento de servidor sin sobrecargar el hilo principal de Paper:

- **🛡️ Sistema de Menas de Protección:** Protecciones de terreno cilíndricas/cúbicas completas de bedrock a cielo (30 a 300 bloques de radio) guardadas de forma persistente y nativa con `PersistentDataContainer` (PDC). Modalidades permanente y temporal (1 semana).
- **🔒 Candados Privados (Chest & Door Lock):** Bloqueo seguro para cofres simples, cofres dobles, barriles, shulkers y puertas con carteles `[Privado]`, protegido contra tolvas, vagonetas y explosiones adyacentes.
- **🪄 Varitas de Venta Atómicas (SellWands):** Sistema de venta instantánea vinculado con la economía de **Vault**, garantizando atomicidad transaccional (cero duplicaciones o pérdida de ítems).
- **🔮 Esfera Cuántica (Mob Catcher):** Captura y liberación segura de criaturas, aldeanos y mascotas manteniendo NBTs, niveles y profesiones intactos.
- **⚔️ Anti-CombatLog Inteligente:** Etiquetado estricto en combate PvP, bloqueo de comandos de evasión (`/spawn`, `/tpa`, etc.), bloqueo de portales e intercepción de teletransportes de terceros.
- **📦 Bóvedas Privadas Infinitas (Player Vaults):** Almacenamiento virtual multi-página sincronizado entre archivos YAML locales y PDC como espejo redundante.
- **⚡ Suite de Optimización Antilag:** Congelamiento de IA de aldeanos en salas de tradeo, desacelerador y agrupación de TNT, stacker de ítems/XP y monitor de rendimiento en tiempo real (TPS/MSPT) libre de pausas *Stop-The-World*.
- **🌐 Detección y Aislamiento de Bedrock (Geyser/Floodgate):** Detección instantánea por UUID (MSB == 0) y reflexión cacheada en memoria para evitar congelamientos en el login y garantizar compatibilidad con paquetes de recursos.

---

## 🏛️ Arquitectura Modular (27 Módulos Activos)

A partir de la versión **v1.1.0**, la arquitectura fue completamente saneada, desacoplada y blindada:
- **🧹 Módulos Retirados y Purgados (0 dependencias residuales):**
  1. `sellchest` — Redundante con la economía de cofres de Vault y alto consumo de ticks.
  2. `optimizer/hoppers` — Conflictos con el optimizador nativo de tolvas de Paper 1.21.1.
  3. `optimizer/pregen` — Sustituido por herramientas especializadas de servidor (Chunky).
  4. `achat` — Reemplazado por chat nativo y `ChatGroupManager`.
  5. `autosmelt` — Removido para prevenir duplicación con encantamientos personalizados de ACE.

- **🛡️ 27 Módulos Activos en Producción:**
  1. `protection` — Protección de reclamos por menas de protección (radios 30, 50, 100, 150, 300).
  2. `chestlock` — Candados de seguridad para cofres y puertas privadas.
  3. `sellwand` — Varitas de venta configurables con multiplicadores y usos.
  4. `mobcatcher` — Capturador de mobs y aldeanos en esferas cuánticas.
  5. `combatlog` — Prevención de desconexión cobarde y evasión de combate.
  6. `playervaults` — Bóvedas virtuales individuales por número (`/pv <num>`).
  7. `sonicblade` — Arma legendaria Hoja Sónica con raytracing colisionable.
  8. `netheriteelytra` — Pechera de inframundo fusionada con élitras y desgaste dinámico.
  9. `lifemending` — Reparación de armaduras a cambio de vitalidad y saturación.
  10. `mounts` — Monturas personalizadas con física y montura guiada.
  11. `bossdefense` — Mecánicas de defensa contra jefes y reducción de daño crítico.
  12. `chatbubbles` — Bocadillos de chat mediante `TextDisplay` volátil (no serializable en chunks).
  13. `resourcepack` — Gestión y entrega inteligente de texturas Java sin pantallas repetitivas de recarga.
  14. `bedrock` — Aislamiento y detección de clientes Bedrock/Geyser por UUID de Floodgate.
  15. `duels` — Menús interactivos de selección y desafíos de duelos PvP.
  16. `halloween` — Siembra determinista y recolección protegida de cabezas malditas.
  17. `antiflood` — Filtro contra saturación de mensajes en chat.
  18. `chatfilter` — Detección y censura de lenguaje inapropiado y enlaces maliciosos.
  19. `anticaps` — Normalización automática de mensajes en mayúsculas sostenidas.
  20. `antispam` — Cooldown inteligente entre mensajes consecutivos.
  21. `antixray` — Detección y alerta heurística de patrones de minería no natural.
  22. `grapplinghook` — Gancho de agarre con impulso vectorial y amortiguación de caída con TTL.
  23. `elevator` — Ascensores instantáneos entre bloques del mismo tipo en el eje Y.
  24. `voidspawn` — Rescate automático y teletransporte seguro al caer al vacío.
  25. `newbieprotection` — Inmunidad temporal a PvP para jugadores nuevos.
  26. `villagers` — Optimizador de IA y pathfinding para aldeanos encapsulados.
  27. `tnt` — Limitador de densidad de explosiones concurrentes por chunk.
  28. `monitor` — Monitor en tiempo real de métricas Paper (TPS, MSPT, memoria).

---

## 📜 Historial Completo de Versiones

### 🌟 [v1.1.0] - 16 de Octubre de 2026 *(Gran Remasterización y Blindaje Zero-Bugs)*
- **Purga de Código:** Eliminación completa de 5 módulos obsoletos (`sellchest`, `hoppers`, `pregen`, `achat`, `autosmelt`).
- **Remasterización Paper 1.21.1 & Java 21:** Adaptación a la API moderna de Paper, eliminación de llamadas asíncronas no seguras a bloques y soporte completo para records y switch patterns de Java 21.
- **Blindaje Forense:** Resolución integral de 18 fallos críticos de concurrencia, corrupción de datos, duplicaciones económicas y pérdidas de rendimiento.
- **Documentación Completa:** Publicación del informe técnico forense y arquitectura unificada.

### 🏷️ [v1.0.9] - 04 de Octubre de 2026
- **Menas Temporales:** Incorporación de modalidad temporal de 7 días con fecha de expiración guardada en milisegundos en PDC.
- **Candados de Bloques:** Introducción de bloqueo privado para cofres, hornos y puertas mediante carteles `[Privado]`.
- **SellWand:** Corrección de lectura de precios de ítems en archivos de configuración YAML.

### 🏷️ [v1.0.8] - 28 de Septiembre de 2026
- **Halloween:** Blindaje de intercambios comerciales con el NPC Mercader de Halloween.
- **Trade-Lock de Aldeanos:** Parche de compatibilidad para evitar congelamiento indebido de aldeanos zombificados en fase de curación.

### 🏷️ [v1.0.7] - 27 de Septiembre de 2026
- **Siembra Segura de Cabezas:** Algoritmo de dispersión asíncrono para ubicar bloques de apoyo válidos (evitando agua, lava o vacío en chunks no generados).

### 🏷️ [v1.0.6] - 21 de Septiembre de 2026
- **Concurrencia en EnumMap:** Solución a `NullPointerException` y colisiones de iteración concurrente en mapas de banderas de reclamos.
- **Cooldowns:** Implementación de persistencia volátil en memoria para evitar bypass mediante cambio rápido de slots en la hotbar.

### 🏷️ [v1.0.5] - 20 de Septiembre de 2026
- **ResourcePack Unificado:** Arquitectura dual para servir texturas a clientes Java Edition sin enviar paquetes erróneos a clientes de Bedrock.

### 🏷️ [v1.0.4] - 12 de Septiembre de 2026
- **Integración Geyser/Floodgate:** Primera versión del detector de Bedrock mediante verificación de MSB en UUIDs de Floodgate.

### 🏷️ [v1.0.3] - 09 de Septiembre de 2026
- **Módulo BossDefense:** Mitigación de daño masivo y control de agro en criaturas modificadas por MythicMobs.

### 🏷️ [v1.0.2] - 07 de Septiembre de 2026
- **AuraSkills 5.0:** Ajuste de eventos de recolección para evitar duplicación de experiencia en habilidades de minería dentro de zonas protegidas.

### 🏷️ [v1.0.1] - 06 de Septiembre de 2026
- **Optimizador de Chunks de Paso Rápido:** Descarga forzada de chunks transitados rápidamente con élitras para preservar memoria RAM.
- **Telemetría:** Ajuste en el cálculo de MSPT para servidores Paper 1.21.x.

### 🏷️ [v1.0.0] - 01 de Septiembre de 2026
- **Lanzamiento Inicial del Núcleo Unificado:** Consolidación de sistemas de protección por menas, bóvedas virtuales, chat bubbles y armas personalizadas en un único plugin monolítico de alto rendimiento.

---

## 🛡️ Auditoría Forense: 18 Errores y Vulnerabilidades Corregidas

A continuación se detalla la matriz forense completa de los 18 errores críticos subsanados en los módulos durante la actualización **v1.1.0**:

| ID | Módulo / Archivo | Causa Raíz del Error | Vector de Riesgo Evitado | Solución Técnica Implementada |
|:--:|---|---|---|---|
| **01** | `VillagerOptimizer.java` | Invocación de `Bukkit.getScheduler().runTask()` durante `onDisable()`. | `IllegalPluginAccessException` abortaba el guardado de PDC y candados al reiniciar. | Ejecución síncrona inmediata si `!plugin.isEnabled()` sin pasar por el scheduler. |
| **02** | `HalloweenCaveSpawner.java` | Modificación de bloques (`setType`, `getState`) dentro del hilo asíncrono de chunks. | `IllegalStateException` y riesgo de corrupción en archivos de región `.mca`. | Encapsulación obligatoria de operaciones de mundo en el Server Main Thread. |
| **03** | `LifeMendingTask.java` | Mutación de durabilidad sobre el clon de `getArmorContents()` sin reinyectarlo. | Jugadores perdían comida/vida continuamente sin que su armadura se reparase. | Reasignación explícita con `setArmorContents()` y cobro condicionado a reparación real. |
| **04** | `ProtectionManager.java` | Invocación síncrona de `world.getChunkAt()` para persistir datos en PDC. | Congelamiento del hilo principal (Spikes > 500ms MSPT) al cargar chunks del disco. | Validación previa con `world.isChunkLoaded()` y eliminación de llamadas redundantes. |
| **05** | `SellWandListener.java` | Verificación de permisos limitada a la mitad del cofre doble clickeada por el jugador. | Robo de ítems en cofres ajenos apuntando a la mitad situada fuera del claim. | Chequeo estricto de permisos en **ambas mitades** del contenedor doble. |
| **06** | `SellWandManager.java` | Vaciado del contenedor antes de confirmar la transacción de depósito con Vault. | Duplicación o pérdida permanente de ítems ante fallos o timeouts de economía. | Operación de venta 100% atómica: vaciado condicionado a `transactionSuccess()`. |
| **07** | `MobCatcherListener.java` | Ausencia de validación de muerte (`isDead()`) y reaparición en centro de bloque. | Duplicación de mobs moribundos y daño inmediato por asfixia al liberarlos. | Validación de `health > 0`, `isValid()` y reaparición en la cara adyacente segura. |
| **08** | `ChestLockListener.java` | Protección limitada al cartel/puerta, ignorando el bloque inferior de soporte. | Bypass de candados picando el bloque de tierra o piedra bajo la puerta/cartel. | Intercepción de eventos de minería y explosiones en bloques de apoyo (`isSupporting`). |
| **09** | `CombatLogListener.java` | Intercepción de comandos pero omisión de `PlayerTeleportEvent`. | Evasión cobarde de PvP mediante Chorus Fruit o invocaciones de terceros plugins. | Listener `HIGHEST` en `PlayerTeleportEvent` cancelando escapes no permitidos. |
| **10** | `SonicBladeListener.java` | Raytracing de entidades sin verificación previa de colisiones sólidas de terreno. | El rayo sónico atravesaba paredes de obsidiana matando jugadores en búnkeres. | Raytracing de bloques con `FluidCollisionMode.NEVER` y chequeo de flags de claim. |
| **11** | `ProtectionListener.java` | Chequeo de TNT limitado al epicentro de detonación y radio central en WorldGuard. | TNT colocada al borde exterior destruía el interior de reclamos vecinos. | Filtrado bloque por bloque (`blockList().removeIf`) e inspección en 4 esquinas. |
| **12** | `HalloweenListener.java` | Ejecución de `removeHead(loc)` sin validación atómica en clics concurrentes. | Duplicación de saldo y tokens por clic simultáneo o macros de usuarios. | Chequeo de no-nulidad en el retorno atómico de remoción de cabeza. |
| **13** | `BedrockDetector.java` | Métodos de Floodgate y Geyser declarados como `synchronized`. | Contención severa de hilos en logins masivos provocando timeouts de red. | Inicialización estática cacheada de métodos de reflexión, operación 100% lock-free. |
| **14** | `VoidSpawnListener.java` | Ejecución de partículas y pociones en el pool asíncrono de chunks de Paper. | Desincronización de paquetes con el cliente y warnings en consola. | Despacho obligatorio con `getMainThreadExecutor(plugin)` al terminar el teletransporte. |
| **15** | `ChatBubbleManager.java` | Generación de entidades `TextDisplay` sin marcar persistencia como falsa. | Contaminación de archivos `.mca` con entidades invisibles tras caídas o apagados. | Llamada explícita a `td.setPersistent(false)` para evitar serialización a disco. |
| **16** | `GrapplingHookManager.java` | UUIDs agregados a un `Set` permanente hasta sufrir daño de caída. | Inmunidad infinita a caída si el jugador caía en agua y saltaba horas después. | Mapa concurrente con TTL estricto de 10 segundos para la protección de caída. |
| **17** | `PerformanceMonitor.java` | Invocación explícita de `System.gc()` dentro del comando de limpieza. | Paradas masivas (*Stop-The-World*) congelando el servidor durante cientos de ms. | Retiro de `System.gc()`, delegando la gestión natural en los recolectores de Java 21. |
| **18** | `SellWandManager.java` | Llamada a método auxiliar `color()` inexistente en tiempo de compilación. | Error de compilación en Maven al reportar mensajes de error de transacciones. | Implementación del helper `color()` con `ChatColor.translateAlternateColorCodes`. |

---

## 🎮 Comandos y Permisos Principales

### 🛡️ Protecciones (`/ps` o `/claim`)
| Comando | Permiso | Descripción |
|---|---|---|
| `/ps trust <jugador>` | `alcatraz.protection.trust` | Otorga confianza a un amigo en la protección. |
| `/ps untrust <jugador>` | `alcatraz.protection.untrust` | Revoca la confianza a un jugador. |
| `/ps info` o `/ps view` | `alcatraz.protection.info` | Muestra los datos del reclamo y activa el borde de partículas. |
| `/ps flags` | `alcatraz.protection.flags` | Lista los permisos y estados configurables del terreno. |
| `/ps flag <flag> <allow\|deny>` | `alcatraz.protection.flags.edit` | Cambia banderas (PVP, EXPLOSIONS, INTERACT, etc.). |
| `/ps list` | `alcatraz.protection.list` | Lista tus protecciones activas y el tiempo restante. |
| `/ps give <jugador> <mena> [tipo] [cant]` | `alcatraz.admin` | Entrega menas de protección personalizadas. |

### 📦 Bóvedas Virtuales (`/pv`)
| Comando | Permiso | Descripción |
|---|---|---|
| `/pv <número>` | `alcatraz.pv.use` | Abre tu bóveda privada número N. |
| `/pv <jugador> <número>` | `alcatraz.pv.admin` | Inspecciona o modifica la bóveda de otro jugador. |

### ⚔️ Combate, Utilidades e Ítems Especiales
| Comando | Permiso | Descripción |
|---|---|---|
| `/sellwand [give\|list\|reload]` | `alcatraz.sellwand.admin` | Gestión y entrega de varitas de venta. |
| `/mobcatcher [give]` | `alcatraz.mobcatcher.admin` | Entrega de esferas cuánticas de captura. |
| `/hojasonica [give]` | `alcatraz.admin` | Otorga la Hoja Sónica legendaria del Warden. |
| `/pechera [give]` | `alcatraz.admin` | Entrega la Pechera con Élitra de Netherite. |
| `/lifemending [give]` | `alcatraz.admin` | Entrega el libro de encantamiento Life Mending. |
| `/grapplinghook [give]` | `alcatraz.admin` | Entrega el gancho de agarre con usos configurables. |
| `/trash` o `/basurero` | `alcatraz.trash.use` | Abre la papelera virtual para incinerar ítems. |
| `/alcatraz monitor` | `alcatraz.admin` | Panel de telemetría de TPS, MSPT y RAM en vivo. |
| `/alcatraz clean` | `alcatraz.admin` | Limpieza controlada de ítems viejos en suelo y flechas. |

---

## 🛠️ Compilación y Despliegue

### Requisitos:
- **Java Development Kit (JDK):** Versión 21 o superior.
- **Apache Maven:** Versión 3.9 o superior.
- **Servidor:** Paper 1.21.1 (o forks compatibles como Purpur).

### Compilación limpia:
```bash
# Clonar repositorio
git clone https://github.com/maryMaryMar/AlcatrazCoreOptimizer.git
cd AlcatrazCoreOptimizer

# Compilar JAR listo para producción
mvn clean package
```
El archivo final generado se ubicará en:
`target/AlcatrazCoreOptimizer.jar`

---

## 📄 Licencia

Desarrollado de forma exclusiva y propietaria por **AlcatrazM** para el ecosistema **PipónMC**. Todos los derechos reservados.
