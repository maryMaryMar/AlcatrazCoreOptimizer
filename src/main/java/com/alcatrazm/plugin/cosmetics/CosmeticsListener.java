package com.alcatrazm.plugin.cosmetics;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.announcer.AutoAnnouncer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class CosmeticsListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final CosmeticsManager manager;

    public CosmeticsListener(AlcatrazPlugin plugin, CosmeticsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView() == null || event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().equals(CosmeticsGUI.GUI_TITLE)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        if (slot == 11) {
            handleCosmeticClick(player, CosmeticType.BLAZE_SWORDS);
        } else if (slot == 15) {
            handleCosmeticClick(player, CosmeticType.TENEBRIS_HALO);
        } else if (slot == 22) {
            manager.removeCosmetic(player, true);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            CosmeticsGUI.open(player, manager);
        }
    }

    private void handleCosmeticClick(Player player, CosmeticType type) {
        if (!manager.hasPermission(player, type)) {
            player.sendMessage(AutoAnnouncer.colorize("&8[&bCosméticos&8] &cNo tienes desbloqueado este cosmético."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        manager.equipCosmetic(player, type);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        // Refrescar el menú para actualizar estado visual
        CosmeticsGUI.open(player, manager);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.removeCosmeticByUUID(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        manager.restoreOnRespawn(event.getPlayer());
    }
}
