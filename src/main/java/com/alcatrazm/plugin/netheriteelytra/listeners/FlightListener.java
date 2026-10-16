package com.alcatrazm.plugin.netheriteelytra.listeners;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.netheriteelytra.managers.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FlightListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final ItemManager itemManager;
    // Registra ÚNICAMENTE a los jugadores a los que este plugin les dio vuelo temporal para el doble salto
    private final Set<UUID> elytraFlightEnabled = new HashSet<>();

    public FlightListener(AlcatrazPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    public void clearAll() {
        elytraFlightEnabled.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Si el jugador está volando legítimamente con /fly (Essentials u otros plugins), no interferir
        if (!elytraFlightEnabled.contains(player.getUniqueId())) {
            return;
        }

        ItemStack chestplate = player.getInventory().getChestplate();
        if (itemManager.isElytraChestplate(chestplate)) {
            event.setCancelled(true);
            elytraFlightEnabled.remove(player.getUniqueId());
            player.setAllowFlight(false);

            if (!hasEnoughDurability(chestplate)) {
                String msg = plugin.getConfig().getString("messages-elytra.chestplate-broken", "&c¡Tu pechera tiene muy poca durabilidad para volar!");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages-elytra.prefix", "") + msg));
                return;
            }

            player.setGliding(true);

            Vector dir = player.getLocation().getDirection();
            Vector boost = dir.multiply(0.85);
            boost.setY(Math.max(boost.getY(), 0.15));
            player.setVelocity(boost);

            player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack chestplate = player.getInventory().getChestplate();
        if (itemManager.isElytraChestplate(chestplate)) {
            if (!event.isGliding()) {
                if (!player.isOnGround() && !player.isInWater() && !player.isInsideVehicle() && hasEnoughDurability(chestplate)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.FIREWORK_ROCKET) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack chestplate = player.getInventory().getChestplate();
        if (itemManager.isElytraChestplate(chestplate) && player.isGliding()) {
            event.setCancelled(true);

            if (!hasEnoughDurability(chestplate)) {
                player.setGliding(false);
                String msg = plugin.getConfig().getString("messages-elytra.chestplate-broken", "&c¡Tu pechera tiene muy poca durabilidad para volar!");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages-elytra.prefix", "") + msg));
                return;
            }

            double multiplier = plugin.getConfig().getDouble("flight.boost-velocity-multiplier", 1.65);
            Vector dir = player.getLocation().getDirection();
            player.setVelocity(dir.multiply(multiplier));

            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation(), 25, 0.25, 0.25, 0.25, 0.05);

            // Consumir cohetes pirotécnicos de manera infalible
            if (player.getGameMode() != GameMode.CREATIVE && plugin.getConfig().getBoolean("flight.consume-fireworks", true)) {
                consumeRocket(player, event.getHand());
            }

            int boostDamage = plugin.getConfig().getInt("flight.durability-loss-on-boost", 1);
            if (boostDamage > 0 && player.getGameMode() != GameMode.CREATIVE) {
                damageChestplate(player, chestplate, boostDamage);
            }
        }
    }

    private void consumeRocket(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND) {
            ItemStack off = player.getInventory().getItemInOffHand();
            if (off != null && off.getType() == Material.FIREWORK_ROCKET) {
                int newAmount = off.getAmount() - 1;
                if (newAmount > 0) {
                    off.setAmount(newAmount);
                    player.getInventory().setItemInOffHand(off);
                } else {
                    player.getInventory().setItemInOffHand(null);
                }
            }
        } else {
            ItemStack main = player.getInventory().getItemInMainHand();
            if (main != null && main.getType() == Material.FIREWORK_ROCKET) {
                int newAmount = main.getAmount() - 1;
                if (newAmount > 0) {
                    main.setAmount(newAmount);
                    player.getInventory().setItemInMainHand(main);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
            } else {
                ItemStack off = player.getInventory().getItemInOffHand();
                if (off != null && off.getType() == Material.FIREWORK_ROCKET) {
                    int newAmount = off.getAmount() - 1;
                    if (newAmount > 0) {
                        off.setAmount(newAmount);
                        player.getInventory().setItemInOffHand(off);
                    } else {
                        player.getInventory().setItemInOffHand(null);
                    }
                }
            }
        }
        player.updateInventory();
        plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Si el jugador ya está volando con /fly (modo vuelo normal), NUNCA interferir
        if (player.isFlying() && !elytraFlightEnabled.contains(player.getUniqueId())) {
            return;
        }

        ItemStack chestplate = player.getInventory().getChestplate();
        boolean hasElytraChest = itemManager.isElytraChestplate(chestplate);

        if (hasElytraChest && hasEnoughDurability(chestplate)) {
            if (!player.isOnGround() && !player.isInWater() && !player.isClimbing() && !player.isGliding() && !player.isFlying()) {
                if (plugin.getConfig().getBoolean("flight.double-jump-to-glide", true)) {
                    if (!player.getAllowFlight()) {
                        elytraFlightEnabled.add(player.getUniqueId());
                        player.setAllowFlight(true);
                    }
                }
            } else if (player.isOnGround()) {
                if (elytraFlightEnabled.remove(player.getUniqueId())) {
                    player.setAllowFlight(false);
                }
                if (player.isGliding()) {
                    player.setGliding(false);
                }
            }
        } else {
            if (elytraFlightEnabled.remove(player.getUniqueId())) {
                player.setAllowFlight(false);
            }
            if (player.isGliding() && !hasElytraChest) {
                ItemStack currentChest = player.getInventory().getChestplate();
                if (currentChest == null || currentChest.getType() != Material.ELYTRA) {
                    player.setGliding(false);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        elytraFlightEnabled.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        elytraFlightEnabled.remove(event.getPlayer().getUniqueId());
    }

    public boolean hasEnoughDurability(ItemStack chestplate) {
        if (chestplate == null || !chestplate.hasItemMeta()) return false;
        ItemMeta meta = chestplate.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int max = chestplate.getType().getMaxDurability();
            int remaining = max - damageable.getDamage();
            int minRequired = plugin.getConfig().getInt("flight.min-durability-to-glide", 5);
            return remaining > minRequired;
        }
        return true;
    }

    public void damageChestplate(Player player, ItemStack chestplate, int amount) {
        ItemMeta meta = chestplate.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int unbreaking = meta.getEnchantLevel(Enchantment.UNBREAKING);
            int actualDamage = 0;
            for (int i = 0; i < amount; i++) {
                double chanceToNegate = unbreaking > 0 ? (0.6 + (0.4 / (unbreaking + 1.0))) : 0.0;
                if (Math.random() > chanceToNegate) {
                    actualDamage++;
                }
            }

            if (actualDamage > 0) {
                int newDamage = damageable.getDamage() + actualDamage;
                int max = chestplate.getType().getMaxDurability();
                if (newDamage >= max) {
                    player.getInventory().setChestplate(null);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                    player.setGliding(false);
                } else {
                    damageable.setDamage(newDamage);
                    chestplate.setItemMeta(damageable);
                }
            }
        }
    }
}
