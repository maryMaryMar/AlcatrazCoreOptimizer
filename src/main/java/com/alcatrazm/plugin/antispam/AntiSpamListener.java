package com.alcatrazm.plugin.antispam;

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

public class AntiSpamListener implements Listener {

    private final AntiSpamManager manager;
    private final Map<UUID, ProcessedEntry> processedMessages = new ConcurrentHashMap<>();

    public AntiSpamListener(AntiSpamManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPaperChat(AsyncChatEvent event) {
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (raw == null || raw.startsWith("/")) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        ProcessedEntry prev = processedMessages.get(uuid);

        if (prev != null && (now - prev.timestamp < 1000)) {
            if (prev.wasCancelled) {
                event.setCancelled(true);
            }
            return;
        }

        AntiSpamManager.SpamResult result = manager.checkSpam(player, raw);
        boolean cancelled = !result.allowed;

        if (cancelled) {
            event.setCancelled(true);
        }

        processedMessages.put(uuid, new ProcessedEntry(raw, now, cancelled));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        String raw = event.getMessage();
        if (raw == null || raw.startsWith("/")) return;

        UUID uuid = player.getUniqueId();
        ProcessedEntry prev = processedMessages.get(uuid);
        long now = System.currentTimeMillis();

        if (prev != null && (now - prev.timestamp < 1000)) {
            if (prev.wasCancelled) {
                event.setCancelled(true);
            }
            return;
        }

        AntiSpamManager.SpamResult result = manager.checkSpam(player, raw);
        if (!result.allowed) {
            event.setCancelled(true);
            processedMessages.put(uuid, new ProcessedEntry(raw, now, true));
        } else {
            processedMessages.put(uuid, new ProcessedEntry(raw, now, false));
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

        ProcessedEntry(String message, long timestamp, boolean wasCancelled) {
            this.message = message;
            this.timestamp = timestamp;
            this.wasCancelled = wasCancelled;
        }
    }
}
