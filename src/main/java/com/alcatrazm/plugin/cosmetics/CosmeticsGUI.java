package com.alcatrazm.plugin.cosmetics;

import com.alcatrazm.plugin.announcer.AutoAnnouncer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class CosmeticsGUI {

    public static final String GUI_TITLE = AutoAnnouncer.colorize("&8» &#31d8d7&lCOSMÉTICOS MÍSTICOS &8«");

    public static void open(Player player, CosmeticsManager manager) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        // Decoración bordes con panel gris oscuro
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.setDisplayName(" ");
            border.setItemMeta(borderMeta);
        }
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || (i + 1) % 9 == 0) {
                inv.setItem(i, border);
            }
        }

        CosmeticType active = manager.getActiveCosmetic(player.getUniqueId());

        // Slot 11: Espadas del Blaze
        inv.setItem(11, createCosmeticItem(player, manager, CosmeticType.BLAZE_SWORDS, active == CosmeticType.BLAZE_SWORDS));

        // Slot 15: Halo de Tenebris
        inv.setItem(15, createCosmeticItem(player, manager, CosmeticType.TENEBRIS_HALO, active == CosmeticType.TENEBRIS_HALO));

        // Slot 22: Desequipar
        ItemStack clearItem = new ItemStack(Material.BARRIER);
        ItemMeta clearMeta = clearItem.getItemMeta();
        if (clearMeta != null) {
            clearMeta.setDisplayName(AutoAnnouncer.colorize("&c&lDesequipar Cosmético Actual"));
            List<String> lore = new ArrayList<>();
            lore.add(AutoAnnouncer.colorize("&7Haz clic aquí para retirar cualquier"));
            lore.add(AutoAnnouncer.colorize("&7cosmético que lleves activo."));
            clearMeta.setLore(lore);
            clearItem.setItemMeta(clearMeta);
        }
        inv.setItem(22, clearItem);

        player.openInventory(inv);
    }

    private static ItemStack createCosmeticItem(Player player, CosmeticsManager manager, CosmeticType type, boolean isEquipped) {
        boolean unlocked = manager.hasPermission(player, type);
        ItemStack item = new ItemStack(type.getIcon());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(AutoAnnouncer.colorize(type.getDisplayName()));
            List<String> lore = new ArrayList<>();
            for (String desc : type.getDescription()) {
                lore.add(AutoAnnouncer.colorize(desc));
            }
            lore.add("");

            if (isEquipped) {
                lore.add(AutoAnnouncer.colorize("&b&l▶ ACTIVADO &a(Haz clic para quitar)"));
            } else if (unlocked) {
                lore.add(AutoAnnouncer.colorize("&a✔ Desbloqueado &7(Haz clic para equipar)"));
            } else {
                lore.add(AutoAnnouncer.colorize("&c🔒 Bloqueado"));
                lore.add(AutoAnnouncer.colorize("&7Consíguelo con un &eVoucher &7o en la Tienda:"));
                lore.add(AutoAnnouncer.colorize("&fhttps://piponmc.shop"));
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
