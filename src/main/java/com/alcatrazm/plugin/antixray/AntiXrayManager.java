package com.alcatrazm.plugin.antixray;

import com.alcatrazm.plugin.AlcatrazPlugin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AntiXrayManager {

    private final AlcatrazPlugin plugin;
    private boolean enabled;
    private double ratioThresholdPercent;
    private int fastOreThreshold;
    private int windowSeconds;
    private int alertCooldownSeconds;

    private final Set<Material> trackedOres = EnumSet.noneOf(Material.class);
    private final Set<Material> stoneMaterials = EnumSet.noneOf(Material.class);

    // Player UUID -> PlayerMiningData
    private final Map<UUID, PlayerMiningData> playerData = new ConcurrentHashMap<>();
    // Staff UUIDs who disabled alerts
    private final Set<UUID> disabledStaffAlerts = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // Player UUID -> Last Alert Timestamp
    private final Map<UUID, Long> lastAlertTime = new ConcurrentHashMap<>();

    public AntiXrayManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        setupDefaults();
        loadConfig();
    }

    private void setupDefaults() {
        trackedOres.add(Material.DIAMOND_ORE);
        trackedOres.add(Material.DEEPSLATE_DIAMOND_ORE);
        trackedOres.add(Material.ANCIENT_DEBRIS);
        trackedOres.add(Material.EMERALD_ORE);
        trackedOres.add(Material.DEEPSLATE_EMERALD_ORE);
        trackedOres.add(Material.GOLD_ORE);
        trackedOres.add(Material.DEEPSLATE_GOLD_ORE);

        stoneMaterials.add(Material.STONE);
        stoneMaterials.add(Material.DEEPSLATE);
        stoneMaterials.add(Material.TUFF);
        stoneMaterials.add(Material.ANDESITE);
        stoneMaterials.add(Material.DIORITE);
        stoneMaterials.add(Material.GRANITE);
        stoneMaterials.add(Material.NETHERRACK);
        stoneMaterials.add(Material.BASALT);
        stoneMaterials.add(Material.BLACKSTONE);
        stoneMaterials.add(Material.END_STONE);
    }

    public void loadConfig() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("antixray");
        if (sec == null) {
            this.enabled = true;
            this.ratioThresholdPercent = 8.0;
            this.fastOreThreshold = 5;
            this.windowSeconds = 120;
            this.alertCooldownSeconds = 45;
            return;
        }

        this.enabled = sec.getBoolean("enabled", true);
        this.ratioThresholdPercent = sec.getDouble("ratio-threshold-percent", 8.0);
        this.fastOreThreshold = sec.getInt("fast-ore-count-threshold", 5);
        this.windowSeconds = sec.getInt("analysis-window-seconds", 120);
        this.alertCooldownSeconds = sec.getInt("alert-cooldown-seconds", 45);
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isTrackedOre(Material mat) {
        return trackedOres.contains(mat);
    }

    public boolean isStone(Material mat) {
        return stoneMaterials.contains(mat);
    }

    public void recordBlockBreak(Player player, Material material, Location location) {
        if (!enabled || player == null) return;
        if (player.hasPermission("alcatraz.xray.bypass")) return;

        UUID uuid = player.getUniqueId();
        PlayerMiningData data = playerData.computeIfAbsent(uuid, k -> new PlayerMiningData(player.getName()));
        data.setPlayerName(player.getName());

        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;

        boolean isOre = isTrackedOre(material);
        boolean isRock = isStone(material);

        if (isOre) {
            data.addOre(material, now);
        } else if (isRock) {
            data.addStone(now);
        }

        // Clean old timestamps
        data.cleanOldRecords(now - windowMillis);

        if (isOre) {
            checkSuspicion(player, material, data, location, now);
        }
    }

    private void checkSuspicion(Player player, Material ore, PlayerMiningData data, Location loc, long now) {
        UUID uuid = player.getUniqueId();
        Long lastAlert = lastAlertTime.get(uuid);
        if (lastAlert != null && (now - lastAlert) < (alertCooldownSeconds * 1000L)) {
            return; // En cooldown de alerta para este jugador
        }

        int recentOres = data.getRecentOreCount();
        int recentStones = data.getRecentStoneCount();
        double ratio = data.calculateRatio();

        boolean isFastMining = recentOres >= fastOreThreshold;
        boolean isHighRatio = (recentStones >= 15 && ratio >= ratioThresholdPercent);

        if (isFastMining || isHighRatio) {
            lastAlertTime.put(uuid, now);
            sendStaffAlert(player, ore, recentOres, ratio, loc, isFastMining);
        }
    }

    public void sendStaffAlert(Player player, Material ore, int oreCount, double ratio, Location loc, boolean fastMining) {
        String oreName = formatOreName(ore);
        String reason = fastMining ? "&c¡Picado Rápido!" : "&e¡Ratio Alto!";

        String coordsStr = loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();

        TextComponent prefix = new TextComponent(color("&8[&4&lAlcatraz &cAnti-Xray&8] "));
        
        TextComponent body = new TextComponent(color(
                "&7El jugador &e" + player.getName() + " &7picó &b" + oreCount + "x " + oreName + 
                " &7(Ratio: &e" + String.format(Locale.US, "%.1f", ratio) + "%&7) " + reason + " &8» "
        ));

        TextComponent tpButton = new TextComponent(color("&a&l[TELEPORT]"));
        tpButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + player.getName()));
        tpButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new ComponentBuilder(color("&aClick para teletransportarte a &e" + player.getName() + "\n&7Coordenadas: &f" + coordsStr)).create()));

        prefix.addExtra(body);
        prefix.addExtra(tpButton);

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("alcatraz.xray.alerts")) {
                if (!disabledStaffAlerts.contains(staff.getUniqueId())) {
                    staff.spigot().sendMessage(prefix);
                }
            }
        }

        plugin.getLogger().info("[Anti-Xray] Alerta para " + player.getName() + " - " + oreName + " x" + oreCount + " (Ratio " + String.format(Locale.US, "%.1f", ratio) + "%) en " + coordsStr);
    }

    private String formatOreName(Material mat) {
        switch (mat) {
            case DIAMOND_ORE:
            case DEEPSLATE_DIAMOND_ORE:
                return "Diamante";
            case ANCIENT_DEBRIS:
                return "Ancient Debris";
            case EMERALD_ORE:
            case DEEPSLATE_EMERALD_ORE:
                return "Esmeralda";
            case GOLD_ORE:
            case DEEPSLATE_GOLD_ORE:
                return "Oro";
            default:
                return mat.name();
        }
    }

    public boolean toggleStaffAlerts(Player staff) {
        UUID uuid = staff.getUniqueId();
        if (disabledStaffAlerts.contains(uuid)) {
            disabledStaffAlerts.remove(uuid);
            return true; // Alertas activadas
        } else {
            disabledStaffAlerts.add(uuid);
            return false; // Alertas desactivadas
        }
    }

    public boolean hasAlertsEnabled(Player staff) {
        return !disabledStaffAlerts.contains(staff.getUniqueId());
    }

    public PlayerMiningData getPlayerData(UUID uuid) {
        return playerData.get(uuid);
    }

    public Map<UUID, PlayerMiningData> getAllPlayerData() {
        return Collections.unmodifiableMap(playerData);
    }

    public void clearPlayerData(UUID uuid) {
        playerData.remove(uuid);
        lastAlertTime.remove(uuid);
    }

    // Estructura interna de datos
    public static class PlayerMiningData {
        private String playerName;
        private final List<Long> oreTimestamps = new ArrayList<>();
        private final List<Long> stoneTimestamps = new ArrayList<>();
        private final Map<Material, Integer> totalOresMined = new EnumMap<>(Material.class);
        private int totalStonesMined = 0;

        public PlayerMiningData(String playerName) {
            this.playerName = playerName;
        }

        public synchronized void setPlayerName(String name) {
            this.playerName = name;
        }

        public synchronized String getPlayerName() {
            return playerName;
        }

        public synchronized void addOre(Material mat, long time) {
            oreTimestamps.add(time);
            totalOresMined.put(mat, totalOresMined.getOrDefault(mat, 0) + 1);
        }

        public synchronized void addStone(long time) {
            stoneTimestamps.add(time);
            totalStonesMined++;
        }

        public synchronized void cleanOldRecords(long cutoffTime) {
            oreTimestamps.removeIf(t -> t < cutoffTime);
            stoneTimestamps.removeIf(t -> t < cutoffTime);
        }

        public synchronized int getRecentOreCount() {
            return oreTimestamps.size();
        }

        public synchronized int getRecentStoneCount() {
            return stoneTimestamps.size();
        }

        public synchronized double calculateRatio() {
            int ores = oreTimestamps.size();
            int stones = stoneTimestamps.size();
            int total = ores + stones;
            if (total == 0) return 0.0;
            return ((double) ores / total) * 100.0;
        }

        public synchronized Map<Material, Integer> getTotalOresMined() {
            return new HashMap<>(totalOresMined);
        }

        public synchronized int getTotalStonesMined() {
            return totalStonesMined;
        }
    }
}
