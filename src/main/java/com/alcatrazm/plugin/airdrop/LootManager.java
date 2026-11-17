package com.alcatrazm.plugin.airdrop;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.sellwand.SellWandTier;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Gestiona las tablas de botin por rareza en airdrop_loot.yml soportando sintaxis YAML simple,
 * items nativos de Alcatraz, comandos/llaves e ItemStacks guardados desde /uad.
 */
public final class LootManager {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final AirDropModule module;
    private final File lootFile;
    private final NamespacedKey commandKey;
    private final Map<String, List<LootEntry>> tierLoot = new LinkedHashMap<>();

    public LootManager(AirDropModule module) {
        this.module = module;
        this.lootFile = new File(module.getPlugin().getDataFolder(), "airdrop_loot.yml");
        this.commandKey = new NamespacedKey(module.getPlugin(), "airdrop-cmd");
        this.reload();
    }

    public NamespacedKey getCommandKey() {
        return this.commandKey;
    }

    public String getPendingCommand(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(this.commandKey, PersistentDataType.STRING);
    }

    public void reload() {
        this.tierLoot.clear();
        if (!this.lootFile.exists()) {
            this.populateDefaults();
            this.save();
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(this.lootFile);
        ConfigurationSection tiersSec = config.getConfigurationSection("tiers");
        if (tiersSec == null) {
            this.populateDefaults();
            List<?> legacyItems = config.getList("items", List.of());
            if (!legacyItems.isEmpty()) {
                List<LootEntry> extra = this.parseRawList(legacyItems);
                List<LootEntry> comun = this.tierLoot.computeIfAbsent("comun", k -> new ArrayList<>());
                for (LootEntry e : extra) {
                    if (comun.stream().noneMatch(existing -> existing.display().isSimilar(e.display()) && existing.display().getAmount() == e.display().getAmount())) {
                        comun.add(e);
                    }
                }
            }
            this.save();
            return;
        }

        for (String tierKey : tiersSec.getKeys(false)) {
            String normId = DropTier.normalizeId(tierKey);
            List<?> rawList = tiersSec.getList(tierKey + ".items", List.of());
            this.tierLoot.put(normId, this.parseRawList(rawList));
        }

        for (DropTier tier : DropTier.loadTiers(this.module)) {
            this.tierLoot.computeIfAbsent(tier.id(), k -> new ArrayList<>());
        }
    }

    private void populateDefaults() {
        this.tierLoot.put("comun", this.parseRawList(List.of(
            "IRON_INGOT:16",
            "GOLD_INGOT:8",
            "EMERALD:8",
            "DIAMOND:3",
            "OBSIDIAN:8",
            "GOLDEN_APPLE:2",
            "EXPERIENCE_BOTTLE:16",
            "ENDER_PEARL:4",
            "FIREWORK_ROCKET:16",
            "COOKED_BEEF:16",
            "ARROW:32",
            mapOfCmd("crates key give %player% vote 1", "TRIPWIRE_HOOK", "&a&lLlave Vote &7(x1)", List.of("&7Click en el cofre para recibir tu llave")),
            mapOfCmd("crates key give %player% sunshine 1", "TRIPWIRE_HOOK", "&e&lLlave Sunshine &7(x1)", List.of("&7Click en el cofre para recibir tu llave"))
        )));

        this.tierLoot.put("epico", this.parseRawList(List.of(
            "DIAMOND:8",
            "ANCIENT_DEBRIS:3",
            "NETHERITE_INGOT:1",
            "EMERALD_BLOCK:2",
            "GOLDEN_APPLE:4",
            "ENCHANTED_GOLDEN_APPLE:1",
            "TOTEM_OF_UNDYING:1",
            "EXPERIENCE_BOTTLE:32",
            "ENDER_PEARL:8",
            mapOfItem("DIAMOND_SWORD", 1, "&b&lEspada del Drop Épico", Map.of("sharpness", 4, "unbreaking", 3)),
            Map.of("alcatraz", "sellwand_angel"),
            Map.of("alcatraz", "mob_catcher"),
            mapOfCmd("crates key give %player% oceanic 1", "TRIPWIRE_HOOK", "&b&lLlave Oceanic &7(x1)", List.of("&7Click en el cofre para recibir tu llave")),
            mapOfCmd("crates key give %player% amethyst 1", "TRIPWIRE_HOOK", "&d&lLlave Amethyst &7(x1)", List.of("&7Click en el cofre para recibir tu llave")),
            mapOfCmd("crazyvouchers give Coins200 1 %player%", "PAPER", "&e&lVoucher 200 Coins", List.of("&7Click para recibir el voucher")),
            mapOfCmd("crazyvouchers give AuraSkills_1.5X_1Hour 1 %player%", "PAPER", "&a&lAuraSkills 1.5X (1h)", List.of("&7Click para recibir el voucher"))
        )));

        this.tierLoot.put("legendario", this.parseRawList(List.of(
            "NETHERITE_INGOT:2",
            "NETHER_STAR:1",
            "BEACON:1",
            "ENCHANTED_GOLDEN_APPLE:2",
            "TOTEM_OF_UNDYING:2",
            "NETHERITE_UPGRADE_SMITHING_TEMPLATE:1",
            "SHULKER_SHELL:2",
            Map.of("alcatraz", "sellwand_celestial"),
            Map.of("alcatraz", "sonic_blade"),
            Map.of("alcatraz", "netherite_elytra"),
            mapOfCmd("crates key give %player% blaze 1", "TRIPWIRE_HOOK", "&6&lLlave Blaze &7(x1)", List.of("&7Click en el cofre para recibir tu llave")),
            mapOfCmd("crates key give %player% amethyst 2", "TRIPWIRE_HOOK", "&d&lLlave Amethyst &7(x2)", List.of("&7Click en el cofre para recibir tus llaves")),
            mapOfCmd("crazyvouchers give Souls4000 1 %player%", "PAPER", "&c&lVoucher 4000 Souls", List.of("&7Click para recibir el voucher")),
            mapOfCmd("crazyvouchers give AuraSkills_2X_1Hour 1 %player%", "PAPER", "&6&lAuraSkills 2X (1h)", List.of("&7Click para recibir el voucher"))
        )));
    }

    private static Map<String, Object> mapOfCmd(String cmd, String icon, String name, List<String> lore) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("command", cmd);
        m.put("icon", icon);
        m.put("name", name);
        m.put("lore", lore);
        return m;
    }

    private static Map<String, Object> mapOfItem(String mat, int amount, String name, Map<String, Integer> enchants) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("material", mat);
        m.put("amount", amount);
        m.put("name", name);
        m.put("enchants", enchants);
        return m;
    }

    private List<LootEntry> parseRawList(List<?> list) {
        List<LootEntry> entries = new ArrayList<>();
        Logger log = this.module.getPlugin().getLogger();
        for (Object obj : list) {
            ItemStack parsed = this.parseItem(obj, log);
            if (parsed != null && !parsed.getType().isAir()) {
                entries.add(new LootEntry(parsed, obj));
            }
        }
        return entries;
    }

    private ItemStack parseItem(Object obj, Logger log) {
        if (obj instanceof ItemStack stack) {
            return stack.clone();
        }
        if (obj instanceof String str) {
            return this.parseStringShorthand(str.trim(), log);
        }
        if (obj instanceof Map<?, ?> rawMap) {
            return this.parseMapEntry(rawMap, log);
        }
        return null;
    }

    private ItemStack parseStringShorthand(String str, Logger log) {
        if (str.isEmpty()) {
            return null;
        }
        String[] parts = str.split(":", 2);
        Material mat = Material.matchMaterial(parts[0].trim());
        if (mat == null || !mat.isItem() || mat.isAir()) {
            log.warning("[AirDrop] Material invalido en airdrop_loot.yml: " + str);
            return null;
        }
        int amount = 1;
        if (parts.length == 2) {
            try {
                amount = Math.clamp(Integer.parseInt(parts[1].trim()), 1, 64);
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        return new ItemStack(mat, amount);
    }

    private ItemStack parseMapEntry(Map<?, ?> rawMap, Logger log) {
        int amount = rawMap.containsKey("amount") && rawMap.get("amount") instanceof Number n
            ? Math.clamp(n.intValue(), 1, 64)
            : 1;

        Object alcatrazId = rawMap.get("alcatraz");
        if (alcatrazId instanceof String id) {
            ItemStack nativeItem = this.buildAlcatrazItem(id.trim().toLowerCase(Locale.ROOT));
            if (nativeItem != null) {
                nativeItem.setAmount(Math.min(amount, nativeItem.getMaxStackSize()));
                return nativeItem;
            }
            log.warning("[AirDrop] Item Alcatraz desconocido en airdrop_loot.yml: " + id);
            return null;
        }

        Object commandObj = rawMap.get("command");
        String matName = rawMap.containsKey("icon") ? String.valueOf(rawMap.get("icon"))
            : (rawMap.containsKey("material") ? String.valueOf(rawMap.get("material")) : "PAPER");
        Material mat = Material.matchMaterial(matName);
        if (mat == null || !mat.isItem() || mat.isAir()) {
            mat = commandObj != null ? Material.TRIPWIRE_HOOK : Material.STONE;
        }

        ItemStack stack = new ItemStack(mat, Math.min(amount, mat.getMaxStackSize()));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (rawMap.get("name") instanceof String nameStr && !nameStr.isEmpty()) {
                meta.displayName(LEGACY.deserialize(nameStr).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            }
            if (rawMap.get("lore") instanceof List<?> loreList) {
                List<Component> comps = new ArrayList<>();
                for (Object l : loreList) {
                    comps.add(LEGACY.deserialize(String.valueOf(l)).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                }
                meta.lore(comps);
            }
            if (rawMap.get("enchants") instanceof Map<?, ?> enchMap) {
                for (Map.Entry<?, ?> entry : enchMap.entrySet()) {
                    Enchantment ench = resolveEnchantment(String.valueOf(entry.getKey()));
                    int lvl = entry.getValue() instanceof Number n ? n.intValue() : 1;
                    if (ench != null && lvl > 0) {
                        meta.addEnchant(ench, lvl, true);
                    }
                }
            }
            if (commandObj instanceof String cmdStr && !cmdStr.isBlank()) {
                meta.getPersistentDataContainer().set(this.commandKey, PersistentDataType.STRING, cmdStr.trim());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack buildAlcatrazItem(String id) {
        AlcatrazPlugin p = this.module.getPlugin();
        return switch (id) {
            case "sellwand_angel" -> {
                SellWandTier tier = p.getSellWandManager() != null ? p.getSellWandManager().getTierById("ANGEL") : null;
                yield tier != null ? p.getSellWandManager().createSellWand(tier, tier.getMaxUses()) : null;
            }
            case "sellwand_celestial" -> {
                SellWandTier tier = p.getSellWandManager() != null ? p.getSellWandManager().getTierById("CELESTIAL") : null;
                yield tier != null ? p.getSellWandManager().createSellWand(tier, tier.getMaxUses()) : null;
            }
            case "mob_catcher" -> p.getMobCatcherManager() != null ? p.getMobCatcherManager().createEmptyCatcher(1) : null;
            case "sonic_blade" -> p.getSonicBladeManager() != null ? p.getSonicBladeManager().createSonicBlade() : null;
            case "netherite_elytra" -> p.getItemManager() != null ? p.getItemManager().createElytraChestplate() : null;
            case "elytra_book" -> p.getItemManager() != null ? p.getItemManager().createElytraBook() : null;
            default -> null;
        };
    }

    private static Enchantment resolveEnchantment(String key) {
        if (key == null) {
            return null;
        }
        String clean = key.trim().toLowerCase(Locale.ROOT);
        if (clean.contains(":")) {
            return Registry.ENCHANTMENT.get(NamespacedKey.fromString(clean));
        }
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(clean));
    }

    public List<ItemStack> getItems(String tierId) {
        List<LootEntry> list = this.tierLoot.getOrDefault(DropTier.normalizeId(tierId), List.of());
        return list.stream().map(e -> e.display().clone()).toList();
    }

    public int getItemCount(String tierId) {
        return this.tierLoot.getOrDefault(DropTier.normalizeId(tierId), List.of()).size();
    }

    public int getTotalItemCount() {
        return this.tierLoot.values().stream().mapToInt(List::size).sum();
    }

    public boolean setItems(String tierId, List<ItemStack> newItems) {
        String normId = DropTier.normalizeId(tierId);
        List<LootEntry> previous = this.tierLoot.getOrDefault(normId, List.of());
        List<LootEntry> updated = new ArrayList<>();

        for (ItemStack raw : newItems) {
            if (raw == null || raw.getType().isAir()) {
                continue;
            }
            ItemStack cloned = raw.clone();
            Object matchingSource = previous.stream()
                .filter(e -> e.display().isSimilar(cloned) && e.display().getAmount() == cloned.getAmount())
                .map(LootEntry::rawYaml)
                .findFirst()
                .orElse(null);

            if (matchingSource == null) {
                matchingSource = this.toFriendlyYaml(cloned);
            }
            updated.add(new LootEntry(cloned, matchingSource));
        }

        this.tierLoot.put(normId, updated);
        if (this.save()) {
            return true;
        }
        this.tierLoot.put(normId, previous);
        return false;
    }

    private Object toFriendlyYaml(ItemStack item) {
        if (!item.hasItemMeta()) {
            return item.getType().name() + ":" + item.getAmount();
        }
        return item;
    }

    public boolean save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, List<LootEntry>> entry : this.tierLoot.entrySet()) {
            List<Object> serialized = entry.getValue().stream().map(LootEntry::rawYaml).toList();
            config.set("tiers." + entry.getKey() + ".items", serialized);
        }
        try {
            String header = ""
                + "# ==========================================================================\n"
                + "# TABLA DE BOTIN POR RAREZA (ALCATRAZ AIRDROPS) - EDITABLE SIN RECOMPILAR\n"
                + "# Formatos soportados en 'items:' (usa /uad recargar despues de editar):\n"
                + "#   1) \"MATERIAL:CANTIDAD\"              -> ej: \"DIAMOND:8\", \"TOTEM_OF_UNDYING:1\"\n"
                + "#   2) { material: ..., amount: ..., name: \"&b...\", enchants: { sharpness: 4 } }\n"
                + "#   3) { alcatraz: sellwand_angel }     -> sellwand_celestial, mob_catcher, sonic_blade, netherite_elytra, elytra_book\n"
                + "#   4) { command: \"crates key give %player% blaze 1\", icon: TRIPWIRE_HOOK, name: \"&6Llave Blaze\" }\n"
                + "#   5) ItemStack completo (guardado automatico desde el menu /uad -> CONTENIDO)\n"
                + "# ==========================================================================\n";
            Files.writeString(this.lootFile.toPath(), header + config.saveToString(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            this.module.getPlugin().getLogger().severe("[AirDrop] No se pudo guardar airdrop_loot.yml: " + e.getMessage());
            return false;
        }
    }

    public List<ItemStack> rollLoot(DropTier tier) {
        List<LootEntry> entries = this.tierLoot.getOrDefault(tier.id(), List.of());
        if (entries.isEmpty()) {
            return List.of();
        }
        List<ItemStack> pool = entries.stream().map(LootEntry::display).toList();
        return DropMath.pickRandom(pool, tier.minItems(), tier.maxItems(), this.module.getRandom())
            .stream()
            .map(ItemStack::clone)
            .toList();
    }

    private record LootEntry(ItemStack display, Object rawYaml) {
        private LootEntry {
            Objects.requireNonNull(display);
        }
    }
}
