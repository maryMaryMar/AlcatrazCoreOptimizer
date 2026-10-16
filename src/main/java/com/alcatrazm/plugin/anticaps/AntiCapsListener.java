package com.alcatrazm.plugin.anticaps;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiCapsListener implements Listener {

    private final AntiCapsManager manager;
    private final Map<UUID, ProcessedEntry> processedMessages = new ConcurrentHashMap<>();

    public AntiCapsListener(AntiCapsManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPaperChat(AsyncChatEvent event) {
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (raw == null || raw.startsWith("/")) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        ProcessedEntry prev = processedMessages.get(uuid);

        if (prev != null && (now - prev.timestamp < 1000)) {
            if (prev.formattedMessage != null && !prev.formattedMessage.equals(raw)) {
                event.message(Component.text(prev.formattedMessage));
            }
            return;
        }

        String formatted = manager.processMessage(player, raw);
        if (formatted != null && !formatted.equals(raw)) {
            event.message(Component.text(formatted));
        }

        processedMessages.put(uuid, new ProcessedEntry(raw, now, formatted));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        String raw = event.getMessage();
        if (raw == null || raw.startsWith("/")) return;

        UUID uuid = player.getUniqueId();
        ProcessedEntry prev = processedMessages.get(uuid);
        long now = System.currentTimeMillis();

        if (prev != null && (now - prev.timestamp < 1000)) {
            if (prev.formattedMessage != null && !prev.formattedMessage.equals(raw)) {
                event.setMessage(prev.formattedMessage);
            }
            return;
        }

        String formatted = manager.processMessage(player, raw);
        if (formatted != null && !formatted.equals(raw)) {
            event.setMessage(formatted);
        }
        processedMessages.put(uuid, new ProcessedEntry(raw, now, formatted));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        processedMessages.remove(event.getPlayer().getUniqueId());
    }

    private static class ProcessedEntry {
        final String originalMessage;
        final long timestamp;
        final String formattedMessage;

        ProcessedEntry(String originalMessage, long timestamp, String formattedMessage) {
            this.originalMessage = originalMessage;
            this.timestamp = timestamp;
            this.formattedMessage = formattedMessage;
        }
    }
}
