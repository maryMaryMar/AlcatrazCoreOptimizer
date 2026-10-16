package com.alcatrazm.plugin.chatbubbles;

import com.alcatrazm.plugin.AlcatrazPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatBubbleListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final ChatBubbleManager manager;
    private final Map<UUID, Long> lastBubbleTime = new ConcurrentHashMap<>();

    public ChatBubbleListener(AlcatrazPlugin plugin, ChatBubbleManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPaperChat(AsyncChatEvent event) {
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (message == null || message.trim().isEmpty() || message.startsWith("/")) {
            return;
        }

        lastBubbleTime.put(player.getUniqueId(), System.currentTimeMillis());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !player.isDead()) {
                manager.spawnBubble(player, message);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long prev = lastBubbleTime.get(player.getUniqueId());
        if (prev != null && (now - prev < 200)) {
            return;
        }

        String message = event.getMessage();
        if (message == null || message.trim().isEmpty() || message.startsWith("/")) {
            return;
        }

        lastBubbleTime.put(player.getUniqueId(), now);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !player.isDead()) {
                manager.spawnBubble(player, message);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.removeBubble(event.getPlayer().getUniqueId());
        lastBubbleTime.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        manager.removeBubble(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        manager.removeBubble(event.getPlayer().getUniqueId());
    }
}
