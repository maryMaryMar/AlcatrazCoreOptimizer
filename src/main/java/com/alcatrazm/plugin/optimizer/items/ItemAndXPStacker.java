package com.alcatrazm.plugin.optimizer.items;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Optimizador masivo de Ítems, Fusión de Experiencia (XP Stacking) y Limpieza Rápida de Flechas clavadas.
 */
public class ItemAndXPStacker implements Listener {

    private final AlcatrazPlugin plugin;

    public ItemAndXPStacker(AlcatrazPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        // No junk list needed
    }

    // 1. Fusión y Optimización de Experiencia (XP Stacker)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onXPSpawn(EntitySpawnEvent event) {
        if (!plugin.getConfig().getBoolean("optimizer.xp-stacker.enabled", true)) return;
        if (!(event.getEntity() instanceof ExperienceOrb newOrb)) return;

        double mergeRadius = plugin.getConfig().getDouble("optimizer.xp-stacker.merge-radius", 4.0);
        double radiusSq = mergeRadius * mergeRadius;

        for (Entity nearby : newOrb.getWorld().getNearbyEntities(newOrb.getLocation(), mergeRadius, mergeRadius, mergeRadius)) {
            if (nearby instanceof ExperienceOrb existing && existing != newOrb && existing.isValid()) {
                if (existing.getLocation().distanceSquared(newOrb.getLocation()) <= radiusSq) {
                    existing.setExperience(existing.getExperience() + newOrb.getExperience());
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // 2. Fusión de Ítems en el suelo (Item Stacker)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("optimizer.items.stacker-enabled", true)) return;

        Item newItem = event.getEntity();
        ItemStack stack = newItem.getItemStack();

        int maxStack = stack.getMaxStackSize();
        if (maxStack <= 1 || stack.getAmount() >= maxStack) return;

        double radius = plugin.getConfig().getDouble("optimizer.items.merge-radius", 3.0);
        double radiusSq = radius * radius;

        for (Entity nearby : newItem.getWorld().getNearbyEntities(newItem.getLocation(), radius, radius, radius)) {
            if (nearby instanceof Item existing && existing != newItem && existing.isValid()) {
                if (existing.getLocation().distanceSquared(newItem.getLocation()) <= radiusSq) {
                    ItemStack existingStack = existing.getItemStack();
                    if (existingStack.isSimilar(stack)) {
                        int total = existingStack.getAmount() + stack.getAmount();
                        if (total <= maxStack) {
                            existingStack.setAmount(total);
                            existing.setItemStack(existingStack);
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    // 3. Limpieza Rápida de Flechas y Proyectiles Clavados en el Suelo
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!plugin.getConfig().getBoolean("optimizer.projectiles.fast-despawn-arrows", true)) return;

        Entity entity = event.getEntity();
        // Trident extiende AbstractArrow: nunca borrar tridentes lanzados (pérdida de ítem del jugador)
        if (entity instanceof Trident) return;
        if (entity instanceof AbstractArrow arrow) {
            int lifetimeTicks = plugin.getConfig().getInt("optimizer.projectiles.arrow-lifetime-ticks", 100); // 5 segundos
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (arrow.isValid() && !arrow.isDead() && arrow.isInBlock()) {
                    arrow.remove();
                }
            }, (long) lifetimeTicks);
        }
    }
}
