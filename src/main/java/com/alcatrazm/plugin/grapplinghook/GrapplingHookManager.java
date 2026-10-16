package com.alcatrazm.plugin.grapplinghook;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GrapplingHookManager {

    private final AlcatrazPlugin plugin;
    public final NamespacedKey KEY_GRAPPLING_HOOK;
    public final NamespacedKey KEY_USES_LEFT;

    private boolean enabled;
    private double pullMultiplier;
    private double verticalBoost;
    private double cooldownSeconds;
    private int defaultUses;

    // Cooldowns: Player UUID -> Cooldown expiry timestamp
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    // Safe fall protection: Player UUID -> Expiry timestamp (10s max)
    private final Map<UUID, Long> fallProtectedUntil = new ConcurrentHashMap<>();

    public GrapplingHookManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.KEY_GRAPPLING_HOOK = new NamespacedKey(plugin, "grappling_hook");
        this.KEY_USES_LEFT = new NamespacedKey(plugin, "grappling_uses");
        loadConfig();
    }

    public void loadConfig() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("grapplinghook");
        if (sec == null) {
            this.enabled = true;
            this.pullMultiplier = 1.65;
            this.verticalBoost = 0.35;
            this.cooldownSeconds = 2.0;
            this.defaultUses = 50;
            return;
        }

        this.enabled = sec.getBoolean("enabled", true);
        this.pullMultiplier = sec.getDouble("pull-multiplier", 1.65);
        this.verticalBoost = sec.getDouble("vertical-boost", 0.35);
        this.cooldownSeconds = sec.getDouble("cooldown-seconds", 2.0);
        this.defaultUses = sec.getInt("default-uses", 50);
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getPullMultiplier() {
        return pullMultiplier;
    }

    public double getVerticalBoost() {
        return verticalBoost;
    }

    public double getCooldownSeconds() {
        return cooldownSeconds;
    }

    public ItemStack createGrapplingHook(int uses) {
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&b✦ &3&lGancho de Agarre Alcatraz &b✦"));
            List<String> lore = new ArrayList<>();
            lore.add(color("&7¡Herramienta táctica de propulsión y agarre!"));
            lore.add(color(""));
            lore.add(color("&8▪ &7Lanza el anzuelo y recoge para impulsarte."));
            lore.add(color("&8▪ &7Inmunidad a daño por caída al aterrizar."));
            lore.add(color("&8▪ &7Cooldown: &e" + cooldownSeconds + "s"));
            lore.add(color(""));
            if (uses > 0) {
                lore.add(color("&eUsos restantes: &a" + uses));
            } else {
                lore.add(color("&eUsos: &6&lINFINITO"));
            }
            meta.setLore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(KEY_GRAPPLING_HOOK, PersistentDataType.STRING, "true");
            pdc.set(KEY_USES_LEFT, PersistentDataType.INTEGER, uses);

            rod.setItemMeta(meta);
        }
        return rod;
    }

    public boolean isGrapplingHook(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(KEY_GRAPPLING_HOOK, PersistentDataType.STRING);
    }

    public int getUsesLeft(ItemStack item) {
        if (!isGrapplingHook(item)) return -1;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return -1;
        Integer val = meta.getPersistentDataContainer().get(KEY_USES_LEFT, PersistentDataType.INTEGER);
        return val != null ? val : -1;
    }

    public void decrementUses(Player player, ItemStack item) {
        if (!isGrapplingHook(item)) return;
        int current = getUsesLeft(item);
        if (current <= 0) return; // Infinito

        int remaining = current - 1;
        if (remaining <= 0) {
            player.getInventory().remove(item);
            player.sendMessage(color("&c¡Tu Gancho de Agarre se ha roto por agotamiento de usos!"));
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(KEY_USES_LEFT, PersistentDataType.INTEGER, remaining);
            List<String> lore = meta.getLore();
            if (lore != null) {
                for (int i = 0; i < lore.size(); i++) {
                    if (lore.get(i).contains("Usos restantes:")) {
                        lore.set(i, color("&eUsos restantes: &a" + remaining));
                        break;
                    }
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
    }

    public boolean isOnCooldown(Player player) {
        Long exp = cooldowns.get(player.getUniqueId());
        if (exp == null) return false;
        return System.currentTimeMillis() < exp;
    }

    public double getRemainingCooldownSeconds(Player player) {
        Long exp = cooldowns.get(player.getUniqueId());
        if (exp == null) return 0.0;
        long diff = exp - System.currentTimeMillis();
        return diff > 0 ? diff / 1000.0 : 0.0;
    }

    public void applyCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (long)(cooldownSeconds * 1000L));
    }

    public void setFallProtected(Player player) {
        fallProtectedUntil.put(player.getUniqueId(), System.currentTimeMillis() + 10000L);
    }

    public boolean isFallProtected(Player player) {
        Long until = fallProtectedUntil.get(player.getUniqueId());
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            fallProtectedUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void removeFallProtection(Player player) {
        fallProtectedUntil.remove(player.getUniqueId());
    }

    public void cleanPlayer(UUID uuid) {
        cooldowns.remove(uuid);
        fallProtectedUntil.remove(uuid);
    }
}
