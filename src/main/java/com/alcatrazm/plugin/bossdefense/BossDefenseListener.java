package com.alcatrazm.plugin.bossdefense;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class BossDefenseListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final BossDefenseManager manager;

    public BossDefenseListener(AlcatrazPlugin plugin, BossDefenseManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        Entity victimEntity = event.getEntity();
        if (!(victimEntity instanceof LivingEntity victim)) {
            return;
        }

        // Solo aplicar si la víctima es identificada como Boss
        if (!manager.isBoss(victim)) {
            return;
        }

        Entity damager = event.getDamager();
        DamageCause cause = event.getCause();

        boolean isProjectile = (cause == DamageCause.PROJECTILE) || (damager instanceof Projectile);
        boolean isSword = false;
        Player attackerPlayer = null;

        if (isProjectile) {
            // Si es poción y no se incluyen pociones, ignorar
            if (damager instanceof ThrownPotion && !manager.isIncludePotions()) {
                return;
            }
            if (!manager.isProjectileDefenseEnabled()) {
                return;
            }
            if (damager instanceof Projectile projectile) {
                if (projectile.getShooter() instanceof Player p) {
                    attackerPlayer = p;
                }
            } else if (damager instanceof Player p) {
                attackerPlayer = p;
            }
        } else if (damager instanceof Player player) {
            attackerPlayer = player;
            if (manager.isSwordDefenseEnabled()) {
                ItemStack weapon = player.getInventory().getItemInMainHand();
                if (manager.isSwordOrMelee(weapon)) {
                    isSword = true;
                }
            }
        }

        // Si no fue ni proyectil ni espada/cuerpo a cuerpo contemplado, no reducir
        if (!isProjectile && !isSword) {
            return;
        }

        double originalDamage = event.getDamage();
        if (originalDamage <= 0) {
            return;
        }

        // Reducción del daño (ej. 75% -> multiplicador 0.25)
        double reductionPercentage = isProjectile
                ? manager.getProjectileReductionPercentage()
                : manager.getSwordReductionPercentage();

        double reduction = reductionPercentage / 100.0;
        double multiplier = Math.max(0.0, 1.0 - reduction);
        double finalDamage = originalDamage * multiplier;

        event.setDamage(finalDamage);

        manager.playDefenseEffects(victim, attackerPlayer, originalDamage, finalDamage, isProjectile);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.cleanupPlayer(event.getPlayer().getUniqueId());
    }
}
