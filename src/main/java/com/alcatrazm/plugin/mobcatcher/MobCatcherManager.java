package com.alcatrazm.plugin.mobcatcher;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class MobCatcherManager {

    private final AlcatrazPlugin plugin;
    public final NamespacedKey KEY_CATCHER;
    public final NamespacedKey KEY_MOB_TYPE;
    public final NamespacedKey KEY_MOB_NAME;
    public final NamespacedKey KEY_MOB_HEALTH;
    public final NamespacedKey KEY_VILLAGER_PROF;
    public final NamespacedKey KEY_VILLAGER_LEVEL;
    public final NamespacedKey KEY_VILLAGER_EXP;
    public final NamespacedKey KEY_SNAPSHOT;

    public MobCatcherManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.KEY_CATCHER = new NamespacedKey(plugin, "mob_catcher");
        this.KEY_MOB_TYPE = new NamespacedKey(plugin, "mob_type");
        this.KEY_MOB_NAME = new NamespacedKey(plugin, "mob_name");
        this.KEY_MOB_HEALTH = new NamespacedKey(plugin, "mob_health");
        this.KEY_VILLAGER_PROF = new NamespacedKey(plugin, "villager_prof");
        this.KEY_VILLAGER_LEVEL = new NamespacedKey(plugin, "villager_lvl");
        this.KEY_VILLAGER_EXP = new NamespacedKey(plugin, "villager_exp");
        this.KEY_SNAPSHOT = new NamespacedKey(plugin, "mob_snapshot");
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public ItemStack createEmptyCatcher(int amount) {
        ItemStack item = new ItemStack(Material.ENDER_EYE, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&d✦ &6&lEsfera de Contención Alcatraz &d✦"));
            List<String> lore = new ArrayList<>();
            lore.add(color("&7¡Tecnología de captura cuántica de criaturas!"));
            lore.add(color(""));
            lore.add(color("&eEstado: &a[VACÍA - LISTA PARA CAPTURAR]"));
            lore.add(color("&7▪ Click derecho sobre un aldeano, caballo"));
            lore.add(color("&7  o criatura pacífica para atraparla."));
            lore.add(color("&7▪ Conserva todos los &btradeos y niveles&7."));
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(KEY_CATCHER, PersistentDataType.STRING, "empty");
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createFilledCatcher(LivingEntity entity) {
        ItemStack item = new ItemStack(Material.SNOWBALL, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String typeName = entity.getType().name();
            String customName = entity.getCustomName() != null ? entity.getCustomName() : typeName;

            meta.setDisplayName(color("&e✦ &6Esfera: &b" + customName + " &e✦"));
            List<String> lore = new ArrayList<>();
            lore.add(color("&7Criatura almacenada dentro de la esfera:"));
            lore.add(color(""));
            lore.add(color("&8▪ &7Tipo: &f" + typeName));
            lore.add(color("&8▪ &7Vida: &c" + Math.round(entity.getHealth()) + " HP"));

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(KEY_CATCHER, PersistentDataType.STRING, "filled");
            pdc.set(KEY_MOB_TYPE, PersistentDataType.STRING, typeName);
            pdc.set(KEY_MOB_HEALTH, PersistentDataType.DOUBLE, entity.getHealth());

            // Captura completa del estado (tradeos, equipamiento, dueño de mascota, atributos)
            try {
                org.bukkit.entity.EntitySnapshot snapshot = entity.createSnapshot();
                if (snapshot != null) {
                    pdc.set(KEY_SNAPSHOT, PersistentDataType.STRING, snapshot.getAsString());
                }
            } catch (Throwable ignored) {}

            if (entity.getCustomName() != null) {
                pdc.set(KEY_MOB_NAME, PersistentDataType.STRING, entity.getCustomName());
            }

            if (entity instanceof Villager v) {
                String profKey = v.getProfession().getKey().getKey();
                String profDisplay = profKey.isEmpty() ? "None" : (Character.toUpperCase(profKey.charAt(0)) + profKey.substring(1));
                lore.add(color("&8▪ &7Profesión: &e" + profDisplay));
                lore.add(color("&8▪ &7Nivel de Tradeo: &a" + v.getVillagerLevel()));
                lore.add(color("&8▪ &7Experiencia: &b" + v.getVillagerExperience()));

                pdc.set(KEY_VILLAGER_PROF, PersistentDataType.STRING, profKey);
                pdc.set(KEY_VILLAGER_LEVEL, PersistentDataType.INTEGER, v.getVillagerLevel());
                pdc.set(KEY_VILLAGER_EXP, PersistentDataType.INTEGER, v.getVillagerExperience());
            }

            lore.add(color(""));
            lore.add(color("&eClick derecho en el suelo para liberar."));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isEmptyCatcher(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String val = item.getItemMeta().getPersistentDataContainer().get(KEY_CATCHER, PersistentDataType.STRING);
        return "empty".equalsIgnoreCase(val);
    }

    public boolean isFilledCatcher(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String val = item.getItemMeta().getPersistentDataContainer().get(KEY_CATCHER, PersistentDataType.STRING);
        return "filled".equalsIgnoreCase(val);
    }
}
