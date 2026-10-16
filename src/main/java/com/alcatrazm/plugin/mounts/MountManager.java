package com.alcatrazm.plugin.mounts;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MountManager {

    private final AlcatrazPlugin plugin;
    private final Map<UUID, ActiveMount> activeMounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fallProtection = new ConcurrentHashMap<>();

    private BukkitTask tickTask;
    private boolean enabled;
    private boolean combatAutoDismiss;
    private boolean protectFallDamage;
    private int fallProtectionSeconds;
    private double followStartDistance;
    private double teleportDistance;

    public MountManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        startTickTask();
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("mounts.enabled", true);
        this.combatAutoDismiss = config.getBoolean("mounts.combat-auto-dismiss", true);
        this.protectFallDamage = config.getBoolean("mounts.protect-fall-damage", true);
        this.fallProtectionSeconds = config.getInt("mounts.fall-protection-seconds", 10);
        this.followStartDistance = config.getDouble("mounts.follow-start-distance", 5.0);
        this.teleportDistance = config.getDouble("mounts.teleport-distance", 28.0);
    }

    public void reload() {
        loadConfig();
        for (ActiveMount mount : activeMounts.values()) {
            mount.loadConfigValues();
        }
    }

    private void startTickTask() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
        }

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled || activeMounts.isEmpty()) {
                return;
            }

            for (Map.Entry<UUID, ActiveMount> entry : activeMounts.entrySet()) {
                UUID uuid = entry.getKey();
                ActiveMount mount = entry.getValue();

                if (!mount.isValid()) {
                    mount.dismiss();
                    activeMounts.remove(uuid);
                    continue;
                }

                PlayerInputHelper.InputResult input = PlayerInputHelper.getInput(mount.getPlayer());
                mount.tick(input);
            }
        }, 1L, 1L);
    }

    public String getMountPermission(MountType type) {
        String customPerm = plugin.getConfig().getString("mounts.types." + type.getId() + ".permission");
        if (customPerm != null && !customPerm.trim().isEmpty()) {
            return customPerm.trim();
        }
        return "alcatraz.mounts." + type.getId();
    }

    public boolean canUseMount(Player player, MountType type) {
        if (player == null) return false;
        if (player.isOp() 
                || player.hasPermission("alcatraz.mounts.admin") 
                || player.hasPermission("alcatraz.admin") 
                || player.hasPermission("alcatraz.mounts.*") 
                || player.hasPermission("alcatraz.mounts.all")) {
            return true;
        }

        // 1. Permiso independiente específico de esta montura
        String specificPerm = getMountPermission(type);
        if (player.hasPermission(specificPerm)) {
            return true;
        }
        if (player.hasPermission("alcatraz.mounts." + type.getId())) {
            return true;
        }
        if (player.hasPermission("alcatraz.mounts.type." + type.getId())) {
            return true;
        }
        if (player.hasPermission("alcatraz.mounts.premium." + type.getId())) {
            return true;
        }

        // 2. Si la montura está configurada como gratuita para usuarios con alcatraz.mounts.use
        boolean isFree = plugin.getConfig().getBoolean("mounts.types." + type.getId() + ".free", false);
        if (isFree && player.hasPermission("alcatraz.mounts.use")) {
            return true;
        }

        // 3. Si el jugador tiene el rango VIP/Premium general y la montura está marcada como premium
        boolean isPrem = plugin.getConfig().getBoolean("mounts.types." + type.getId() + ".premium", type.isPremium());
        if (isPrem && player.hasPermission("alcatraz.mounts.premium")) {
            return true;
        }

        return false;
    }

    public boolean hasAnyMountPermission(Player player) {
        if (player == null) return false;
        if (player.isOp() 
                || player.hasPermission("alcatraz.mounts.admin") 
                || player.hasPermission("alcatraz.admin") 
                || player.hasPermission("alcatraz.mounts.*") 
                || player.hasPermission("alcatraz.mounts.all")
                || player.hasPermission("alcatraz.mounts.use")
                || player.hasPermission("alcatraz.mounts.gui")
                || player.hasPermission("alcatraz.mounts.mod")) {
            return true;
        }
        for (MountType type : MountType.values()) {
            if (canUseMount(player, type)) {
                return true;
            }
        }
        return false;
    }

    public void spawnMount(Player player, MountType type) {
        if (!enabled) {
            player.sendMessage(color("&cEl sistema de monturas se encuentra desactivado temporalmente."));
            return;
        }

        if (!canUseMount(player, type)) {
            player.sendMessage(color("&8[&6Monturas&8] &cNo tienes desbloqueada la criatura &e" + type.getDisplayName() + "&c."));
            player.sendMessage(color("&7¡Adquiérela en la tienda del servidor! Permiso requerido: &e" + getMountPermission(type)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (type.isAquatic() && type == MountType.DOLPHIN) {
            if (!player.getLocation().getBlock().isLiquid() && !player.getEyeLocation().getBlock().isLiquid()) {
                player.sendMessage(color("&8[&6Monturas&8] &c¡El delfín solo puede ser invocado dentro del agua!"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
        }

        // Si ya tenía una montura, retirarla primero
        dismissMount(player.getUniqueId(), false);

        double scale = plugin.getConfig().getDouble("mounts.types." + type.getId() + ".scale", type.getDefaultScale());
        double speed = plugin.getConfig().getDouble("mounts.types." + type.getId() + ".speed", type.getDefaultSpeed());
        double jump = plugin.getConfig().getDouble("mounts.types." + type.getId() + ".jump-velocity", type.getDefaultJump());

        ActiveMount mount = new ActiveMount(plugin, player, type, scale, speed, jump);
        mount.spawn();
        activeMounts.put(player.getUniqueId(), mount);

        if (!type.isRideable()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1.0f, 1.2f);
            player.sendMessage(color("&8[&bMascotas&8] &aHas invocado a tu &b&l" + type.getDisplayName() + "&a."));
            player.sendMessage(color("&7¡Volará a tu lado otorgándote &aRegeneración I &7y &eVelocidad I&7!"));
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_HORSE_ARMOR, 1.0f, 1.2f);
            player.sendMessage(color("&8[&6Monturas&8] &aHas invocado a tu &e&l" + type.getDisplayName() + "&a."));
            if (type.isFlying()) {
                player.sendMessage(color("&7Usa &eWASD &7+ Mirada para volar, &eESPACIO &7para ascender y &eSHIFT &7para descender."));
            } else if (type.isAquatic()) {
                player.sendMessage(color("&7Usa &eWASD &7para nadar velozmente por el agua."));
            } else {
                player.sendMessage(color("&7Usa &eWASD &7para moverte y &eESPACIO &7para saltar."));
            }
        }
    }

    public void dismissMount(UUID uuid) {
        dismissMount(uuid, true);
    }

    public void dismissMount(UUID uuid, boolean notify) {
        ActiveMount mount = activeMounts.remove(uuid);
        if (mount != null) {
            mount.dismiss();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && notify) {
                player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1.0f, 0.8f);
                player.sendMessage(color("&8[&6Monturas&8] &cTu montura ha regresado al establo."));
            }
        }
    }

    public void addFallProtection(UUID uuid, long seconds) {
        fallProtection.put(uuid, System.currentTimeMillis() + (seconds * 1000L));
    }

    public boolean hasFallProtection(UUID uuid) {
        if (!protectFallDamage || uuid == null) return false;
        Long exp = fallProtection.get(uuid);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            fallProtection.remove(uuid);
            return false;
        }
        return true;
    }

    public ActiveMount getActiveMount(UUID uuid) {
        return activeMounts.get(uuid);
    }

    public boolean hasActiveMount(UUID uuid) {
        return activeMounts.containsKey(uuid);
    }

    public void dismissAll() {
        for (ActiveMount mount : activeMounts.values()) {
            mount.dismiss();
        }
        activeMounts.clear();
        fallProtection.clear();
        if (tickTask != null) {
            tickTask.cancel();
        }
    }

    public boolean isCombatAutoDismiss() {
        return combatAutoDismiss;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getFallProtectionSeconds() {
        return fallProtectionSeconds;
    }

    public double getFollowStartDistance() {
        return followStartDistance;
    }

    public double getTeleportDistance() {
        return teleportDistance;
    }

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
