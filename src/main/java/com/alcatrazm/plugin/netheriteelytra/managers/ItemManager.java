package com.alcatrazm.plugin.netheriteelytra.managers;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ItemManager {

    private final AlcatrazPlugin plugin;
    private final NamespacedKey keyElytraBook;
    private final NamespacedKey keyNetheriteElytra;
    private NamespacedKey legacyKeyElytraBook;
    private NamespacedKey legacyKeyNetheriteElytra;

    public ItemManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.keyElytraBook = new NamespacedKey(plugin, "elytra_book");
        this.keyNetheriteElytra = new NamespacedKey(plugin, "netherite_elytra");
        try {
            this.legacyKeyElytraBook = NamespacedKey.fromString("netheriteelytra:elytra_book");
        } catch (Exception ignored) {
            this.legacyKeyElytraBook = null;
        }
        try {
            this.legacyKeyNetheriteElytra = NamespacedKey.fromString("netheriteelytra:netherite_elytra");
        } catch (Exception ignored) {
            this.legacyKeyNetheriteElytra = null;
        }
    }

    public NamespacedKey getKeyElytraBook() {
        return keyElytraBook;
    }

    public NamespacedKey getKeyNetheriteElytra() {
        return keyNetheriteElytra;
    }

    public ItemStack createElytraBook() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        if (meta != null) {
            String name = plugin.getConfig().getString("items.book.name", "&6&lLibro Encantado: &d&lAlas del Inframundo");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            List<String> rawLore = plugin.getConfig().getStringList("items.book.lore");
            if (rawLore != null && !rawLore.isEmpty()) {
                List<String> lore = new ArrayList<>();
                for (String line : rawLore) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(lore);
            }

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyElytraBook, PersistentDataType.BYTE, (byte) 1);
            if (legacyKeyElytraBook != null) {
                try {
                    pdc.set(legacyKeyElytraBook, PersistentDataType.BYTE, (byte) 1);
                } catch (Exception ignored) {}
            }

            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(Enchantment.UNBREAKING, 1, true);
                try {
                    storageMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                } catch (Exception ignored) {}
            }

            book.setItemMeta(meta);
        }

        return book;
    }

    public boolean isElytraBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(keyElytraBook, PersistentDataType.BYTE)) {
            return true;
        }
        return legacyKeyElytraBook != null && pdc.has(legacyKeyElytraBook, PersistentDataType.BYTE);
    }

    public ItemStack createElytraChestplate() {
        ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = chestplate.getItemMeta();
        if (meta != null) {
            String name = plugin.getConfig().getString("items.chestplate.name", "&5&lPechera de Netherite &d[Élitra]");
            if (name != null && !name.isEmpty()) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            }
            chestplate.setItemMeta(meta);
        }
        return applyElytraToChestplate(chestplate);
    }

    public ItemStack applyElytraToChestplate(ItemStack original) {
        if (original == null || original.getType() != Material.NETHERITE_CHESTPLATE) {
            return null;
        }

        ItemStack chestplate = original.clone();
        ItemMeta meta = chestplate.getItemMeta();

        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyNetheriteElytra, PersistentDataType.BYTE, (byte) 1);
            if (legacyKeyNetheriteElytra != null) {
                try {
                    pdc.set(legacyKeyNetheriteElytra, PersistentDataType.BYTE, (byte) 1);
                } catch (Exception ignored) {}
            }

            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            String tagLore = ChatColor.translateAlternateColorCodes('&', 
                    plugin.getConfig().getString("items.chestplate.enchantment-tag-lore", "&b✦ Propulsión de Élitra Integrada ✦"));
            String extraLore = ChatColor.translateAlternateColorCodes('&', 
                    plugin.getConfig().getString("items.chestplate.extra-lore-line", "&7Presiona doble espacio en el aire para planear y usa cohetes."));

            if (!lore.contains(tagLore)) {
                lore.add(tagLore);
                if (!extraLore.isEmpty() && !lore.contains(extraLore)) {
                    lore.add(extraLore);
                }
                meta.setLore(lore);
            }

            chestplate.setItemMeta(meta);
        }

        return chestplate;
    }

    public boolean isElytraChestplate(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_CHESTPLATE || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(keyNetheriteElytra, PersistentDataType.BYTE)) {
            return true;
        }
        return legacyKeyNetheriteElytra != null && pdc.has(legacyKeyNetheriteElytra, PersistentDataType.BYTE);
    }
}
