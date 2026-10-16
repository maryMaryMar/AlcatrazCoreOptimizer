package com.alcatrazm.plugin.combatlog;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvPDamage(EntityDamageByEntityEvent event) {
        if (!manager.isEnabled()) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker != null && !attacker.equals(victim)) {
            manager.tagPlayer(victim);
            manager.tagPlayer(attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!manager.isEnabled()) return;
        Player player = event.getPlayer();

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
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!manager.isEnabled()) return;
        Player player = event.getPlayer();

        if (manager.isInCombat(player.getUniqueId())) {
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
