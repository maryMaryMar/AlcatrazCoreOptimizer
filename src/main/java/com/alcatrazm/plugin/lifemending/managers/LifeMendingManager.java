package com.alcatrazm.plugin.lifemending.managers;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class LifeMendingManager {

    private final AlcatrazPlugin plugin;
    private final NamespacedKey keyLifeMendingBook;
    private final NamespacedKey keyLifeMendingItem;
    private NamespacedKey legacyKeyBook;
    private NamespacedKey legacyKeyItem;

    public LifeMendingManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.keyLifeMendingBook = new NamespacedKey(plugin, "life_mending_book");
        this.keyLifeMendingItem = new NamespacedKey(plugin, "life_mending");
        try {
            this.legacyKeyBook = NamespacedKey.fromString("lifemending:life_mending_book");
        } catch (Exception ignored) {
            this.legacyKeyBook = null;
        }
        try {
            this.legacyKeyItem = NamespacedKey.fromString("lifemending:life_mending");
        } catch (Exception ignored) {
            this.legacyKeyItem = null;
        }
    }

    public NamespacedKey getKeyLifeMendingBook() {
        return keyLifeMendingBook;
    }

    public NamespacedKey getKeyLifeMendingItem() {
        return keyLifeMendingItem;
    }

    public String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public String getMessage(String path, String def) {
        String msg = plugin.getConfig().getString("messages-lifemending." + path, def);
        String prefix = plugin.getConfig().getString("messages-lifemending.prefix", "&8[&4Life&cMending&8] &r");
        return color(prefix + msg);
    }

    /**
     * Crea un libro encantado de Life Mending.
     */
    public ItemStack createLifeMendingBook() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        if (meta != null) {
            String name = plugin.getConfig().getString("life-mending.book.name", "&c&lLibro Encantado: &4&lLife Mending");
            meta.setDisplayName(color(name));

            List<String> rawLore = plugin.getConfig().getStringList("life-mending.book.lore");
            if (rawLore != null && !rawLore.isEmpty()) {
                List<String> lore = new ArrayList<>();
                for (String line : rawLore) {
                    lore.add(color(line));
                }
                meta.setLore(lore);
            }

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyLifeMendingBook, PersistentDataType.BYTE, (byte) 1);
            if (legacyKeyBook != null) {
                try {
                    pdc.set(legacyKeyBook, PersistentDataType.BYTE, (byte) 1);
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

    /**
     * Comprueba si un ItemStack es un libro de Life Mending.
     */
    public boolean isLifeMendingBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(keyLifeMendingBook, PersistentDataType.BYTE)) {
            return true;
        }
        return legacyKeyBook != null && pdc.has(legacyKeyBook, PersistentDataType.BYTE);
    }

    /**
     * Comprueba si un objeto puede recibir o tiene durabilidad (armas, herramientas, armaduras, élitras, etc.).
     */
    public boolean isDamageableItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType().getMaxDurability() <= 0) return false;
        return item.getItemMeta() instanceof Damageable;
    }

    /**
     * Comprueba si un objeto ya tiene el encantamiento Life Mending aplicado.
     */
    public boolean hasLifeMending(ItemStack item) {
        if (!isDamageableItem(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(keyLifeMendingItem, PersistentDataType.BYTE)) {
            return true;
        }
        return legacyKeyItem != null && pdc.has(legacyKeyItem, PersistentDataType.BYTE);
    }

    /**
     * Aplica el encantamiento Life Mending a cualquier objeto con durabilidad.
     */
    public ItemStack applyLifeMending(ItemStack original) {
        if (!isDamageableItem(original)) return null;

        ItemStack result = original.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyLifeMendingItem, PersistentDataType.BYTE, (byte) 1);
        if (legacyKeyItem != null) {
            try {
                pdc.set(legacyKeyItem, PersistentDataType.BYTE, (byte) 1);
            } catch (Exception ignored) {}
        }

        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        String tagLore = color(plugin.getConfig().getString("life-mending.enchantment-tag-lore", "&c✦ Life Mending ✦"));
        String extraLore = color(plugin.getConfig().getString("life-mending.extra-lore-line", "&7Repara su durabilidad consumiendo tu vida y saturación."));

        if (!lore.contains(tagLore)) {
            lore.add(tagLore);
            if (!extraLore.isEmpty() && !lore.contains(extraLore)) {
                lore.add(extraLore);
            }
            meta.setLore(lore);
        }

        result.setItemMeta(meta);
        return result;
    }

    /**
     * Repara una cantidad de durabilidad de un objeto.
     * Retorna la cantidad real de durabilidad restaurada.
     */
    public int repairDurability(ItemStack item, int amount) {
        if (!isDamageableItem(item) || amount <= 0) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int currentDamage = damageable.getDamage();
            if (currentDamage <= 0) return 0;

            int repaired = Math.min(currentDamage, amount);
            damageable.setDamage(currentDamage - repaired);
            item.setItemMeta(damageable);
            return repaired;
        }
        return 0;
    }
}
