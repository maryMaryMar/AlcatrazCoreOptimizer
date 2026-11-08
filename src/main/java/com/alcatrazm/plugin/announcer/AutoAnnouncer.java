package com.alcatrazm.plugin.announcer;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoAnnouncer {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private final AlcatrazPlugin plugin;
    private File file;
    private FileConfiguration config;
    private BukkitTask task;

    private boolean enabled;
    private int intervalSeconds;
    private boolean randomMode;
    private String prefix = "";
    private boolean soundEnabled;
    private Sound sound;
    private float soundVolume = 0.8f;
    private float soundPitch = 1.2f;

    // Lista de anuncios: cada anuncio es una lista de líneas (1 o más)
    private final List<List<String>> messages = new ArrayList<>();
    private int currentIndex = 0;
    private final Random random = new Random();

    public AutoAnnouncer(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "anuncios.yml");
        loadConfig();
        startTask();
    }

    public void loadConfig() {
        if (!file.exists()) {
            try {
                plugin.saveResource("anuncios.yml", false);
            } catch (Exception e) {
                plugin.getLogger().warning("No se pudo copiar anuncios.yml por defecto: " + e.getMessage());
            }
        }

        this.config = YamlConfiguration.loadConfiguration(file);
        this.enabled = config.getBoolean("configuracion.activado", true);
        this.intervalSeconds = config.getInt("configuracion.intervalo-segundos", 200);
        this.randomMode = config.getBoolean("configuracion.modo-aleatorio", false);
        this.prefix = config.getString("configuracion.prefijo", "&8[&#31d8d7&lPIPÓN MC&8] ");

        this.soundEnabled = config.getBoolean("configuracion.sonido.activado", true);
        String soundName = config.getString("configuracion.sonido.nombre", "BLOCK_NOTE_BLOCK_PLING");
        try {
            this.sound = Sound.valueOf(soundName.toUpperCase());
        } catch (Exception ignored) {
            this.sound = Sound.BLOCK_NOTE_BLOCK_PLING;
        }
        this.soundVolume = (float) config.getDouble("configuracion.sonido.volumen", 0.8);
        this.soundPitch = (float) config.getDouble("configuracion.sonido.tono", 1.2);

        this.messages.clear();
        List<?> rawList = config.getList("mensajes");
        if (rawList != null && !rawList.isEmpty()) {
            for (Object obj : rawList) {
                if (obj instanceof String str) {
                    messages.add(Collections.singletonList(str));
                } else if (obj instanceof List<?> subList) {
                    List<String> lines = new ArrayList<>();
                    for (Object lineObj : subList) {
                        if (lineObj != null) lines.add(lineObj.toString());
                    }
                    if (!lines.isEmpty()) {
                        messages.add(lines);
                    }
                }
            }
        }

        if (this.messages.isEmpty()) {
            this.messages.add(Collections.singletonList("&7¡Protege tu terreno colocando un bloque de protección con &e/ps&7!"));
            this.messages.add(Collections.singletonList("&7¿Dudas o sugerencias? Abre un ticket en Discord: &bhttps://discord.gg/p3jsXtCf3j"));
        }
        this.currentIndex = 0;
    }

    public void reload() {
        stopTask();
        loadConfig();
        startTask();
    }

    public void startTask() {
        if (!enabled || messages.isEmpty()) return;
        long ticks = Math.max(100L, intervalSeconds * 20L);

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            if (players.isEmpty() || messages.isEmpty()) return;

            int index;
            if (randomMode) {
                index = random.nextInt(messages.size());
            } else {
                index = currentIndex;
                currentIndex = (currentIndex + 1) % messages.size();
            }

            List<String> announcement = messages.get(index);
            boolean isMultiLine = announcement.size() > 1;

            for (String rawLine : announcement) {
                String fullLine = isMultiLine ? rawLine : (prefix + rawLine);
                String formatted = colorize(fullLine);
                for (Player p : players) {
                    p.sendMessage(formatted);
                }
            }

            if (soundEnabled && sound != null) {
                for (Player p : players) {
                    p.playSound(p.getLocation(), sound, soundVolume, soundPitch);
                }
            }
        }, ticks, ticks);
    }

    public void stopTask() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Traduce códigos de color clásicos (&) y colores HEX (&#RRGGBB).
     */
    public static String colorize(String text) {
        if (text == null || text.isEmpty()) return "";

        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(sb, "§x§" + hex.charAt(0) + "§" + hex.charAt(1)
                    + "§" + hex.charAt(2) + "§" + hex.charAt(3)
                    + "§" + hex.charAt(4) + "§" + hex.charAt(5));
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public boolean isEnabled() { return enabled; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public int getMessagesCount() { return messages.size(); }
}
