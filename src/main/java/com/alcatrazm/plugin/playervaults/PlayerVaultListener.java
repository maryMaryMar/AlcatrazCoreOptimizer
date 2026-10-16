package com.alcatrazm.plugin.playervaults;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class PlayerVaultListener implements Listener {

    private final PlayerVaultManager vaultManager;

    public PlayerVaultListener(PlayerVaultManager vaultManager) {
        this.vaultManager = vaultManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (vaultManager.isVaultInventory(event.getInventory())) {
            vaultManager.saveVault(event.getInventory());
        }
    }
}
