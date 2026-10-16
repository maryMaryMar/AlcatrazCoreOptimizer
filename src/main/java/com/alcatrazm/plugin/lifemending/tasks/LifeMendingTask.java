package com.alcatrazm.plugin.lifemending.tasks;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.lifemending.managers.LifeMendingManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.List;

public class LifeMendingTask implements Runnable {

    private final AlcatrazPlugin plugin;
    private final LifeMendingManager manager;

    public LifeMendingTask(AlcatrazPlugin plugin, LifeMendingManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void run() {
        boolean enabled = plugin.getConfig().getBoolean("life-mending.enabled", true);
        if (!enabled) return;

        int durabilityPerCycle = plugin.getConfig().getInt("life-mending.durability-per-cycle", 1);
        double minHealth = plugin.getConfig().getDouble("life-mending.min-health-threshold", 4.0);
        double healthCost = plugin.getConfig().getDouble("life-mending.health-cost-per-durability", 0.5);
        float satCost = (float) plugin.getConfig().getDouble("life-mending.saturation-cost-per-durability", 0.5);
        int foodCost = plugin.getConfig().getInt("life-mending.food-cost-per-durability", 1);
        String mode = plugin.getConfig().getString("life-mending.consumption-mode", "SATURATION_THEN_HEALTH").toUpperCase();
        boolean checkInventory = plugin.getConfig().getBoolean("life-mending.repair-all-inventory-items", false);
        boolean particles = plugin.getConfig().getBoolean("life-mending.particles-enabled", true);
        boolean playSound = plugin.getConfig().getBoolean("life-mending.sound-enabled", true);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isValid() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) continue;

            int totalRepairedThisCycle = 0;

            // 1. Armor
            ItemStack[] armor = player.getInventory().getArmorContents();
            boolean armorChanged = false;
            for (int i = 0; i < armor.length; i++) {
                ItemStack item = armor[i];
                if (item != null && manager.hasLifeMending(item) && isDamaged(item)) {
                    if (payCost(player, mode, healthCost, minHealth, satCost, foodCost)) {
                        int rep = manager.repairDurability(item, durabilityPerCycle);
                        if (rep > 0) {
                            armorChanged = true;
                            totalRepairedThisCycle += rep;
                        }
                    } else {
                        break;
                    }
                }
            }
            if (armorChanged) {
                player.getInventory().setArmorContents(armor);
            }

            // 2. Main hand
            ItemStack main = player.getInventory().getItemInMainHand();
            if (main != null && manager.hasLifeMending(main) && isDamaged(main)) {
                if (payCost(player, mode, healthCost, minHealth, satCost, foodCost)) {
                    int rep = manager.repairDurability(main, durabilityPerCycle);
                    if (rep > 0) {
                        player.getInventory().setItemInMainHand(main);
                        totalRepairedThisCycle += rep;
                    }
                }
            }

            // 3. Off hand
            ItemStack off = player.getInventory().getItemInOffHand();
            if (off != null && manager.hasLifeMending(off) && isDamaged(off)) {
                if (payCost(player, mode, healthCost, minHealth, satCost, foodCost)) {
                    int rep = manager.repairDurability(off, durabilityPerCycle);
                    if (rep > 0) {
                        player.getInventory().setItemInOffHand(off);
                        totalRepairedThisCycle += rep;
                    }
                }
            }

            // 4. Inventory storage
            if (checkInventory) {
                int held = player.getInventory().getHeldItemSlot();
                for (int slot = 0; slot < 36; slot++) {
                    if (slot == held) continue;
                    ItemStack item = player.getInventory().getItem(slot);
                    if (item != null && manager.hasLifeMending(item) && isDamaged(item)) {
                        if (payCost(player, mode, healthCost, minHealth, satCost, foodCost)) {
                            int rep = manager.repairDurability(item, durabilityPerCycle);
                            if (rep > 0) {
                                player.getInventory().setItem(slot, item);
                                totalRepairedThisCycle += rep;
                            }
                        } else {
                            break;
                        }
                    }
                }
            }

            if (totalRepairedThisCycle > 0) {
                Location loc = player.getLocation().add(0, 1.0, 0);
                if (particles) {
                    try {
                        player.getWorld().spawnParticle(Particle.HEART, loc, 2, 0.3, 0.3, 0.3, 0.05);
                        player.getWorld().spawnParticle(Particle.CRIMSON_SPORE, loc, 5, 0.2, 0.4, 0.2, 0.02);
                    } catch (Throwable ignored) {}
                }
                if (playSound) {
                    try {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.8f);
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    private boolean payCost(Player player, String mode, double healthCost, double minHealth, float satCost, int foodCost) {
        if (player.getGameMode() == GameMode.CREATIVE) return true;
        double currentHealth = player.getHealth();
        float currentSat = player.getSaturation();
        int currentFood = player.getFoodLevel();

        switch (mode) {
            case "SATURATION_THEN_HEALTH":
                if (currentSat >= satCost) {
                    player.setSaturation(Math.max(0.0f, currentSat - satCost));
                    return true;
                } else if (currentFood > 1) {
                    player.setFoodLevel(Math.max(0, currentFood - foodCost));
                    return true;
                } else if (currentHealth - healthCost >= minHealth) {
                    player.setHealth(Math.max(1.0, currentHealth - healthCost));
                    player.playHurtAnimation(0.0f);
                    return true;
                }
                return false;

            case "HEALTH_AND_SATURATION":
                if (currentHealth - healthCost >= minHealth) {
                    player.setHealth(Math.max(1.0, currentHealth - healthCost));
                    if (currentSat >= satCost) {
                        player.setSaturation(Math.max(0.0f, currentSat - satCost));
                    } else if (currentFood > 0) {
                        player.setFoodLevel(Math.max(0, currentFood - foodCost));
                    }
                    player.playHurtAnimation(0.0f);
                    return true;
                }
                return false;

            case "HEALTH_ONLY":
                if (currentHealth - healthCost >= minHealth) {
                    player.setHealth(Math.max(1.0, currentHealth - healthCost));
                    player.playHurtAnimation(0.0f);
                    return true;
                }
                return false;

            case "SATURATION_ONLY":
                if (currentSat >= satCost) {
                    player.setSaturation(Math.max(0.0f, currentSat - satCost));
                    return true;
                } else if (currentFood > 1) {
                    player.setFoodLevel(Math.max(0, currentFood - foodCost));
                    return true;
                }
                return false;

            default: // Fallback to SATURATION_THEN_HEALTH
                if (currentSat >= satCost) {
                    player.setSaturation(Math.max(0.0f, currentSat - satCost));
                    return true;
                } else if (currentHealth - healthCost >= minHealth) {
                    player.setHealth(Math.max(1.0, currentHealth - healthCost));
                    player.playHurtAnimation(0.0f);
                    return true;
                }
                return false;
        }
    }

    private boolean isDamaged(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (item.getItemMeta() instanceof Damageable damageable) {
            return damageable.getDamage() > 0;
        }
        return false;
    }
}
