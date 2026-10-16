package com.alcatrazm.plugin.resourcepack;

import com.alcatrazm.plugin.AlcatrazPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor avanzado de Paquetes de Recursos (Resource Packs) para AlcatrazCoreOptimizer.
 * 
 * Características:
 * 1. Envío a usuarios seleccionados (vía permisos o whitelist configurable).
 * 2. Descarga única ("Send-Only-Once"): Evita que el cliente recargue recursos en cada conexión.
 * 3. Protección total para Bedrock: Detecta jugadores de Geyser/Floodgate para evitar errores,
 *    pantallas de carga infinitas y cierres del juego en Bedrock Edition.
 * 4. Actualización inteligente: Si el admin cambia el SHA-1 en config.yml, se actualiza automáticamente.
 */
public class ResourcePackManager {

    private final AlcatrazPlugin plugin;
    private File dataFile;
    private YamlConfiguration dataConfig;

    // Configuración general
    private boolean enabled;
    private String packUrl;
    private String packHash;
    private byte[] packHashBytes;
    private String promptText;
    private boolean force;
    private String targetMode; // "PERMISSION", "WHITELIST", "ALL"
    private String permission;
    private Set<String> whitelistNames = new HashSet<>();
    private boolean sendOnlyOnce;
    private int delayTicks;
    private boolean resetOnHashChange;
    private boolean kickOnDecline;
    private String declineKickMessage;

    // Configuración Bedrock
    private boolean bedrockBypassJava;
    private String bedrockCustomUrl;
    private String bedrockCustomHash;
    private byte[] bedrockCustomHashBytes;
    private boolean bedrockNotifyInfo;
    private String bedrockInfoMessage;

    // Datos en memoria
    private final Set<UUID> installedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> optedOutPlayers = ConcurrentHashMap.newKeySet();

    public ResourcePackManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        loadData();
    }

    public void reload() {
        loadConfig();
        loadData();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("resource-pack.enabled", true);
        this.packUrl = plugin.getConfig().getString("resource-pack.url", "");
        this.packHash = plugin.getConfig().getString("resource-pack.hash", "").trim().toLowerCase();
        this.packHashBytes = parseHexHash(this.packHash);
        this.promptText = plugin.getConfig().getString("resource-pack.prompt", "&b&lAlcatraz &8» &7Descargando paquete de texturas oficial...");
        this.force = plugin.getConfig().getBoolean("resource-pack.force", false);
        this.targetMode = plugin.getConfig().getString("resource-pack.target-mode", "PERMISSION").toUpperCase();
        this.permission = plugin.getConfig().getString("resource-pack.permission", "alcatraz.pack.receive");
        
        List<String> list = plugin.getConfig().getStringList("resource-pack.whitelist");
        this.whitelistNames = new HashSet<>();
        for (String s : list) {
            this.whitelistNames.add(s.toLowerCase());
        }

        this.sendOnlyOnce = plugin.getConfig().getBoolean("resource-pack.send-only-once", true);
        this.delayTicks = plugin.getConfig().getInt("resource-pack.delay-ticks", 30);
        this.resetOnHashChange = plugin.getConfig().getBoolean("resource-pack.reset-on-hash-change", true);
        this.kickOnDecline = plugin.getConfig().getBoolean("resource-pack.kick-on-decline", false);
        this.declineKickMessage = plugin.getConfig().getString("resource-pack.decline-kick-message", "&cDebes aceptar el paquete de recursos para ingresar al servidor.");

        // Bedrock
        this.bedrockBypassJava = plugin.getConfig().getBoolean("resource-pack.bedrock.bypass-java-pack", true);
        this.bedrockCustomUrl = plugin.getConfig().getString("resource-pack.bedrock.custom-pack-url", "");
        this.bedrockCustomHash = plugin.getConfig().getString("resource-pack.bedrock.custom-pack-hash", "").trim().toLowerCase();
        this.bedrockCustomHashBytes = parseHexHash(this.bedrockCustomHash);
        this.bedrockNotifyInfo = plugin.getConfig().getBoolean("resource-pack.bedrock.notify-pack-info", false);
        this.bedrockInfoMessage = plugin.getConfig().getString("resource-pack.bedrock.info-message", "&e[Alcatraz] &7Texturas de Bedrock optimizadas por Geyser.");
    }

    public void loadData() {
        installedPlayers.clear();
        optedOutPlayers.clear();

        dataFile = new File(plugin.getDataFolder(), "data/resourcepacks.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo crear data/resourcepacks.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        String savedHash = dataConfig.getString("last-hash", "");
        if (resetOnHashChange && !packHash.isEmpty() && !packHash.equalsIgnoreCase(savedHash)) {
            plugin.getLogger().info(" Se detectó una nueva versión de Resource Pack (Hash actualizado). Se pedirá a los jugadores elegibles.");
            dataConfig.set("players", null);
            dataConfig.set("last-hash", packHash);
            saveData();
            return;
        }

        if (dataConfig.isConfigurationSection("players")) {
            for (String uuidStr : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    if (dataConfig.getBoolean("players." + uuidStr + ".loaded", false)) {
                        installedPlayers.add(uuid);
                    }
                    if (dataConfig.getBoolean("players." + uuidStr + ".opt-out", false)) {
                        optedOutPlayers.add(uuid);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public synchronized void saveData() {
        if (dataConfig == null || dataFile == null) return;
        dataConfig.set("last-hash", packHash);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Error al guardar data/resourcepacks.yml: " + e.getMessage());
        }
    }

    /**
     * Comprueba si un jugador debe recibir el pack del servidor.
     */
    public boolean isEligible(Player player) {
        if (!enabled) return false;
        if (player == null || !player.isOnline()) return false;

        // Jugador ha optado por no recibirlo manualmente
        if (optedOutPlayers.contains(player.getUniqueId())) {
            return false;
        }

        // Si es Bedrock
        if (BedrockDetector.isBedrockPlayer(player)) {
            if (bedrockBypassJava && (bedrockCustomUrl == null || bedrockCustomUrl.trim().isEmpty())) {
                return false; // Bedrock está exento de paquetes Java para evitar bugs
            }
        }

        switch (targetMode) {
            case "ALL":
                return true;
            case "WHITELIST":
                return whitelistNames.contains(player.getName().toLowerCase());
            case "PERMISSION":
            default:
                return player.hasPermission(permission) || player.isOp();
        }
    }

    /**
     * Verifica si el jugador ya tiene el pack instalado/cargado en sesión previa.
     */
    public boolean hasLoadedPack(UUID uuid) {
        return installedPlayers.contains(uuid);
    }

    /**
     * Envía el paquete de recursos al jugador respetando filtros y compatibilidad.
     */
    public boolean sendResourcePack(Player player, boolean forceSend) {
        if (!enabled) return false;
        if (player == null || !player.isOnline()) return false;

        boolean isBedrock = BedrockDetector.isBedrockPlayer(player);

        // Caso 1: Jugador de Bedrock
        if (isBedrock) {
            if (bedrockBypassJava && (bedrockCustomUrl == null || bedrockCustomUrl.trim().isEmpty())) {
                if (bedrockNotifyInfo && bedrockInfoMessage != null && !bedrockInfoMessage.isEmpty()) {
                    player.sendMessage(color(bedrockInfoMessage));
                }
                return false; // Totalmente protegido de paquetes Java
            }

            // Si hay una URL dedicada exclusivamente para Bedrock
            if (bedrockCustomUrl != null && !bedrockCustomUrl.trim().isEmpty()) {
                sendPackPacket(player, bedrockCustomUrl, bedrockCustomHashBytes, promptText, force);
                return true;
            }
            return false;
        }

        // Caso 2: Jugador Java
        if (packUrl == null || packUrl.trim().isEmpty()) {
            return false;
        }

        // Si ya lo tiene y está activo send-only-once, no forzarlo
        if (!forceSend && sendOnlyOnce && hasLoadedPack(player.getUniqueId())) {
            return false; // Evita el reload screen en el cliente
        }

        sendPackPacket(player, packUrl, packHashBytes, promptText, force);
        return true;
    }

    private void sendPackPacket(Player player, String url, byte[] hash, String prompt, boolean isForce) {
        try {
            Component promptComponent = prompt != null && !prompt.isEmpty()
                    ? LegacyComponentSerializer.legacyAmpersand().deserialize(prompt)
                    : null;

            UUID packUUID = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
            
            if (promptComponent != null) {
                player.setResourcePack(packUUID, url, hash, promptComponent, isForce);
            } else {
                if (hash != null) {
                    player.setResourcePack(url, hash, isForce);
                } else {
                    player.setResourcePack(url);
                }
            }
        } catch (Throwable t) {
            // Fallback para variantes de API
            try {
                if (hash != null) {
                    player.setResourcePack(url, hash);
                } else {
                    player.setResourcePack(url);
                }
            } catch (Throwable fallbackErr) {
                plugin.getLogger().warning("No se pudo enviar Resource Pack a " + player.getName() + ": " + fallbackErr.getMessage());
            }
        }
    }

    /**
     * Registra que un jugador cargó satisfactoriamente el pack.
     */
    public void recordSuccess(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        installedPlayers.add(uuid);

        if (dataConfig != null) {
            dataConfig.set("players." + uuid + ".loaded", true);
            dataConfig.set("players." + uuid + ".name", player.getName());
            dataConfig.set("players." + uuid + ".timestamp", System.currentTimeMillis());
            dataConfig.set("players." + uuid + ".hash", packHash);
            saveData();
        }
    }

    public void resetPlayer(UUID uuid) {
        installedPlayers.remove(uuid);
        if (dataConfig != null) {
            dataConfig.set("players." + uuid, null);
            saveData();
        }
    }

    public boolean toggleOptOut(UUID uuid) {
        if (optedOutPlayers.contains(uuid)) {
            optedOutPlayers.remove(uuid);
            if (dataConfig != null) {
                dataConfig.set("players." + uuid + ".opt-out", false);
                saveData();
            }
            return false; // Ahora está activado
        } else {
            optedOutPlayers.add(uuid);
            if (dataConfig != null) {
                dataConfig.set("players." + uuid + ".opt-out", true);
                saveData();
            }
            return true; // Ahora está desactivado (opt-out)
        }
    }

    public boolean isOptedOut(UUID uuid) {
        return optedOutPlayers.contains(uuid);
    }

    public static byte[] parseHexHash(String hashStr) {
        if (hashStr == null) return null;
        hashStr = hashStr.trim();
        if (hashStr.length() != 40) return null;
        try {
            byte[] data = new byte[20];
            for (int i = 0; i < 40; i += 2) {
                data[i / 2] = (byte) ((Character.digit(hashStr.charAt(i), 16) << 4)
                                     + Character.digit(hashStr.charAt(i + 1), 16));
            }
            return data;
        } catch (Exception ignored) {
            return null;
        }
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    // Getters
    public boolean isEnabled() { return enabled; }
    public String getPackUrl() { return packUrl; }
    public String getPackHash() { return packHash; }
    public boolean isSendOnlyOnce() { return sendOnlyOnce; }
    public int getDelayTicks() { return delayTicks; }
    public boolean isKickOnDecline() { return kickOnDecline; }
    public String getDeclineKickMessage() { return declineKickMessage; }
    public boolean isBedrockBypassJava() { return bedrockBypassJava; }
    public String getBedrockCustomUrl() { return bedrockCustomUrl; }

    public void sendBedrockPack(Player player) {
        if (player == null || !player.isOnline()) return;
        if (bedrockCustomUrl == null || bedrockCustomUrl.trim().isEmpty()) return;
        sendPackPacket(player, bedrockCustomUrl, bedrockCustomHashBytes, promptText, false);
    }
}
