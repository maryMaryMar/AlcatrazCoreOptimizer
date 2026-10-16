package com.alcatrazm.plugin.newbieprotection;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NewbieProtectionManager {

    private final AlcatrazPlugin plugin;
    private final Map<UUID, Long> protections = new ConcurrentHashMap<>();
    private final NamespacedKey KEY_NEWBIE_EXPIRE;
    private File dataFile;
    private YamlConfiguration dataConfig;

    public NewbieProtectionManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.KEY_NEWBIE_EXPIRE = new NamespacedKey(plugin, "newbie_expire");
        loadData();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("newbie-protection.enabled", true);
    }

    public int getDefaultDurationMinutes() {
        if (plugin.getConfig().contains("newbie-protection.duration-hours")) {
            return plugin.getConfig().getInt("newbie-protection.duration-hours", 48) * 60;
        }
        return plugin.getConfig().getInt("newbie-protection.duration-minutes", 2880);
    }

    public void loadData() {
        protections.clear();
        dataFile = new File(plugin.getDataFolder(), "data/newbies.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo crear data/newbies.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        long now = System.currentTimeMillis();
        for (String key : dataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long expiry = dataConfig.getLong(key);
                if (expiry > now) {
                    protections.put(uuid, expiry);
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void saveData() {
        if (dataConfig == null || dataFile == null) return;

        long now = System.currentTimeMillis();
        for (String key : dataConfig.getKeys(false)) {
            dataConfig.set(key, null);
        }

        for (Map.Entry<UUID, Long> entry : protections.entrySet()) {
            if (entry.getValue() > now) {
                dataConfig.set(entry.getKey().toString(), entry.getValue());
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Error al guardar data/newbies.yml: " + e.getMessage());
        }
    }

    public boolean hasProtection(UUID uuid) {
        if (!isEnabled()) return false;
        Long expiry = protections.get(uuid);

        // Auto-recuperación desde el PDC del jugador
        if (expiry == null) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                PersistentDataContainer pdc = p.getPersistentDataContainer();
                if (pdc.has(KEY_NEWBIE_EXPIRE, PersistentDataType.LONG)) {
                    expiry = pdc.get(KEY_NEWBIE_EXPIRE, PersistentDataType.LONG);
                    if (expiry != null && expiry > System.currentTimeMillis()) {
                        protections.put(uuid, expiry);
                    }
                }
            }
        }

        if (expiry == null) return false;

        if (System.currentTimeMillis() >= expiry) {
            removeProtection(uuid);
            return false;
        }
        return true;
    }

    public void grantProtection(UUID uuid, int minutes) {
        long expiry = System.currentTimeMillis() + (minutes * 60L * 1000L);
        protections.put(uuid, expiry);
        saveData();

        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            p.getPersistentDataContainer().set(KEY_NEWBIE_EXPIRE, PersistentDataType.LONG, expiry);
        }
    }

    public void removeProtection(UUID uuid) {
        protections.remove(uuid);
        saveData();

        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            p.getPersistentDataContainer().remove(KEY_NEWBIE_EXPIRE);
        }
    }

    public long getRemainingSeconds(UUID uuid) {
        Long expiry = protections.get(uuid);
        if (expiry == null) return 0;
        long diff = expiry - System.currentTimeMillis();
        return Math.max(0, diff / 1000L);
    }

    public String getRemainingFormatted(UUID uuid) {
        long seconds = getRemainingSeconds(uuid);
        if (seconds <= 0) return "0s";
        long hours = seconds / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return hours + "h " + mins + "m";
        } else if (mins > 0) {
            return mins + "m " + secs + "s";
        } else {
            return secs + "s";
        }
    }
}
