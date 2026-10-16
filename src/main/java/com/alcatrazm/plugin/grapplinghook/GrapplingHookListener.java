package com.alcatrazm.plugin.grapplinghook;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Locale;

public class GrapplingHookListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final GrapplingHookManager manager;

    public GrapplingHookListener(AlcatrazPlugin plugin, GrapplingHookManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!manager.isGrapplingHook(item)) {
            item = player.getInventory().getItemInOffHand();
            if (!manager.isGrapplingHook(item)) return;
        }

        PlayerFishEvent.State state = event.getState();
        // Trigger pull on reel in, in ground or caught entity
        if (state == PlayerFishEvent.State.IN_GROUND || state == PlayerFishEvent.State.REEL_IN || state == PlayerFishEvent.State.CAUGHT_ENTITY) {
            FishHook hook = event.getHook();
            Location hookLoc = hook.getLocation();
            Location playerLoc = player.getLocation();

            double distance = hookLoc.distance(playerLoc);
            if (distance < 1.5) return;

            if (manager.isOnCooldown(player)) {
                double remaining = manager.getRemainingCooldownSeconds(player);
                player.sendMessage(color("&c¡Gancho en cooldown! Espera &e" + String.format(Locale.US, "%.1f", remaining) + "s&c."));
                return;
            }

            // Calculate impulse vector
            Vector diff = hookLoc.toVector().subtract(playerLoc.toVector());
            Vector velocity = diff.normalize().multiply(manager.getPullMultiplier());
            velocity.setY(Math.min(velocity.getY() + manager.getVerticalBoost(), 1.35));

            player.setVelocity(velocity);

            // Audio visual effects
            player.getWorld().playSound(playerLoc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.2f);
            player.getWorld().playSound(playerLoc, Sound.ITEM_CROSSBOW_SHOOT, 0.8f, 1.4f);
            player.getWorld().spawnParticle(Particle.CLOUD, playerLoc.clone().add(0, 0.5, 0), 10, 0.2, 0.2, 0.2, 0.05);

            // Protection and cooldown
            manager.setFallProtected(player);
            manager.applyCooldown(player);
            manager.decrementUses(player, item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        if (manager.isFallProtected(player)) {
            event.setCancelled(true);
            manager.removeFallProtection(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.cleanPlayer(event.getPlayer().getUniqueId());
    }
}
