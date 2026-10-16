package com.alcatrazm.plugin.trash;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

public class TrashManager implements Listener {

    private final String trashTitle;
    private final Set<Inventory> openTrashInventories = new HashSet<>();

    public TrashManager() {
        this.trashTitle = ChatColor.translateAlternateColorCodes('&', "&8[&cPapelera / Basurero&8]");
    }

    public void openTrash(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, trashTitle);
        openTrashInventories.add(inv);
        player.openInventory(inv);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&cPapelera&8] &7Coloca los objetos que desees destruir permanentemente y cierra el menú."));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (openTrashInventories.remove(event.getInventory())) {
            boolean hadItems = false;
            for (ItemStack item : event.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    hadItems = true;
                    break;
                }
            }

            event.getInventory().clear();

            if (hadItems && event.getPlayer() instanceof Player player) {
                player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.7f, 1.2f);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&cPapelera&8] &a¡Objetos destruidos permanentemente sin generar lag!"));
            }
        }
    }
}
