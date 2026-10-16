# AlcatrazCoreOptimizer ⚡

[![Paper Version](https://img.shields.io/badge/Paper-1.21.1-blue.svg)](https://papermc.io)
[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org)
[![Version](https://img.shields.io/badge/Version-1.1.0-emerald.svg)](https://github.com/maryMaryMar/AlcatrazCoreOptimizer)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](#licencia)

> **Suite Core Todo-en-Uno de Alto Rendimiento, Protecciones de Terreno en PDC, Utilidades Avanzadas y Optimización de Memoria para Paper 1.21.x (Java 21).**  
> Diseñado e implementado a medida para el servidor **PipónMC**.

---

## 📖 Descripción General

**AlcatrazCoreOptimizer** es un núcleo modular diseñado para resolver simultáneamente múltiples necesidades críticas de jugabilidad, seguridad de inventarios y rendimiento de servidor sin sobrecargar el hilo principal de Paper:

- **🛡️ Sistema de Menas de Protección:** Protecciones de terreno cilíndricas/cúbicas completas de bedrock a cielo (30 a 300 bloques de radio) guardadas de forma persistente y nativa con `PersistentDataContainer` (PDC). Modalidades permanente y temporal (1 semana).
- **🔒 Candados Privados (Chest & Door Lock):** Bloqueo seguro para cofres simples, cofres dobles, barriles, shulkers y puertas con carteles `[Privado]`, protegido contra tolvas, vagonetas y explosiones.
- **🪄 Varitas de Venta Atómicas (SellWands):** Sistema de venta instantánea vinculado con la economía de **Vault**, garantizando atomicidad transaccional (cero duplicaciones o pérdida de ítems).
- **🔮 Esfera Cuántica (Mob Catcher):** Captura y liberación segura de criaturas, aldeanos y mascotas manteniendo NBTs, niveles y profesiones intactos.
- **⚔️ Anti-CombatLog Inteligente:** Etiquetado estricto en combate PvP, bloqueo de comandos de evasión (`/spawn`, `/tpa`, etc.), bloqueo de portales e intercepción de teletransportes de terceros.
- **📦 Bóvedas Privadas Infinitas (Player Vaults):** Almacenamiento virtual multi-página sincronizado entre archivos YAML locales y PDC como espejo redundante.
- **⚡ Suite de Optimización Antilag:** Congelamiento de IA de aldeanos en salas de tradeo, desacelerador y agrupación de TNT, stacker de ítems/XP y monitor de rendimiento en tiempo real (TPS/MSPT) libre de pausas *Stop-The-World*.
- **🌐 Detección y Aislamiento de Bedrock (Geyser/Floodgate):** Detección instantánea por UUID (MSB == 0) y reflexión cacheada en memoria para evitar congelamientos en el login y garantizar compatibilidad con paquetes de recursos.

Para consultar el registro histórico de bugs corregidos, vectores de riesgo y cronograma de parches, revisa el archivo [**CHANGELOG.md**](CHANGELOG.md).

---

## 🏛️ Arquitectura Modular (27 Módulos Activos)

A partir de la versión **v1.1.0**, la arquitectura fue completamente saneada, desacoplada y blindada:
- **Módulos Retirados y Purgados (0 dependencias residuales):** `sellchest`, `optimizer/hoppers`, `optimizer/pregen`, `achat` y `autosmelt`.
- **Módulos Activos:**
  1. `protection` - Protección de reclamos por menas (radios 30, 50, 100, 150, 300).
  2. `chestlock` - Candados de seguridad para cofres y puertas privadas.
  3. `sellwand` - Varitas de venta configurables con multiplicadores y usos.
  4. `mobcatcher` - Capturador de mobs y aldeanos en esferas cuánticas.
  5. `combatlog` - Prevención de desconexión cobarde y evasión de combate.
  6. `playervaults` - Bóvedas virtuales individuales por número (`/pv <num>`).
  7. `sonicblade` - Arma legendaria Hoja Sónica con raytracing colisionable.
  8. `netheriteelytra` - Pechera de inframundo fusionada con élitras y desgaste dinámico.
  9. `lifemending` - Reparación de armaduras a cambio de vitalidad y saturación.
  10. `mounts` - Monturas personalizadas con física y montura guiada.
  11. `bossdefense` - Mecánicas de defensa contra jefes y reducción de daño crítico.
  12. `chatbubbles` - Bocadillos de chat mediante `TextDisplay` volátil (no serializable en chunks).
  13. `resourcepack` & `bedrock` - Gestión de resource packs y detección de clientes móviles.
  14. `duels` - Menús interactivos de selección y desafíos de duelos PvP.
  15. `halloween` - Siembra determinista y recolección protegida de cabezas malditas.
  16. `antiflood` - Filtro contra saturación de mensajes en chat.
  17. `chatfilter` - Detección y censura de lenguaje inapropiado y enlaces maliciosos.
  18. `anticaps` - Normalización automática de mensajes en mayúsculas sostenidas.
  19. `antispam` - Cooldown inteligente entre mensajes consecutivos.
  20. `antixray` - Detección y alerta heurística de patrones de minería no natural.
  21. `grapplinghook` - Gancho de agarre con impulso vectorial y amortiguación de caída con TTL.
  22. `elevator` - Ascensores instantáneos entre bloques del mismo tipo en el eje Y.
  23. `voidspawn` - Rescate automático y teletransporte seguro al caer al vacío.
  24. `newbieprotection` - Inmunidad temporal a PvP para jugadores nuevos.
  25. `villagers` - Optimizador de IA y pathfinding para aldeanos encapsulados.
  26. `tnt` - Limitador de densidad de explosiones concurrentes por chunk.
  27. `monitor` - Monitor en tiempo real de métricas Paper (TPS, MSPT, memoria).

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

# Compilar JAR listo para producción (sin ofuscación)
mvn clean package
```
El archivo final generado se ubicará en:
`target/AlcatrazCoreOptimizer.jar`

---

## 📄 Licencia

Desarrollado de forma exclusiva y propietaria por **AlcatrazM** para el ecosistema **PipónMC**. Todos los derechos reservados.
