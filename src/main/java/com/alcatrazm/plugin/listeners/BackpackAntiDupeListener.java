package com.alcatrazm.plugin.listeners;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class BackpackAntiDupeListener implements Listener {

    private final AlcatrazPlugin plugin;

    public BackpackAntiDupeListener(AlcatrazPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Comprueba si un inventario es una mochila (BackpackPlus u otros plugins de mochilas).
     */
    private boolean isBackpackInventory(InventoryView view) {
        if (view == null) return false;
        String title = view.getTitle().toLowerCase();
        return title.contains("backpack") || title.contains("ʙᴀᴄᴋᴘᴀᴄᴋ") || title.contains("mochila");
    }

    /**
     * Comprueba si un ItemStack es una mochila física.
     */
    private boolean isBackpackItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        // BackpackPlus usa FLINT con CustomModelData 7, 8 o 9
        if (item.getType() == Material.FLINT && meta.hasCustomModelData()) {
            int cmd = meta.getCustomModelData();
            if (cmd == 7 || cmd == 8 || cmd == 9) {
                return true;
            }
        }

        // Detección por nombre o lore
        if (meta.hasDisplayName()) {
            String name = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();
            if (name.contains("backpack") || name.contains("mochila") || name.contains("ʙᴀᴄᴋᴘᴀᴄᴋ")) {
                return true;
            }
        }

        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryView view = event.getView();
        if (!isBackpackInventory(view)) return;

        ClickType click = event.getClick();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // 1. Bloquear clics fuera de la ventana para evitar dropeo con la GUI abierta
        if (event.getSlotType() == InventoryType.SlotType.OUTSIDE) {
            event.setCancelled(true);
            return;
        }

        // 2. Bloquear dropeo con tecla Q o Ctrl+Q mientras la mochila está abierta
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            if (isBackpackItem(current)) {
                event.setCancelled(true);
                return;
            }
        }

        // 3. Bloquear intercambio de hotbar con teclas numéricas 1-9 sobre la mochila
        if (click == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot >= 0 && hotbarSlot < 9) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                if (isBackpackItem(hotbarItem) || isBackpackItem(current)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // 3b. Bloquear la tecla F (intercambio con mano secundaria) si involucra una mochila
        if (click == ClickType.SWAP_OFFHAND) {
            if (isBackpackItem(player.getInventory().getItemInOffHand()) || isBackpackItem(current)) {
                event.setCancelled(true);
                return;
            }
        }

        // 4. Bloquear mover o hacer clic en la propia mochila dentro del inventario inferior
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            if (isBackpackItem(current)) {
                event.setCancelled(true);
                return;
            }
        }

        // 5. Bloquear colocar una mochila dentro de otra mochila (anidación)
        Inventory topInv = view.getTopInventory();
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(topInv)) {
            if (isBackpackItem(cursor) || (click.isShiftClick() && isBackpackItem(current))) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[Seguridad] ¡No puedes guardar una mochila dentro de otra mochila!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        InventoryView view = event.getView();
        if (!isBackpackInventory(view)) return;
        if (!isBackpackItem(event.getOldCursor())) return;

        // Arrastrar una mochila sobre slots de la propia mochila (anidación) está prohibido
        int topSize = view.getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        InventoryView openView = player.getOpenInventory();

        if (isBackpackInventory(openView)) {
            ItemStack dropped = event.getItemDrop().getItemStack();
            if (isBackpackItem(dropped)) {
                event.setCancelled(true);
                player.closeInventory();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        InventoryView openView = player.getOpenInventory();

        if (isBackpackInventory(openView)) {
            event.setCancelled(true);
            player.closeInventory();
        }
    }
}
