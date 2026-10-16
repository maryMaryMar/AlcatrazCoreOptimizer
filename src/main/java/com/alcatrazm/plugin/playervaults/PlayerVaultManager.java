package com.alcatrazm.plugin.playervaults;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerVaultManager {

    private final AlcatrazPlugin plugin;
    private final File vaultsFolder;
    private final Map<Inventory, VaultContext> openVaults = new HashMap<>();
    // Una única instancia de inventario por bóveda (dueño + número) para evitar duplicaciones
    private final Map<String, Inventory> vaultByKey = new HashMap<>();

    public PlayerVaultManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.vaultsFolder = new File(plugin.getDataFolder(), "vaults");
        if (!vaultsFolder.exists()) {
            vaultsFolder.mkdirs();
        }
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private NamespacedKey getVaultKey(int vaultNumber) {
        return new NamespacedKey(plugin, "vault_" + vaultNumber);
    }

    private static String key(UUID owner, int vaultNumber) {
        return owner + ":" + vaultNumber;
    }

    public void openVault(Player viewer, UUID targetUUID, String targetName, int vaultNumber) {
        // Si la bóveda ya está abierta por otra persona, compartir la MISMA instancia
        Inventory existing = vaultByKey.get(key(targetUUID, vaultNumber));
        if (existing != null) {
            viewer.openInventory(existing);
            return;
        }

        File playerFile = new File(vaultsFolder, targetUUID.toString() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

        String title = color("&8[&6Bóveda #" + vaultNumber + "&8] &7" + (viewer.getUniqueId().equals(targetUUID) ? "Admin" : targetName));
        Inventory inv = Bukkit.createInventory(null, 54, title);

        String path = "vaults." + vaultNumber;

        if (config.contains(path)) {
            // El .yml es la fuente de verdad (incluso si la bóveda está vacía)
            for (int i = 0; i < 54; i++) {
                ItemStack item = config.getItemStack(path + "." + i);
                if (item != null) {
                    inv.setItem(i, item);
                }
            }
        } else {
            // Migración única desde el PDC del jugador si nunca existió en el .yml
            Player targetPlayer = Bukkit.getPlayer(targetUUID);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                PersistentDataContainer pdc = targetPlayer.getPersistentDataContainer();
                String base64 = pdc.get(getVaultKey(vaultNumber), PersistentDataType.STRING);
                if (base64 != null && !base64.isEmpty()) {
                    ItemStack[] items = itemStackArrayFromBase64(base64);
                    if (items != null) {
                        for (int i = 0; i < Math.min(items.length, 54); i++) {
                            if (items[i] != null) {
                                inv.setItem(i, items[i]);
                            }
                        }
                    }
                }
            }
        }

        openVaults.put(inv, new VaultContext(targetUUID, vaultNumber, targetName));
        vaultByKey.put(key(targetUUID, vaultNumber), inv);
        viewer.openInventory(inv);
    }

    /**
     * Guarda la bóveda. Se libera la instancia compartida solo cuando cierra el último espectador.
     */
    public void saveVault(Inventory inv) {
        VaultContext context = openVaults.get(inv);
        if (context == null) return;

        writeVault(inv, context);

        // En InventoryCloseEvent el jugador que cierra aún figura como viewer
        if (inv.getViewers().size() <= 1) {
            openVaults.remove(inv);
            vaultByKey.remove(key(context.targetUUID, context.vaultNumber));
        }
    }

    /**
     * Guarda y cierra todas las bóvedas abiertas (apagado / recarga del plugin).
     */
    public void saveAll() {
        for (Map.Entry<Inventory, VaultContext> entry : new HashMap<>(openVaults).entrySet()) {
            writeVault(entry.getKey(), entry.getValue());
            for (org.bukkit.entity.HumanEntity viewer : new java.util.ArrayList<>(entry.getKey().getViewers())) {
                viewer.closeInventory();
            }
        }
        openVaults.clear();
        vaultByKey.clear();
    }

    private void writeVault(Inventory inv, VaultContext context) {
        // 1. Guardado en Archivo YML (fuente de verdad)
        File playerFile = new File(vaultsFolder, context.targetUUID.toString() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

        String path = "vaults." + context.vaultNumber;
        config.set(path, null);
        config.createSection(path); // Persistir aunque quede vacía (evita restauración fantasma desde PDC)

        ItemStack[] items = new ItemStack[54];
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.getType().isAir()) {
                config.set(path + "." + i, item);
                items[i] = item;
            }
        }

        try {
            config.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error al guardar la bóveda #" + context.vaultNumber + " de " + context.targetName + ": " + e.getMessage());
        }

        // 2. Copia espejo en el PDC del Jugador (respaldo)
        Player targetPlayer = Bukkit.getPlayer(context.targetUUID);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            PersistentDataContainer pdc = targetPlayer.getPersistentDataContainer();
            pdc.set(getVaultKey(context.vaultNumber), PersistentDataType.STRING, itemStackArrayToBase64(items));
        }
    }

    public boolean isVaultInventory(Inventory inv) {
        return openVaults.containsKey(inv);
    }

    public static class VaultContext {
        final UUID targetUUID;
        final int vaultNumber;
        final String targetName;

        VaultContext(UUID targetUUID, int vaultNumber, String targetName) {
            this.targetUUID = targetUUID;
            this.vaultNumber = vaultNumber;
            this.targetName = targetName;
        }
    }

    private String itemStackArrayToBase64(ItemStack[] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private ItemStack[] itemStackArrayFromBase64(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack[] items = new ItemStack[dataInput.readInt()];
            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            dataInput.close();
            return items;
        } catch (Exception e) {
            return null;
        }
    }
}
