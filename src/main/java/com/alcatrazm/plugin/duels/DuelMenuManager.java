package com.alcatrazm.plugin.duels;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class DuelMenuManager {

    private final AlcatrazPlugin plugin;
    private final String modalityMenuTitle;
    private final Map<UUID, String> playerSelectedModality = new HashMap<>();
    private final Map<UUID, Integer> playerCurrentPage = new HashMap<>();

    public static final String MODALITY_TITLE = "&8&l⚔ MODALIDADES DE DUELO ⚔";
    public static final String PLAYER_SELECTION_TITLE_PREFIX = "&8⚔ Retar: &e";

    public record Modality(String id, String displayName, Material icon, String[] description) {}

    private final List<Modality> modalities = new ArrayList<>();

    public static final Map<String, String> MODALITY_ARENA_MAP = Map.of(
            "potpvp", "potpvp",
            "sumo", "sumo",
            "Mazo", "arena3",
            "uhc", "arena2",
            "Diamond", "arena1"
    );

    public record PendingChallenge(UUID challenger, String modalityId, long timestamp) {}
    private final Map<UUID, PendingChallenge> pendingChallenges = new HashMap<>();

    public DuelMenuManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.modalityMenuTitle = ChatColor.translateAlternateColorCodes('&', MODALITY_TITLE);
        setupModalities();
    }

    public AlcatrazPlugin getPlugin() {
        return plugin;
    }

    private void setupModalities() {
        modalities.add(new Modality(
                "potpvp",
                "&d&l🧪 PotPvP (Nodebuff)",
                Material.SPLASH_POTION,
                new String[]{
                        "&7Armadura: &bDiamante Protec IV",
                        "&7Armas: &bEspada Sharpness V & Perlas",
                        "&7Efectos: &dPociones de Curación y Velocidad II",
                        "",
                        "&e▶ Clic para elegir oponente."
                }
        ));

        modalities.add(new Modality(
                "sumo",
                "&a&l🥊 Sumo",
                Material.SLIME_BALL,
                new String[]{
                        "&7Reglas: &aPlataforma circular en el vacío",
                        "&7Equipamiento: &aSin armas ni armadura",
                        "&7Objetivo: &aTirar al rival por knockback",
                        "",
                        "&e▶ Clic para elegir oponente."
                }
        ));

        modalities.add(new Modality(
                "Mazo",
                "&6&l🔨 Mazo 1.21",
                Material.MACE,
                new String[]{
                        "&7Novedad: &6Mecánica oficial 1.21",
                        "&7Ataques: &6Smash aéreo con Wind Charges",
                        "&7Armadura: &6Set reforzado para impacto",
                        "",
                        "&e▶ Clic para elegir oponente."
                }
        ));

        modalities.add(new Modality(
                "uhc",
                "&e&l🍎 BuildUHC",
                Material.GOLDEN_APPLE,
                new String[]{
                        "&7Reglas: &eSin regeneración natural",
                        "&7Consumibles: &eManzanas y Cabezas Doradas",
                        "&7Táctica: &eLava, agua, bloques y arco",
                        "",
                        "&e▶ Clic para elegir oponente."
                }
        ));

        modalities.add(new Modality(
                "Diamond",
                "&b&l⚔ Diamante Clásico",
                Material.DIAMOND_SWORD,
                new String[]{
                        "&7Armadura: &bSet de Diamante completo",
                        "&7Armas: &bEspada, caña de pescar y arco",
                        "&7Estilo: &bCombate 1v1 tradicional",
                        "",
                        "&e▶ Clic para elegir oponente."
                }
        ));
    }

    public void openModalityMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, modalityMenuTitle);

        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        int[] slots = {11, 12, 13, 14, 15};
        for (int i = 0; i < modalities.size() && i < slots.length; i++) {
            Modality mod = modalities.get(i);
            ItemStack item = createItem(mod.icon(), mod.displayName(), mod.description());
            inv.setItem(slots[i], item);
        }

        ItemStack spectateItem = createItem(
                Material.COMPASS,
                "&3&l👁 Espectear Duelos en Curso",
                new String[]{
                        "&7Observa las partidas de duelo activas",
                        "&7en tiempo real como espectador.",
                        "",
                        "&e▶ Clic para abrir modo espectador."
                }
        );
        inv.setItem(22, spectateItem);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f);
    }

    public void openPlayerMenu(Player player, String modalityId, int page) {
        Modality mod = getModality(modalityId);
        String modName = mod != null ? mod.displayName() : modalityId;
        playerSelectedModality.put(player.getUniqueId(), modalityId);
        playerCurrentPage.put(player.getUniqueId(), page);

        String title = ChatColor.translateAlternateColorCodes('&', PLAYER_SELECTION_TITLE_PREFIX + modName);
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        Inventory inv = Bukkit.createInventory(null, 54, title);

        List<Player> availablePlayers = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(player.getUniqueId())) {
                availablePlayers.add(p);
            }
        }

        int itemsPerPage = 45;
        int totalPlayers = availablePlayers.size();
        int maxPage = Math.max(0, (totalPlayers - 1) / itemsPerPage);
        page = Math.min(page, maxPage);
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalPlayers);

        if (totalPlayers == 0) {
            ItemStack noPlayers = createItem(
                    Material.BARRIER,
                    "&c&l¡No hay otros jugadores conectados!",
                    new String[]{
                            "&7En este momento no hay más usuarios online.",
                            "&7Espera a que ingrese alguien para retarlo.",
                            "",
                            "&c« Haz clic abajo para volver."
                    }
            );
            inv.setItem(22, noPlayers);
        } else {
            int slot = 0;
            for (int i = startIndex; i < endIndex; i++) {
                Player target = availablePlayers.get(i);
                ItemStack head = createPlayerHead(target, modName);
                inv.setItem(slot++, head);
            }
        }

        ItemStack bottomBorder = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, bottomBorder);
        }

        ItemStack backBtn = createItem(Material.ARROW, "&c&l« Volver a Modalidades", new String[]{"&7Regresa a la lista de modos de duelo."});
        inv.setItem(45, backBtn);

        if (page > 0) {
            ItemStack prevBtn = createItem(Material.PAPER, "&e&l◀ Página Anterior &7(" + page + "/" + (maxPage + 1) + ")", null);
            inv.setItem(48, prevBtn);
        }

        ItemStack refreshBtn = createItem(Material.SUNFLOWER, "&6&l⟳ Actualizar Lista", new String[]{"&7Recarga los jugadores online."});
        inv.setItem(49, refreshBtn);

        if (page < maxPage) {
            ItemStack nextBtn = createItem(Material.PAPER, "&e&lPágina Siguiente ▶ &7(" + (page + 2) + "/" + (maxPage + 1) + ")", null);
            inv.setItem(50, nextBtn);
        }

        ItemStack closeBtn = createItem(Material.BARRIER, "&4&l✕ Cerrar Menú", null);
        inv.setItem(53, closeBtn);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
    }

    private ItemStack createPlayerHead(Player target, String modName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&l" + target.getName()));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Modalidad: &b" + modName));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Ping: &f" + target.getPing() + " ms"));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Vida: &c" + (int) target.getHealth() + "/20 ❤"));
            lore.add("");
            lore.add(ChatColor.translateAlternateColorCodes('&', "&a▶ Clic izquierdo para enviar reto."));
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private final Set<UUID> bypassSet = new HashSet<>();

    public void sendDuelChallenge(Player sender, Player target, String modalityId) {
        Modality mod = getModality(modalityId);
        String modName = mod != null ? mod.displayName() : modalityId;

        pendingChallenges.put(target.getUniqueId(), new PendingChallenge(sender.getUniqueId(), modalityId, System.currentTimeMillis()));

        sender.closeInventory();
        sender.playSound(sender.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&6&lDUELOS&8] &a¡Has retado a &e" + target.getName() + "&a a un duelo de &b" + modName + "&a!"));

        bypassSet.add(sender.getUniqueId());
        try {
            sender.performCommand("versus:duel " + target.getName());
        } finally {
            bypassSet.remove(sender.getUniqueId());
        }
    }

    public PendingChallenge getPendingChallenge(UUID targetUuid) {
        PendingChallenge challenge = pendingChallenges.get(targetUuid);
        if (challenge != null && (System.currentTimeMillis() - challenge.timestamp() > 120_000)) {
            pendingChallenges.remove(targetUuid);
            return null;
        }
        return challenge;
    }

    public void removePendingChallenge(UUID targetUuid) {
        pendingChallenges.remove(targetUuid);
    }

    public void prepareArenaForModality(String modalityId) {
        String targetArenaName = MODALITY_ARENA_MAP.get(modalityId);
        if (targetArenaName == null) return;
        try {
            Class<?> arenaManagerClass = Class.forName("me.robomonkey.versus.arena.ArenaManager");
            Object managerInstance = arenaManagerClass.getMethod("getInstance").invoke(null);
            Field arenaListField = arenaManagerClass.getDeclaredField("arenaList");
            arenaListField.setAccessible(true);
            List<?> list = (List<?>) arenaListField.get(managerInstance);
            if (list != null) {
                for (Object arena : list) {
                    Method getNameMethod = arena.getClass().getMethod("getName");
                    String name = (String) getNameMethod.invoke(arena);
                    Field enabledField = arena.getClass().getDeclaredField("enabled");
                    enabledField.setAccessible(true);
                    enabledField.set(arena, name.equalsIgnoreCase(targetArenaName));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo preparar arena para " + modalityId + ": " + e.getMessage());
        }
    }

    public void restoreAllArenas() {
        try {
            Class<?> arenaManagerClass = Class.forName("me.robomonkey.versus.arena.ArenaManager");
            Object managerInstance = arenaManagerClass.getMethod("getInstance").invoke(null);
            Field arenaListField = arenaManagerClass.getDeclaredField("arenaList");
            arenaListField.setAccessible(true);
            List<?> list = (List<?>) arenaListField.get(managerInstance);
            if (list != null) {
                for (Object arena : list) {
                    Field enabledField = arena.getClass().getDeclaredField("enabled");
                    enabledField.setAccessible(true);
                    enabledField.set(arena, true);
                }
            }
        } catch (Exception e) {
            // Ignorar
        }
    }

    public boolean isBypassing(UUID uuid) {
        return bypassSet.contains(uuid);
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid == null) return;
        playerSelectedModality.remove(uuid);
        playerCurrentPage.remove(uuid);
        pendingChallenges.remove(uuid);
        bypassSet.remove(uuid);
        pendingChallenges.values().removeIf(challenge -> challenge.challenger().equals(uuid));
    }

    public Modality getModality(String id) {
        for (Modality m : modalities) {
            if (m.id().equalsIgnoreCase(id)) return m;
        }
        return null;
    }

    public List<Modality> getModalities() {
        return modalities;
    }

    public String getSelectedModality(UUID uuid) {
        return playerSelectedModality.get(uuid);
    }

    public int getCurrentPage(UUID uuid) {
        return playerCurrentPage.getOrDefault(uuid, 0);
    }

    private ItemStack createItem(Material material, String name, String[] lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            }
            if (lore != null && lore.length > 0) {
                List<String> loreList = new ArrayList<>();
                for (String l : lore) {
                    loreList.add(ChatColor.translateAlternateColorCodes('&', l));
                }
                meta.setLore(loreList);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }
}
