package com.alcatrazm.plugin.chatfilter;

import io.papermc.paper.event.player.AsyncChatEvent;
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

public class ChatFilterListener implements Listener {

    private final ChatFilterManager manager;

    // Seguimiento para evitar procesar dos veces el mismo mensaje en Paper (AsyncChatEvent + AsyncPlayerChatEvent)
    private final Map<UUID, ProcessedEntry> processedMessages = new ConcurrentHashMap<>();

    public ChatFilterListener(ChatFilterManager manager) {
        this.manager = manager;
    }

    /**
     * Listener nativo de Paper 1.21.x (Adventure API)
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPaperChat(AsyncChatEvent event) {
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (rawMessage == null || rawMessage.trim().isEmpty() || rawMessage.startsWith("/")) {
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        ProcessedEntry prev = processedMessages.get(uuid);

        if (prev != null && (now - prev.timestamp < 1000)) {
            if (prev.wasCancelled) {
                event.setCancelled(true);
            }
            return;
        }

        ChatFilterManager.FilterResult result = manager.checkMessage(player, rawMessage);
        boolean cancelled = !result.isAllowed();

        if (cancelled) {
            event.setCancelled(true);
        }

        processedMessages.put(uuid, new ProcessedEntry(rawMessage, now, cancelled));
    }

    /**
     * Listener de compatibilidad para Bukkit / Spigot legado
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        String rawMessage = event.getMessage();

        if (rawMessage == null || rawMessage.trim().isEmpty() || rawMessage.startsWith("/")) {
            return;
        }

        UUID uuid = player.getUniqueId();
        ProcessedEntry prev = processedMessages.get(uuid);
        long now = System.currentTimeMillis();

        if (prev != null && (now - prev.timestamp < 1000)) {
            if (prev.wasCancelled) {
                event.setCancelled(true);
            }
            return;
        }

        ChatFilterManager.FilterResult result = manager.checkMessage(player, rawMessage);
        if (!result.isAllowed()) {
            event.setCancelled(true);
            processedMessages.put(uuid, new ProcessedEntry(rawMessage, now, true));
        } else {
            processedMessages.put(uuid, new ProcessedEntry(rawMessage, now, false));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        processedMessages.remove(event.getPlayer().getUniqueId());
    }

    private static class ProcessedEntry {
        final String message;
        final long timestamp;
        final boolean wasCancelled;

        ProcessedEntry(String message, long timestamp, boolean wasCancelled) {
            this.message = message;
            this.timestamp = timestamp;
            this.wasCancelled = wasCancelled;
        }
    }
}
