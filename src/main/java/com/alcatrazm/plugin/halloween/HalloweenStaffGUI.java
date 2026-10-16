package com.alcatrazm.plugin.halloween;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * GUI visual del Staff para el Módulo Halloween.
 *
 * Muestra las cabezas reales de todos los jugadores registrados (online y offline).
 * Al pasar el cursor (lore): saldo actual, calabazas encontradas, canjeadas, estado.
 * Acciones con clic:
 *  - Clic Izquierdo:        +1 calabaza al jugador.
 *  - Clic Derecho:          -1 calabaza al jugador.
 *  - Shift + Clic Derecho:  Resetear saldo a 0.
 *
 * ── NOTAS DE COMPATIBILIDAD ────────────────────────────────────────────────
 * - SkinsRestorer: Jugadores crackeados offline pueden mostrarse con skin Steve.
 *   Cosmético, sin impacto en jugabilidad.
 * - Bedrock (Geyser/Floodgate): UUIDs con prefijo 0000... también pueden mostrarse como Steve.
 *   Cosmético, sin impacto en jugabilidad.
 * - El UUID del jugador se guarda en el PDC del ítem (hw_gui_uuid) para identificar
 *   el clic en HalloweenListener.onInventoryClick.
 */
public class HalloweenStaffGUI {

    /**
     * Título estático. CRÍTICO: debe ser idéntico al que compara
     * HalloweenListener.onInventoryClick con event.getView().getTitle().
     */
    public static final String GUI_TITLE = ChatColor.translateAlternateColorCodes('&',
        "&8» &6&l👁 MONITOR HALLOWEEN &8«");

    private HalloweenStaffGUI() {}

    /**
     * Abre el panel de monitoreo de Halloween para el staff.
     *
     * @param staff   Jugador staff que abre el GUI.
     * @param manager HalloweenManager con los datos actualizados.
     */
    public static void open(Player staff, HalloweenManager manager) {
        List<UUID> allUUIDs = new ArrayList<>(manager.getAllBalances().keySet());

        // Ordenar por saldo descendente (mayor → menor)
        allUUIDs.sort((a, b) -> Integer.compare(manager.getBalance(b), manager.getBalance(a)));

        // Tamaño del inventario dinámico (máximo 54 slots = 6 filas)
        int rows = Math.max(1, Math.min(6, (int) Math.ceil(allUUIDs.size() / 9.0) + 1));
        int size = rows * 9;
        if (size > 54) size = 54;

        Inventory inv = Bukkit.createInventory(null, size, GUI_TITLE);

        NamespacedKey uuidKey = new NamespacedKey(manager.getPlugin(), "hw_gui_uuid");

        int maxSlots = Math.min(allUUIDs.size(), size - 1); // Reservar último slot para info

        for (int i = 0; i < maxSlots; i++) {
            UUID    uuid     = allUUIDs.get(i);
            String  name     = manager.getSavedName(uuid);
            int     balance  = manager.getBalance(uuid);
            int     found    = manager.getTotalFound(uuid);
            int     redeemed = manager.getTotalRedeemed(uuid);

            Player  online   = Bukkit.getPlayer(uuid);
            boolean isOnline = online != null && online.isOnline();

            // Cabeza del jugador con su skin real
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta == null) { inv.setItem(i, head); continue; }

            // setOwningPlayer: usa la caché de Bukkit para jugadores online,
            // puede hacer una llamada a disco para jugadores offline (aceptable en GUI de admin).
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(op);

            meta.setDisplayName(color((isOnline ? "&a● " : "&c○ ") + "&e&l" + name));

            meta.setLore(Arrays.asList(
                color("&7Estado: " + (isOnline ? "&a&lEN LÍNEA" : "&c&lDESCONECTADO")),
                color("&8────────────────────────"),
                color("&7🎃 Encontradas:  &e" + found),
                color("&7🔄 Canjeadas:    &d" + redeemed),
                color("&7💰 Saldo actual: &6&l" + balance),
                color("&8────────────────────────"),
                color("&a▶ Clic Izq:       &a+1 Calabaza"),
                color("&c▶ Clic Der:       &c-1 Calabaza"),
                color("&4▶ Shift + Clic D: &4Resetear a 0")
            ));

            // Guardar UUID en PDC para identificar el clic en el Listener
            meta.getPersistentDataContainer().set(uuidKey, PersistentDataType.STRING, uuid.toString());
            head.setItemMeta(meta);
            inv.setItem(i, head);
        }

        // Ítem informativo en el último slot
        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta  infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(color("&6&l📊 MONITOR EVENTO HALLOWEEN"));
            infoMeta.setLore(Arrays.asList(
                color("&7Jugadores registrados: &e" + allUUIDs.size()),
                color("&7Cabezas activas en mundo: &e" + manager.getActiveHeadCount())
            ));
            info.setItemMeta(infoMeta);
        }
        inv.setItem(size - 1, info);

        staff.openInventory(inv);
    }

    private static String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
