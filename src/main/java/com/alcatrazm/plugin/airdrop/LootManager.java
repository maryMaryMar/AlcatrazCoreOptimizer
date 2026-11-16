package com.alcatrazm.plugin.airdrop;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Gestiona la tabla de botin de AirDrops en airdrop_loot.yml.
 */
public final class LootManager {
    private final AirDropModule module;
    private final File lootFile;
    private List<ItemStack> items = new ArrayList<>();

    public LootManager(AirDropModule module) {
        this.module = module;
        this.lootFile = new File(module.getPlugin().getDataFolder(), "airdrop_loot.yml");
        this.reload();
    }

    public void reload() {
        if (!this.lootFile.exists()) {
            this.items = new ArrayList<>(List.of(
                new ItemStack(Material.DIAMOND, 3),
                new ItemStack(Material.GOLDEN_APPLE, 2),
                new ItemStack(Material.IRON_INGOT, 16),
                new ItemStack(Material.GOLD_INGOT, 8),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 16),
                new ItemStack(Material.ENDER_PEARL, 4),
                new ItemStack(Material.EMERALD, 8),
                new ItemStack(Material.ARROW, 32),
                new ItemStack(Material.OBSIDIAN, 8),
                new ItemStack(Material.COOKED_BEEF, 16),
                new ItemStack(Material.DIAMOND_SWORD),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE)
            ));
            this.save();
        } else {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(this.lootFile);
            List<?> list = config.getList("items", List.of());
            this.items = new ArrayList<>();
            for (Object obj : list) {
                if (obj instanceof ItemStack stack && !stack.getType().isAir()) {
                    this.items.add(stack.clone());
                }
            }
        }
    }

    public List<ItemStack> getItems() {
        return this.items.stream().map(ItemStack::clone).toList();
    }

    public int getItemCount() {
        return this.items.size();
    }

    public boolean setItems(List<ItemStack> newItems) {
        List<ItemStack> backup = this.items;
        this.items = newItems.stream()
            .filter(Objects::nonNull)
            .filter(it -> !it.getType().isAir())
            .map(ItemStack::clone)
            .toList();

        if (this.save()) {
            return true;
        }
        this.items = backup;
        return false;
    }

    public boolean save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("items", this.items);
        try {
            config.save(this.lootFile);
            return true;
        } catch (IOException e) {
            this.module.getPlugin().getLogger().severe("[AirDrop] No se pudo guardar airdrop_loot.yml: " + e.getMessage());
            return false;
        }
    }

    public List<ItemStack> rollLoot() {
        int min = this.module.getInt("loot.minimum", 2, 1, 54);
        int max = this.module.getInt("loot.maximum", 7, 1, 54);
        return DropMath.pickRandom(this.items, min, max, this.module.getRandom())
            .stream()
            .map(ItemStack::clone)
            .toList();
    }
}
