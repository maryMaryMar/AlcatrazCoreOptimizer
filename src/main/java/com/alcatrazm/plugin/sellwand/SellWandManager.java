package com.alcatrazm.plugin.sellwand;

import com.alcatrazm.plugin.AlcatrazPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.*;

public class SellWandManager {

    private final AlcatrazPlugin plugin;
    private Economy economy;

    private final NamespacedKey keyWandTier;
    private final NamespacedKey keyWandMultiplier;
    private final NamespacedKey keyWandUses;
    private final NamespacedKey keyWandMaxUses;
    private final NamespacedKey keyWandTotalSold;
    private final NamespacedKey keyWandItemsSold;

    private final Map<String, SellWandTier> tiers = new LinkedHashMap<>();
    private final Map<Material, Double> prices = new EnumMap<>(Material.class);

    private boolean enabled;
    private double cooldownSeconds;

    public SellWandManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.keyWandTier = new NamespacedKey(plugin, "sellwand_tier");
        this.keyWandMultiplier = new NamespacedKey(plugin, "sellwand_multiplier");
        this.keyWandUses = new NamespacedKey(plugin, "sellwand_uses");
        this.keyWandMaxUses = new NamespacedKey(plugin, "sellwand_max_uses");
        this.keyWandTotalSold = new NamespacedKey(plugin, "sellwand_total_sold");
        this.keyWandItemsSold = new NamespacedKey(plugin, "sellwand_items_sold");

        setupEconomy();
        loadConfig();
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("[SellWand] Vault no encontrado. El sistema de economía no estará activo.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            this.economy = rsp.getProvider();
            plugin.getLogger().info("[SellWand] Enganchado con éxito a la economía de Vault: " + economy.getName());
        } else {
            plugin.getLogger().warning("[SellWand] Proveedor de economía Vault no registrado aún.");
        }
    }

    public void loadConfig() {
        tiers.clear();
        prices.clear();

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("sellwand");
        if (sec == null) {
            plugin.getLogger().warning("[SellWand] Sección 'sellwand' no encontrada en config.yml.");
            return;
        }

        this.enabled = sec.getBoolean("enabled", true);
        this.cooldownSeconds = sec.getDouble("cooldown-seconds", 0.5);

        // Cargar Tiers
        ConfigurationSection tiersSec = sec.getConfigurationSection("tiers");
        if (tiersSec != null) {
            for (String key : tiersSec.getKeys(false)) {
                ConfigurationSection t = tiersSec.getConfigurationSection(key);
                if (t == null) continue;

                String name = t.getString("name", "&bVara " + key);
                double multiplier = t.getDouble("multiplier", 1.0);
                int maxUses = t.getInt("max-uses", 100);
                boolean infinite = t.getBoolean("infinite", false);
                String matName = t.getString("material", "BLAZE_ROD");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) mat = Material.BLAZE_ROD;
                int cmd = t.getInt("custom-model-data", 1001);
                List<String> lore = t.getStringList("lore");

                SellWandTier tier = new SellWandTier(key, name, multiplier, maxUses, infinite, mat, cmd, lore);
                tiers.put(key.toUpperCase(), tier);
            }
        }

        // Cargar Precios
        ConfigurationSection pricesSec = sec.getConfigurationSection("prices");
        if (pricesSec != null) {
            for (String matKey : pricesSec.getKeys(false)) {
                Material m = Material.matchMaterial(matKey.toUpperCase());
                if (m != null) {
                    double price = pricesSec.getDouble(matKey, 0.0);
                    if (price > 0.0) {
                        prices.put(m, price);
                    }
                }
            }
        }

        plugin.getLogger().info("[SellWand] Cargados " + tiers.size() + " tiers y " + prices.size() + " precios de ítems.");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getCooldownSeconds() {
        return cooldownSeconds;
    }

    public Economy getEconomy() {
        if (economy == null) {
            setupEconomy();
        }
        return economy;
    }

    public Map<String, SellWandTier> getTiers() {
        return Collections.unmodifiableMap(tiers);
    }

    public SellWandTier getTierById(String id) {
        if (id == null) return null;
        return tiers.get(id.toUpperCase());
    }

    public boolean isSellWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(keyWandTier, PersistentDataType.STRING);
    }

    public SellWandTier getTier(ItemStack item) {
        if (!isSellWand(item)) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String tierId = pdc.get(keyWandTier, PersistentDataType.STRING);
        return getTierById(tierId);
    }

    public int getUses(ItemStack item) {
        if (!isSellWand(item)) return 0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Integer u = pdc.get(keyWandUses, PersistentDataType.INTEGER);
        return u != null ? u : 0;
    }

    public double getTotalSold(ItemStack item) {
        if (!isSellWand(item)) return 0.0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Double d = pdc.get(keyWandTotalSold, PersistentDataType.DOUBLE);
        return d != null ? d : 0.0;
    }

    public int getItemsSold(ItemStack item) {
        if (!isSellWand(item)) return 0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Integer i = pdc.get(keyWandItemsSold, PersistentDataType.INTEGER);
        return i != null ? i : 0;
    }

    public double getMultiplier(ItemStack item) {
        if (!isSellWand(item)) return 1.0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Double mult = pdc.get(keyWandMultiplier, PersistentDataType.DOUBLE);
        if (mult != null && mult > 0.0) {
            return mult;
        }
        SellWandTier tier = getTier(item);
        if (tier != null) {
            double tierMult = tier.getMultiplier();
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(keyWandMultiplier, PersistentDataType.DOUBLE, tierMult);
            item.setItemMeta(meta);
            return tierMult;
        }
        return 1.0;
    }

    public ItemStack createSellWand(SellWandTier tier, int customUses) {
        return createSellWand(tier, customUses, tier.getMultiplier());
    }

    public ItemStack createSellWand(SellWandTier tier, int customUses, double customMultiplier) {
        ItemStack item = new ItemStack(tier.getMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(SellWandTier.colorize(tier.getName()));
        if (tier.getCustomModelData() > 0) {
            meta.setCustomModelData(tier.getCustomModelData());
        }

        int uses = customUses > 0 ? customUses : (tier.isInfinite() ? -1 : tier.getMaxUses());
        double mult = customMultiplier > 0.0 ? customMultiplier : tier.getMultiplier();

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyWandTier, PersistentDataType.STRING, tier.getId());
        pdc.set(keyWandMultiplier, PersistentDataType.DOUBLE, mult);
        pdc.set(keyWandUses, PersistentDataType.INTEGER, uses);
        pdc.set(keyWandMaxUses, PersistentDataType.INTEGER, tier.getMaxUses());
        pdc.set(keyWandTotalSold, PersistentDataType.DOUBLE, 0.0);
        pdc.set(keyWandItemsSold, PersistentDataType.INTEGER, 0);

        meta.setLore(tier.buildLore(uses, 0.0, 0, mult));
        item.setItemMeta(meta);
        return item;
    }

    public void updateWandData(ItemStack item, SellWandTier tier, int newUses, double addedMoney, int addedItems) {
        updateWandData(item, tier, newUses, addedMoney, addedItems, getMultiplier(item));
    }

    public void updateWandData(ItemStack item, SellWandTier tier, int newUses, double addedMoney, int addedItems, double multiplier) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        double totalSold = pdc.getOrDefault(keyWandTotalSold, PersistentDataType.DOUBLE, 0.0) + addedMoney;
        int itemsSold = pdc.getOrDefault(keyWandItemsSold, PersistentDataType.INTEGER, 0) + addedItems;

        pdc.set(keyWandUses, PersistentDataType.INTEGER, newUses);
        pdc.set(keyWandTotalSold, PersistentDataType.DOUBLE, totalSold);
        pdc.set(keyWandItemsSold, PersistentDataType.INTEGER, itemsSold);
        pdc.set(keyWandMultiplier, PersistentDataType.DOUBLE, multiplier);

        meta.setLore(tier.buildLore(newUses, totalSold, itemsSold, multiplier));
        item.setItemMeta(meta);
    }

    public boolean isProtectedItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        if (!stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        if (meta.hasEnchants() || meta.hasLore() || meta.hasCustomModelData()) return true;
        if (meta instanceof EnchantmentStorageMeta) return true;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return !pdc.getKeys().isEmpty();
    }

    public static class SellResult {
        private final int itemsSold;
        private final double rawTotal;
        private final double finalTotal;
        private final double bonusTotal;
        private final int remainingUses;
        private final boolean broken;

        public SellResult(int itemsSold, double rawTotal, double finalTotal, int remainingUses, boolean broken) {
            this(itemsSold, rawTotal, finalTotal, Math.max(0.0, finalTotal - rawTotal), remainingUses, broken);
        }

        public SellResult(int itemsSold, double rawTotal, double finalTotal, double bonusTotal, int remainingUses, boolean broken) {
            this.itemsSold = itemsSold;
            this.rawTotal = rawTotal;
            this.finalTotal = finalTotal;
            this.bonusTotal = bonusTotal;
            this.remainingUses = remainingUses;
            this.broken = broken;
        }

        public int getItemsSold() { return itemsSold; }
        public double getRawTotal() { return rawTotal; }
        public double getFinalTotal() { return finalTotal; }
        public double getBonusTotal() { return bonusTotal; }
        public int getRemainingUses() { return remainingUses; }
        public boolean isBroken() { return broken; }
        public boolean hasSold() { return itemsSold > 0 && finalTotal > 0.0; }
    }

    public SellResult processSale(Player player, Block block, ItemStack wand) {
        SellWandTier tier = getTier(wand);
        if (tier == null) return new SellResult(0, 0, 0, 0, 0, false);

        Inventory inv = getContainerInventory(block);
        if (inv == null) return new SellResult(0, 0, 0, 0, 0, false);

        double multiplier = getMultiplier(wand);
        int totalItems = 0;
        double rawAmount = 0.0;

        ItemStack[] contents = inv.getContents();
        List<Integer> slotsToClear = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) continue;

            // Seguridad: no vender ítems con encantamientos o PDC especial (ACE / Custom)
            if (isProtectedItem(stack)) continue;

            Double unitPrice = prices.get(stack.getType());
            if (unitPrice != null && unitPrice > 0.0) {
                int count = stack.getAmount();
                totalItems += count;
                rawAmount += (unitPrice * count);
                slotsToClear.add(i);
            }
        }

        if (totalItems == 0 || rawAmount <= 0.0) {
            return new SellResult(0, 0, 0, 0, getUses(wand), false);
        }

        double finalAmount = rawAmount * multiplier;
        double bonus = Math.max(0.0, finalAmount - rawAmount);

        Economy eco = getEconomy();
        if (eco == null) {
            plugin.getLogger().severe("[SellWand] ¡Error! No hay economía disponible para depositar $" + finalAmount + " a " + player.getName());
            return new SellResult(0, 0, 0, 0, getUses(wand), false);
        }

        EconomyResponse resp = eco.depositPlayer(player, finalAmount);
        if (resp == null || !resp.transactionSuccess()) {
            player.sendMessage(color("&c[SellWand] Error al acreditar el saldo en la economía. No se retiraron los objetos."));
            return new SellResult(0, 0, 0, 0, getUses(wand), false);
        }

        // Transacción confirmada: retirar los ítems del contenedor
        for (int slot : slotsToClear) {
            inv.setItem(slot, null);
        }

        int currentUses = getUses(wand);
        int newUses = currentUses;
        boolean broken = false;

        if (!tier.isInfinite()) {
            newUses = Math.max(0, currentUses - 1);
            if (newUses <= 0) {
                broken = true;
            }
        }

        updateWandData(wand, tier, newUses, finalAmount, totalItems, multiplier);

        return new SellResult(totalItems, rawAmount, finalAmount, bonus, newUses, broken);
    }

    private Inventory getContainerInventory(Block block) {
        if (block == null) return null;
        try {
            BlockState state = block.getState(false);
            if (state instanceof Chest chest) {
                return chest.getInventory();
            }
            if (state instanceof Container container) {
                return container.getInventory();
            }
        } catch (Throwable t) {
            BlockState state = block.getState();
            if (state instanceof Chest chest) {
                return chest.getInventory();
            }
            if (state instanceof Container container) {
                return container.getInventory();
            }
        }
        return null;
    }

    public Double getPrice(Material material) {
        return prices.get(material);
    }

    public Map<Material, Double> getPrices() {
        return Collections.unmodifiableMap(prices);
    }
}
