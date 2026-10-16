package com.alcatrazm.plugin.lifemending.listeners;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.lifemending.managers.LifeMendingManager;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

public class LifeMendingListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final LifeMendingManager manager;

    public LifeMendingListener(AlcatrazPlugin plugin, LifeMendingManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack first = inv.getItem(0);
        ItemStack second = inv.getItem(1);

        if (second == null) return;

        if (manager.isLifeMendingBook(second)) {
            if (first != null && manager.isDamageableItem(first)) {
                if (manager.hasLifeMending(first)) {
                    // Item already has Life Mending
                    return;
                }

                ItemStack result = manager.applyLifeMending(first);

                String renameText = "";
                try {
                    if (event.getView() instanceof AnvilView anvilView) {
                        renameText = anvilView.getRenameText();
                    }
                } catch (Throwable ignored) {}

                if (renameText != null && !renameText.trim().isEmpty() && result != null) {
                    ItemMeta meta = result.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', renameText));
                        result.setItemMeta(meta);
                    }
                }

                event.setResult(result);
                int cost = plugin.getConfig().getInt("life-mending.anvil.exp-level-cost", 5);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        event.getView().setRepairCost(cost);
                    } catch (Throwable ignored) {}
                });
            } else {
                event.setResult(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.ANVIL) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        AnvilInventory inv = (AnvilInventory) event.getInventory();

        if (event.getRawSlot() == 2) {
            ItemStack first = inv.getItem(0);
            ItemStack second = inv.getItem(1);
            ItemStack result = event.getCurrentItem();

            if (second != null && manager.isLifeMendingBook(second)) {
                if (first == null || !manager.isDamageableItem(first)) {
                    event.setCancelled(true);
                    return;
                }

                int cost = plugin.getConfig().getInt("life-mending.anvil.exp-level-cost", 5);
                if (player.getGameMode() != GameMode.CREATIVE && player.getLevel() < cost) {
                    event.setCancelled(true);
                    return;
                }

                // En Minecraft Vanilla, retirar el ítem del slot 2 (resultado) descuenta automáticamente
                // los niveles de experiencia configurados en setRepairCost(). No se debe restar de nuevo
                // manualmente con player.setLevel() para evitar cobro doble (BUG-08).
                if (result != null && manager.hasLifeMending(result)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                }
            }
        }
    }
}
