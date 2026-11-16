package com.alcatrazm.plugin.airdrop;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Maneja interacciones de jugadores, proteccion de entidades y recoleccion de botin de AirDrops.
 */
public final class DropListener implements Listener {
    private final AirDropModule module;

    public DropListener(AirDropModule module) {
        this.module = module;
    }

    private boolean canPlay(Player player) {
        return player.hasPermission("alcatraz.airdrop.play")
                || player.hasPermission("universairdrop.play")
                || player.hasPermission("alcatraz.admin");
    }

    private boolean isWithinReach(Player player, DropInstance drop) {
        double maxDist = this.module.getDouble("safe.interaction-distance", 4.5, 1.5, 16.0);
        return player.getWorld() == drop.location.getWorld() && player.getLocation().distanceSquared(drop.location) <= (maxDist * maxDist);
    }

    public void openDrop(Player player, DropInstance drop) {
        if (canPlay(player) && isWithinReach(player, drop) && !this.module.getCinematicManager().isInCinematic(player.getUniqueId())) {
            if (!drop.isInteractable()) {
                this.module.getMessageFormatter().sendMessage(player, this.module.getMessageFormatter().detail(MessageFormatter.toSmallCaps("Espera a que el drop aterrice")));
            } else {
                player.openInventory(drop.inventory);
                if (player.getOpenInventory().getTopInventory() == drop.inventory) {
                    drop.opened = true;
                    this.module.getMessageFormatter().playSound(player, Sound.BLOCK_CHEST_OPEN, 0.65F, 1.1F);
                    this.module.getMessageFormatter().sendMessage(player, this.module.getMessageFormatter().detail(MessageFormatter.toSmallCaps("Click en un objeto para recogerlo")));
                }
            }
        }
    }

    public void closeInvalidInventories() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof DropInstance drop) {
                if (drop.state == DropInstance.State.REMOVED || !isWithinReach(p, drop) || !canPlay(p)) {
                    p.closeInventory();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof DropInstance drop && event.getPlayer() instanceof Player player) {
            if (!canPlay(player) || !drop.isInteractable() || !isWithinReach(player, drop) || this.module.getCinematicManager().isInCinematic(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        DropInstance drop = this.module.getAirDropManager().findByEntity(event.getRightClicked());
        if (drop != null) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                this.openDrop(event.getPlayer(), drop);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        DropInstance drop = this.module.getAirDropManager().findByEntity(event.getRightClicked());
        if (drop != null) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                this.openDrop(event.getPlayer(), drop);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrePlayerAttack(PrePlayerAttackEntityEvent event) {
        if (this.module.getAirDropManager().findByEntity(event.getAttacked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (this.module.getAirDropManager().findByEntity(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (this.module.getAirDropManager().findByEntity(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DropInstance drop) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && canPlay(player) && drop.canAccess(player.getUniqueId()) && isWithinReach(player, drop)) {
                if (event.getRawSlot() >= 0 && event.getRawSlot() < drop.inventory.getSize()) {
                    if ((event.isLeftClick() || event.isRightClick()) && event.getClick() != ClickType.DOUBLE_CLICK) {
                        ItemStack item = drop.inventory.getItem(event.getRawSlot());
                        if (item != null && !item.getType().isAir()) {
                            int amountToTake = event.isRightClick() && !event.isShiftClick() ? (item.getAmount() + 1) / 2 : item.getAmount();
                            ItemStack toAdd = item.clone();
                            toAdd.setAmount(amountToTake);
                            int leftover = player.getInventory().addItem(toAdd).values().stream().mapToInt(ItemStack::getAmount).sum();
                            int taken = amountToTake - leftover;
                            if (taken <= 0) {
                                this.module.getMessageFormatter().sendMessage(player, this.module.getMessageFormatter().error(MessageFormatter.toSmallCaps("No tienes espacio en el inventario")));
                            } else {
                                ItemStack updated = item.clone();
                                updated.setAmount(item.getAmount() - taken);
                                drop.inventory.setItem(event.getRawSlot(), updated.getAmount() == 0 ? null : updated);

                                Component itemName = (item.getItemMeta() != null && item.getItemMeta().hasDisplayName())
                                    ? item.getItemMeta().displayName()
                                    : Component.translatable(item.translationKey());

                                Component message = this.module.getMessageFormatter().success(player.getName() + " RECOGIO ")
                                    .append(itemName != null ? itemName : Component.text(item.getType().name()))
                                    .append(this.module.getMessageFormatter().detail(" x" + taken + " | "))
                                    .append(this.module.getMessageFormatter().gradient(drop.displayName + " " + drop.id));

                                this.module.getMessageFormatter().broadcast(message);
                                this.module.getMessageFormatter().playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.7F, 1.1F);

                                if (drop.isEmpty()) {
                                    Bukkit.getScheduler().runTask(this.module.getPlugin(), () -> this.module.getAirDropManager().removeDrop(drop, "recogido"));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DropInstance) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        this.module.getAirDropManager().handleChunkLoad(event.getChunk());
    }
}
