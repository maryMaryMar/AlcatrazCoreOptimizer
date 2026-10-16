package com.alcatrazm.plugin.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class DuelMenuListener implements Listener {

    private final DuelMenuManager manager;

    public DuelMenuListener(DuelMenuManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().trim();
        if (!msg.startsWith("/")) return;

        String withoutSlash = msg.substring(1).trim();
        String[] parts = withoutSlash.split("\\s+");
        if (parts.length == 0) return;

        String cmd = parts[0].toLowerCase();
        if (cmd.equals("duel") || cmd.equals("duels") || cmd.equals("duelo") || cmd.equals("duelos")
                || cmd.equals("versus:duel") || cmd.equals("versus:duels")) {

            Player player = event.getPlayer();

            if (manager.isBypassing(player.getUniqueId())) {
                return;
            }

            if (parts.length > 1) {
                String sub = parts[1].toLowerCase();
                if (sub.equals("accept")) {
                    DuelMenuManager.PendingChallenge challenge = manager.getPendingChallenge(player.getUniqueId());
                    if (challenge != null) {
                        manager.prepareArenaForModality(challenge.modalityId());
                        Bukkit.getScheduler().runTaskLater(manager.getPlugin(), manager::restoreAllArenas, 10L);
                    }
                    return;
                }
                if (sub.equals("deny") || sub.equals("cancel")) {
                    manager.removePendingChallenge(player.getUniqueId());
                    return;
                }
            }

            event.setCancelled(true);
            manager.openModalityMenu(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String viewTitle = event.getView().getTitle();
        String modalityTitle = ChatColor.translateAlternateColorCodes('&', DuelMenuManager.MODALITY_TITLE);
        String playerTitlePrefix = ChatColor.translateAlternateColorCodes('&', DuelMenuManager.PLAYER_SELECTION_TITLE_PREFIX);

        if (viewTitle.equals(modalityTitle)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                return;
            }

            int slot = event.getRawSlot();
            int[] modSlots = {11, 12, 13, 14, 15};
            for (int i = 0; i < modSlots.length; i++) {
                if (slot == modSlots[i] && i < manager.getModalities().size()) {
                    DuelMenuManager.Modality mod = manager.getModalities().get(i);
                    manager.openPlayerMenu(player, mod.id(), 0);
                    return;
                }
            }

            if (slot == 22) {
                player.closeInventory();
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&b&lESPECTADOR&8] &7Abriendo lista de partidas en curso..."));
                player.performCommand("versus:spectate");
            }
            return;
        }

        if (viewTitle.startsWith(playerTitlePrefix)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) {
                return;
            }

            int slot = event.getRawSlot();
            String modality = manager.getSelectedModality(player.getUniqueId());
            if (modality == null) modality = "potpvp";
            int currentPage = manager.getCurrentPage(player.getUniqueId());

            if (slot == 45) { // Volver
                manager.openModalityMenu(player);
                return;
            }

            if (slot == 48 && clicked.getType() == Material.PAPER) { // Pagina anterior
                manager.openPlayerMenu(player, modality, Math.max(0, currentPage - 1));
                return;
            }

            if (slot == 49) { // Actualizar
                manager.openPlayerMenu(player, modality, currentPage);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.5f);
                return;
            }

            if (slot == 50 && clicked.getType() == Material.PAPER) { // Pagina siguiente
                manager.openPlayerMenu(player, modality, currentPage + 1);
                return;
            }

            if (slot == 53) { // Cerrar
                player.closeInventory();
                return;
            }

            if (slot < 45 && clicked.getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) clicked.getItemMeta();
                if (meta != null && meta.getOwningPlayer() != null) {
                    Player target = meta.getOwningPlayer().getPlayer();
                    if (target == null || !target.isOnline()) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cEse jugador ya no está conectado."));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                        manager.openPlayerMenu(player, modality, currentPage);
                        return;
                    }

                    manager.sendDuelChallenge(player, target, modality);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        String viewTitle = event.getView().getTitle();
        String modalityTitle = ChatColor.translateAlternateColorCodes('&', DuelMenuManager.MODALITY_TITLE);
        String playerTitlePrefix = ChatColor.translateAlternateColorCodes('&', DuelMenuManager.PLAYER_SELECTION_TITLE_PREFIX);

        if (viewTitle.equals(modalityTitle) || viewTitle.startsWith(playerTitlePrefix)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.cleanupPlayer(event.getPlayer().getUniqueId());
    }
}
