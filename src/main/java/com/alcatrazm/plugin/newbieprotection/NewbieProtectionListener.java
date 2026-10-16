package com.alcatrazm.plugin.newbieprotection;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class NewbieProtectionListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final NewbieProtectionManager manager;

    public NewbieProtectionListener(AlcatrazPlugin plugin, NewbieProtectionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        if (!player.hasPlayedBefore()) {
            int durationMinutes = manager.getDefaultDurationMinutes();
            manager.grantProtection(player.getUniqueId(), durationMinutes);

            String formattedTime = manager.getRemainingFormatted(player.getUniqueId());
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(color("&8&m----------------------------------------"));
                    player.sendMessage(color("&a&l¡BIENVENIDO A ALCATRAZ!"));
                    player.sendMessage(color("&7Cuentas con &e" + formattedTime + " &ade Protección de Nuevo Usuario."));
                    player.sendMessage(color("&8▪ &aInmunidad total contra daño PvP de otros jugadores."));
                    player.sendMessage(color("&8▪ &bRescate automático contra caídas al Vacío en islas."));
                    player.sendMessage(color("&7Si deseas desactivarla para pelear, usa &e/pvp on&7."));
                    player.sendMessage(color("&8&m----------------------------------------"));
                }
            }, 40L);
        } else if (manager.hasProtection(player.getUniqueId())) {
            String remaining = manager.getRemainingFormatted(player.getUniqueId());
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(color("&8[&aProtección Nuevo Usuario&8] &aTu protección contra PvP y Caída al Vacío sigue activa (&e" + remaining + " restantes&a)."));
                }
            }, 40L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvPDamage(EntityDamageByEntityEvent event) {
        if (!manager.isEnabled()) return;

        Entity victimEntity = event.getEntity();
        if (!(victimEntity instanceof Player victim)) return;

        Player attacker = getAttackingPlayer(event.getDamager());
        if (attacker == null) return;

        // Evitar atacarse a sí mismo (ej. perlas de ender)
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

        // 1. Si la víctima está protegida
        if (manager.hasProtection(victim.getUniqueId())) {
            event.setCancelled(true);
            String remaining = manager.getRemainingFormatted(victim.getUniqueId());
            attacker.sendMessage(color("&c¡Ese jugador tiene protección de novato! (" + remaining + " restantes)"));
            return;
        }

        // 2. Si el atacante está protegido
        if (manager.hasProtection(attacker.getUniqueId())) {
            event.setCancelled(true);
            attacker.sendMessage(color("&cNo puedes atacar a otros jugadores mientras tengas protección de novato."));
            attacker.sendMessage(color("&7Usa &e/pvp on &7para desactivar tu protección y poder combatir."));
        }
    }

    private Player getAttackingPlayer(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj) {
            if (proj.getShooter() instanceof Player p) {
                return p;
            }
        }
        return null;
    }
}
