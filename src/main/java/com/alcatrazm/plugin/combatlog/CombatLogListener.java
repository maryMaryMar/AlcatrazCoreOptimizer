package com.alcatrazm.plugin.combatlog;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public class CombatLogListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final CombatLogManager manager;

    public CombatLogListener(AlcatrazPlugin plugin, CombatLogManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) {
            return p;
        }
        if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDungeonPvPPrevent(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event);
        if (attacker != null && !attacker.equals(victim)) {
            if (manager.isDungeonWorld(victim.getWorld()) || manager.isDungeonWorld(attacker.getWorld())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvPDamage(EntityDamageByEntityEvent event) {
        if (!manager.isEnabled()) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event);
        if (attacker != null && !attacker.equals(victim)) {
            manager.tagPlayer(victim);
            manager.tagPlayer(attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!manager.isEnabled()) return;
        Player player = event.getPlayer();
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;

        if (manager.isNoFlyZone(player.getLocation()) && manager.isFlyCommand(event.getMessage())) {
            event.setCancelled(true);
            manager.disableFlightNow(player);
            player.sendMessage(color("&8[&c⚔ Restricción&8] &cEl modo de vuelo está desactivado en esta zona."));
            return;
        }

        if (manager.isInCombat(player.getUniqueId())) {
            if (manager.isCommandBlocked(event.getMessage())) {
                event.setCancelled(true);
                player.sendMessage(color("&8[&c⚔ Combate&8] &cNo puedes usar este comando mientras estás en combate (&e" + manager.getRemainingSeconds(player.getUniqueId()) + "s&c)."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!manager.isEnabled()) return;
        Player player = event.getPlayer();

        if (manager.isInCombat(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(color("&8[&c⚔ Combate&8] &cNo puedes escapar por portales mientras estás en combate."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!manager.isEnabled()) return;
        Player player = event.getPlayer();

        if (manager.isInCombat(player.getUniqueId())) {
            PlayerTeleportEvent.TeleportCause cause = event.getCause();
            if (cause == PlayerTeleportEvent.TeleportCause.COMMAND ||
                cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT ||
                cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
                event.setCancelled(true);
                player.sendMessage(color("&8[&c⚔ Combate&8] &cNo puedes teletransportarte mientras estás en combate."));
                return;
            }
        }

        if (event.getTo() != null && (manager.isNoFlyZone(event.getTo()) || manager.isInCombat(player.getUniqueId()))) {
            manager.disableFlightNow(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && (manager.isNoFlyZone(player.getLocation()) || manager.isInCombat(player.getUniqueId()))) {
                    manager.disableFlightNow(player);
                }
            }, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (manager.isNoFlyZone(player.getLocation()) || manager.isInCombat(player.getUniqueId())) {
            manager.disableFlightNow(player);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && (manager.isNoFlyZone(player.getLocation()) || manager.isInCombat(player.getUniqueId()))) {
                manager.disableFlightNow(player);
            }
        }, 2L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && (manager.isNoFlyZone(player.getLocation()) || manager.isInCombat(player.getUniqueId()))) {
                manager.disableFlightNow(player);
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && manager.isNoFlyZone(player.getLocation())) {
                manager.disableFlightNow(player);
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!manager.isEnabled()) return;
        Player player = event.getPlayer();
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;

        if (manager.isInCombat(player.getUniqueId())) {
            event.setCancelled(true);
            manager.disableFlightNow(player);
            player.sendMessage(color("&8[&c⚔ Combate&8] &cNo puedes volar mientras estás en combate PvP."));
        } else if (manager.isNoFlyZone(player.getLocation())) {
            event.setCancelled(true);
            manager.disableFlightNow(player);
            player.sendMessage(color("&8[&c⚔ Restricción&8] &cEl vuelo está desactivado en esta zona."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!manager.isEnabled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.isGliding()) return;
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;

        if (manager.isInCombat(player.getUniqueId()) || manager.isNoFlyZone(player.getLocation())) {
            event.setCancelled(true);
            manager.disableFlightNow(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!manager.isEnabled()) return;
        Player player = event.getPlayer();
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;

        if (player.isFlying() || player.getAllowFlight() || player.isGliding()) {
            Location to = event.getTo() != null ? event.getTo() : player.getLocation();
            if (manager.isInCombat(player.getUniqueId()) || manager.isNoFlyZone(to)) {
                manager.disableFlightNow(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!manager.isEnabled()) return;
        Player player = event.getPlayer();

        if (manager.isInCombat(player.getUniqueId()) && !player.hasPermission("alcatraz.combatlog.bypass")) {
            // El jugador se desconectó cobardemente en combate: muere y suelta inventario
            player.setHealth(0.0);
            manager.removePlayer(player.getUniqueId());
            Bukkit.broadcastMessage(color("&8[&c⚔ Anti-CombatLog&8] &c" + player.getName() + " &7se desconectó cobardemente durante el combate y ha muerto."));
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!manager.isEnabled()) return;
        manager.removePlayer(event.getEntity().getUniqueId());
    }
}
