package com.alcatrazm.plugin.skills;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

public class AuraSkillsFixer {

    private final AlcatrazPlugin plugin;
    private static final Set<String> INVALID_SKILL_KEYS = new HashSet<>(Arrays.asList(
            "forging", "auraskills/forging",
            "healing", "auraskills/healing",
            "sorcery", "auraskills/sorcery",
            "endurance", "auraskills/endurance"
    ));

    public AuraSkillsFixer(AlcatrazPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAuraSkillsEnabled() {
        Plugin p = Bukkit.getPluginManager().getPlugin("AuraSkills");
        return p != null && p.isEnabled();
    }

    public boolean isUserLoaded(UUID uuid) {
        if (!isAuraSkillsEnabled()) return true;
        try {
            Plugin aura = Bukkit.getPluginManager().getPlugin("AuraSkills");
            Method getUserManager = aura.getClass().getMethod("getUserManager");
            Object userManager = getUserManager.invoke(aura);
            Method hasUser = userManager.getClass().getMethod("hasUser", UUID.class);
            return (Boolean) hasUser.invoke(userManager, uuid);
        } catch (Throwable t) {
            return true;
        }
    }

    public boolean sanitizeUserdataFile(UUID uuid) {
        File dataDir = new File(plugin.getDataFolder().getParentFile(), "AuraSkills/userdata");
        if (!dataDir.exists()) {
            dataDir = new File("plugins/AuraSkills/userdata");
        }
        File userFile = new File(dataDir, uuid.toString() + ".yml");
        if (!userFile.exists()) return false;

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(userFile);
            if (!yaml.contains("skills")) return false;

            boolean modified = false;
            ConfigurationSection skillsSec = yaml.getConfigurationSection("skills");
            if (skillsSec != null) {
                for (String key : new ArrayList<>(skillsSec.getKeys(false))) {
                    for (String invalid : INVALID_SKILL_KEYS) {
                        if (key.equalsIgnoreCase(invalid) || key.endsWith("/" + invalid)) {
                            skillsSec.set(key, null);
                            modified = true;
                        }
                    }
                }
            }

            if (modified) {
                yaml.save(userFile);
                plugin.getLogger().info("[AuraSkillsFixer] Archivo saneado de habilidades heredadas: " + userFile.getName());
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[AuraSkillsFixer] Error saneando archivo de usuario: " + e.getMessage());
        }
        return false;
    }

    public boolean forceLoadUser(Player player) {
        if (player == null || !player.isOnline()) return false;
        if (!isAuraSkillsEnabled()) return false;

        // 1. Saneamos el archivo YAML del jugador si tuviera claves corruptas o heredadas
        sanitizeUserdataFile(player.getUniqueId());

        // 2. Cargamos el usuario en memoria de AuraSkills
        try {
            Plugin aura = Bukkit.getPluginManager().getPlugin("AuraSkills");
            Method getUserManager = aura.getClass().getMethod("getUserManager");
            Object userManager = getUserManager.invoke(aura);
            Method hasUser = userManager.getClass().getMethod("hasUser", UUID.class);
            if ((Boolean) hasUser.invoke(userManager, player.getUniqueId())) {
                return true;
            }

            Method getStorageProvider = aura.getClass().getMethod("getStorageProvider");
            Object storageProvider = getStorageProvider.invoke(aura);

            Class<?> playerRefClass = Class.forName("dev.aurelium.auraskills.common.ref.PlayerRef");
            Class<?> bukkitPlayerRefClass = Class.forName("dev.aurelium.auraskills.bukkit.ref.BukkitPlayerRef");
            Method wrap = bukkitPlayerRefClass.getMethod("wrap", Player.class);
            Object wrappedPlayer = wrap.invoke(null, player);

            Method loadMethod = storageProvider.getClass().getMethod("load", UUID.class, playerRefClass);
            loadMethod.invoke(storageProvider, player.getUniqueId(), wrappedPlayer);

            boolean success = (Boolean) hasUser.invoke(userManager, player.getUniqueId());
            if (success) {
                plugin.getLogger().info("[AuraSkillsFixer] ¡Perfil de AuraSkills cargado y activado con éxito para " + player.getName() + "!");
            }
            return success;
        } catch (Throwable t) {
            plugin.getLogger().warning("[AuraSkillsFixer] Error cargando perfil para " + player.getName() + ": " + t.getMessage());
            return false;
        }
    }

    public int syncAllOnlinePlayers() {
        int loaded = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isUserLoaded(p.getUniqueId())) {
                if (forceLoadUser(p)) {
                    loaded++;
                }
            }
        }
        return loaded;
    }

    public int sanitizeAllUserDataFiles() {
        File dataDir = new File(plugin.getDataFolder().getParentFile(), "AuraSkills/userdata");
        if (!dataDir.exists()) {
            dataDir = new File("plugins/AuraSkills/userdata");
        }
        if (!dataDir.exists()) return 0;
        File[] files = dataDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return 0;

        int count = 0;
        for (File f : files) {
            try {
                String name = f.getName().replace(".yml", "");
                UUID uuid = UUID.fromString(name);
                if (sanitizeUserdataFile(uuid)) {
                    count++;
                }
            } catch (Exception ignored) {}
        }
        return count;
    }
}
