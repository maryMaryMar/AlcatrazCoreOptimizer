package com.alcatrazm.plugin.chatbubbles;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatBubbleManager {

    private final AlcatrazPlugin plugin;
    private final NamespacedKey bubbleKey;
    private final Map<UUID, ActiveBubble> activeBubbles = new ConcurrentHashMap<>();
    private final Set<UUID> disabledPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private BukkitTask updateTask;

    // Configuración en memoria
    private boolean enabled = true;
    private int durationTicks = 80; // 4 segundos
    private int maxCharsPerLine = 32;
    private int maxLines = 3;
    private double verticalOffset = 0.95; // Flota a ~2.6 bloques desde el suelo, bien despegado de la cabeza
    private boolean crowdLimitEnabled = true;
    private int maxNearbyPlayers = 15;
    private double crowdRadius = 15.0;

    private static class ActiveBubble {
        final TextDisplay entity;
        int remainingTicks;

        ActiveBubble(TextDisplay entity, int remainingTicks) {
            this.entity = entity;
            this.remainingTicks = remainingTicks;
        }
    }

    public ChatBubbleManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.bubbleKey = new NamespacedKey(plugin, "chat_bubble");
        loadConfig();
        startTask();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("chat-bubbles.enabled", true);
        this.durationTicks = plugin.getConfig().getInt("chat-bubbles.duration-ticks", 80);
        this.maxCharsPerLine = plugin.getConfig().getInt("chat-bubbles.max-chars-per-line", 32);
        this.maxLines = plugin.getConfig().getInt("chat-bubbles.max-lines", 3);
        this.verticalOffset = plugin.getConfig().getDouble("chat-bubbles.vertical-offset", 0.95);
        this.crowdLimitEnabled = plugin.getConfig().getBoolean("chat-bubbles.crowd-limit.enabled", true);
        this.maxNearbyPlayers = plugin.getConfig().getInt("chat-bubbles.crowd-limit.max-players", 15);
        this.crowdRadius = plugin.getConfig().getDouble("chat-bubbles.crowd-limit.radius", 15.0);

        List<String> disabled = plugin.getConfig().getStringList("chat-bubbles.disabled-players");
        disabledPlayers.clear();
        for (String s : disabled) {
            try {
                disabledPlayers.add(UUID.fromString(s));
            } catch (Exception ignored) {}
        }
    }

    public void saveDisabledPlayers() {
        List<String> list = new ArrayList<>();
        for (UUID u : disabledPlayers) {
            list.add(u.toString());
        }
        plugin.getConfig().set("chat-bubbles.disabled-players", list);
        plugin.saveConfig();
    }

    public boolean togglePlayer(UUID uuid) {
        boolean disabled;
        if (disabledPlayers.contains(uuid)) {
            disabledPlayers.remove(uuid);
            disabled = false;
        } else {
            disabledPlayers.add(uuid);
            disabled = true;
            removeBubble(uuid);
        }
        saveDisabledPlayers();
        return !disabled;
    }

    public boolean isPlayerEnabled(UUID uuid) {
        return !disabledPlayers.contains(uuid);
    }

    private void startTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeBubbles.isEmpty()) return;

            Iterator<Map.Entry<UUID, ActiveBubble>> it = activeBubbles.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, ActiveBubble> entry = it.next();
                UUID uuid = entry.getKey();
                ActiveBubble bubble = entry.getValue();

                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline() || player.isDead() || bubble.entity == null || !bubble.entity.isValid()) {
                    if (bubble.entity != null && bubble.entity.isValid()) {
                        bubble.entity.remove();
                    }
                    it.remove();
                    continue;
                }

                bubble.remainingTicks--;
                if (bubble.remainingTicks <= 0) {
                    bubble.entity.remove();
                    it.remove();
                    continue;
                }

                // Seguimiento fluido de la cabeza del jugador con la altura configurada
                Location targetLoc = player.getLocation().add(0, player.getEyeHeight() + verticalOffset, 0);
                bubble.entity.teleport(targetLoc);
            }
        }, 1L, 1L);
    }

    public void spawnBubble(Player player, String message) {
        if (!enabled || !isPlayerEnabled(player.getUniqueId())) {
            return;
        }

        // Filtro de multitud: si hay más de X jugadores cerca, no mostrar globo
        if (crowdLimitEnabled) {
            long nearbyCount = player.getNearbyEntities(crowdRadius, crowdRadius, crowdRadius).stream()
                    .filter(e -> e instanceof Player && !e.getUniqueId().equals(player.getUniqueId()))
                    .count();
            if (nearbyCount >= maxNearbyPlayers) {
                return; // Supera el límite de multitud
            }
        }

        String formattedText = formatBubbleText(message);
        if (formattedText.isEmpty()) return;

        Location spawnLoc = player.getLocation().add(0, player.getEyeHeight() + verticalOffset, 0);

        ActiveBubble existing = activeBubbles.get(player.getUniqueId());
        if (existing != null && existing.entity != null && existing.entity.isValid()) {
            // Actualizar texto del globo existente y reiniciar tiempo
            existing.entity.setText(formattedText);
            existing.remainingTicks = durationTicks;
            return;
        }

        try {
            TextDisplay display = spawnLoc.getWorld().spawn(spawnLoc, TextDisplay.class, td -> {
                td.setPersistent(false); // No guardar en disco / chunk .mca
                td.getPersistentDataContainer().set(bubbleKey, PersistentDataType.BYTE, (byte) 1);
                td.setBillboard(Display.Billboard.CENTER);
                td.setText(formattedText);
                // Fondo blanco nítido estilo viñeta cómic
                td.setBackgroundColor(Color.fromARGB(245, 255, 255, 255));
                td.setShadowed(false);
                td.setSeeThrough(false);
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                td.setTeleportDuration(2); // Suavizado nativo
            });

            activeBubbles.put(player.getUniqueId(), new ActiveBubble(display, durationTicks));
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo generar TextDisplay para ChatBubble: " + e.getMessage());
        }
    }

    public void removeBubble(UUID uuid) {
        ActiveBubble bubble = activeBubbles.remove(uuid);
        if (bubble != null && bubble.entity != null && bubble.entity.isValid()) {
            bubble.entity.remove();
        }
    }

    public void cleanAll() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        for (ActiveBubble b : activeBubbles.values()) {
            if (b.entity != null && b.entity.isValid()) {
                b.entity.remove();
            }
        }
        activeBubbles.clear();
    }

    private String formatBubbleText(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";

        String clean = ChatColor.stripColor(raw).trim();
        if (clean.isEmpty()) return "";

        List<String> lines = new ArrayList<>();
        String[] words = clean.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxCharsPerLine) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }
                while (word.length() > maxCharsPerLine) {
                    lines.add(word.substring(0, maxCharsPerLine));
                    word = word.substring(maxCharsPerLine);
                }
            }
            if (currentLine.length() > 0) currentLine.append(" ");
            currentLine.append(word);
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        if (lines.size() > maxLines) {
            lines = lines.subList(0, maxLines);
            String last = lines.get(lines.size() - 1);
            if (last.length() > 3) {
                lines.set(lines.size() - 1, last.substring(0, last.length() - 3) + "...");
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append("§0").append(lines.get(i));
            if (i < lines.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public double getVerticalOffset() { return verticalOffset; }
    public void setVerticalOffset(double verticalOffset) { this.verticalOffset = verticalOffset; }
}
