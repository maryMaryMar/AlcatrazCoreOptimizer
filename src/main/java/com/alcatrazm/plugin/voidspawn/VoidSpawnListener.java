package com.alcatrazm.plugin.voidspawn;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VoidSpawnListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final Map<UUID, Long> savedRecently = new ConcurrentHashMap<>();

    public VoidSpawnListener(AlcatrazPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("void-spawn.enabled", true);
    }

    private boolean isWorldAllowed(World world) {
        List<String> worlds = plugin.getConfig().getStringList("void-spawn.worlds");
        return worlds.isEmpty() || worlds.contains(world.getName());
    }

    private int getActivationY() {
        return plugin.getConfig().getInt("void-spawn.activation-y", -60);
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private boolean requiresNewbieProtection() {
        return plugin.getConfig().getBoolean("void-spawn.require-newbie-protection", true);
    }

    private boolean isPlayerProtected(Player player) {
        if (!requiresNewbieProtection()) return true;
        return plugin.getNewbieProtectionManager() != null && plugin.getNewbieProtectionManager().hasProtection(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (!isEnabled()) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) return;

        if (event.getEntity() instanceof Player player) {
            if (!isWorldAllowed(player.getWorld())) return;
            if (!isPlayerProtected(player)) return;

            event.setCancelled(true);
            savePlayerFromVoid(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        if (!isWorldAllowed(player.getWorld())) return;
        if (!isPlayerProtected(player)) return;

        if (player.getLocation().getY() <= getActivationY()) {
            savePlayerFromVoid(player);
        }
    }

    private void savePlayerFromVoid(Player player) {
        Long lastSave = savedRecently.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (lastSave != null && now - lastSave < 2000) {
            return;
        }
        savedRecently.put(player.getUniqueId(), now);

        Location spawnLoc = getSafeSpawnLocation(player);

        player.setFallDistance(0.0f);
        player.setVelocity(new Vector(0, 0, 0));

        player.teleportAsync(spawnLoc).thenAcceptAsync(success -> {
            if (Boolean.TRUE.equals(success) && player.isOnline()) {
                player.setFallDistance(0.0f);
                player.setVelocity(new Vector(0, 0, 0));

                // Efectos de protección para evitar cualquier muerte residual
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 4, false, false));

                // Sonido y partículas
                try {
                    player.playSound(spawnLoc, Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
                } catch (Exception ignored) {}

                if (spawnLoc.getWorld() != null) {
                    spawnLoc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, spawnLoc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                }

                // Título y mensaje
                String title = color(plugin.getConfig().getString("messages-voidspawn.title", "&c&l¡SALVADO DEL VACÍO!"));
                String subtitle = color(plugin.getConfig().getString("messages-voidspawn.subtitle", "&7Has sido rescatado y devuelto al Spawn."));
                player.sendTitle(title, subtitle, 10, 60, 20);

                String msg = color(plugin.getConfig().getString("messages-voidspawn.saved", "&8[&bAlcatraz&cCore&8] &e¡Has caído al vacío! Has sido teletransportado sano y salvo al spawn."));
                player.sendMessage(msg);
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    private Location getSafeSpawnLocation(Player player) {
        Location bed = player.getRespawnLocation();
        if (bed != null) {
            return bed.clone().add(0.5, 0.1, 0.5);
        }

        World mainWorld = Bukkit.getWorlds().get(0);
        Location spawn = mainWorld.getSpawnLocation();
        return spawn.clone().add(0.5, 0.1, 0.5);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        savedRecently.remove(event.getPlayer().getUniqueId());
    }
}
