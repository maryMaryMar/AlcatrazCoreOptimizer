package com.alcatrazm.plugin.sonicblade;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SonicBladeManager {

    private final AlcatrazPlugin plugin;

    public SonicBladeManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
    }

    public String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public String getMessage(String path, String def) {
        String msg = plugin.getConfig().getString("mensajes-hojasonica." + path, def);
        String prefix = plugin.getConfig().getString("mensajes-hojasonica.prefijo", "&8[&bSonicBlade&8] ");
        return color(prefix + msg);
    }

    public ItemStack createSonicBlade() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            int customModelData = plugin.getConfig().getInt("arma.custom-model-data", 12345);
            meta.setCustomModelData(customModelData);

            String nombre = plugin.getConfig().getString("arma.nombre", "&b&lGrito del Centinela");
            meta.setDisplayName(color(nombre));

            List<String> rawLore = plugin.getConfig().getStringList("arma.lore");
            if (rawLore != null && !rawLore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : rawLore) {
                    coloredLore.add(color(line));
                }
                meta.setLore(coloredLore);
            } else {
                String singleLore = plugin.getConfig().getString("arma.lore", "&7¡El eco del abismo!");
                if (singleLore != null && !singleLore.isEmpty()) {
                    meta.setLore(Collections.singletonList(color(singleLore)));
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
