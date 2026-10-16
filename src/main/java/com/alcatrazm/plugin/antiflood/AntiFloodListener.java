package com.alcatrazm.plugin.antiflood;

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

public class AntiFloodListener implements Listener {

    private final AntiFloodManager manager;
    private final Map<UUID, ProcessedEntry> processedMessages = new ConcurrentHashMap<>();

    public AntiFloodListener(AntiFloodManager manager) {
        this.manager = manager;
    }

    /**
     * Paper 1.21.x nativo (Adventure)
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPaperChat(AsyncChatEvent event) {
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (rawMessage == null || rawMessage.startsWith("/")) {
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        ProcessedEntry prev = processedMessages.get(uuid);

        if (prev != null && (now - prev.timestamp < 1000)) {
            if (prev.wasCancelled) {
                event.setCancelled(true);
            } else if (prev.finalMessage != null && !prev.finalMessage.equals(rawMessage)) {
                event.message(Component.text(prev.finalMessage));
            }
            return;
        }

        AntiFloodManager.ValidationResult result = manager.checkMessage(player, rawMessage);

        if (!result.allowed) {
            event.setCancelled(true);
            processedMessages.put(uuid, new ProcessedEntry(rawMessage, now, true, null));
            if (result.errorMessage != null) {
                player.sendMessage(result.errorMessage);
            }
        } else {
            String finalMsg = result.finalMessage;
            if (finalMsg != null && !finalMsg.equals(rawMessage)) {
                event.message(Component.text(finalMsg));
            }
            processedMessages.put(uuid, new ProcessedEntry(rawMessage, now, false, finalMsg));
        }
    }

    /**
     * Fallback para Spigot / Bukkit legado
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        String rawMessage = event.getMessage();

        if (rawMessage == null || rawMessage.startsWith("/")) {
            return;
        }

        UUID uuid = player.getUniqueId();
        ProcessedEntry prev = processedMessages.get(uuid);
        long now = System.currentTimeMillis();

        // Si ya fue procesado en este ciclo de chat (< 1000ms), reutilizar resultado sin volver a chequear cooldown
        if (prev != null && (now - prev.timestamp < 1000)) {
            if (prev.wasCancelled) {
                event.setCancelled(true);
            } else if (prev.finalMessage != null && !prev.finalMessage.equals(rawMessage)) {
                event.setMessage(prev.finalMessage);
            }
            return;
        }

        AntiFloodManager.ValidationResult result = manager.checkMessage(player, rawMessage);
        if (!result.allowed) {
            event.setCancelled(true);
            processedMessages.put(uuid, new ProcessedEntry(rawMessage, now, true, null));
            if (result.errorMessage != null) {
                player.sendMessage(result.errorMessage);
            }
        } else {
            if (result.finalMessage != null && !result.finalMessage.equals(rawMessage)) {
                event.setMessage(result.finalMessage);
            }
            processedMessages.put(uuid, new ProcessedEntry(rawMessage, now, false, result.finalMessage));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.removePlayer(event.getPlayer().getUniqueId());
        processedMessages.remove(event.getPlayer().getUniqueId());
    }

    private static class ProcessedEntry {
        final String message;
        final long timestamp;
        final boolean wasCancelled;
        final String finalMessage;

        ProcessedEntry(String message, long timestamp, boolean wasCancelled, String finalMessage) {
            this.message = message;
            this.timestamp = timestamp;
            this.wasCancelled = wasCancelled;
            this.finalMessage = finalMessage;
        }
    }
}
