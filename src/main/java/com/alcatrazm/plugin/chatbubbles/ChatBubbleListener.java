package com.alcatrazm.plugin.chatbubbles;

import com.alcatrazm.plugin.AlcatrazPlugin;
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
import java.util.regex.Pattern;

public class ChatBubbleListener implements Listener {

    private static final Pattern IC_TAG_WRAPPER = Pattern.compile("<(?:chat|cmd)=[0-9a-fA-F\\-]{36}:(.*?):>");
    private static final Pattern IC_TAG_PLAIN = Pattern.compile(" ?<(?:chat|cmd)=[0-9a-fA-F\\-]{36}>");

    private final AlcatrazPlugin plugin;
    private final ChatBubbleManager manager;
    private final Map<UUID, Long> lastBubbleTime = new ConcurrentHashMap<>();

    public ChatBubbleListener(AlcatrazPlugin plugin, ChatBubbleManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long prev = lastBubbleTime.get(player.getUniqueId());
        if (prev != null && (now - prev < 150L)) {
            return;
        }

        String rawMessage = event.getMessage();
        if (rawMessage == null || rawMessage.trim().isEmpty() || rawMessage.startsWith("/")) {
            return;
        }

        // Sanitizar etiquetas de InteractiveChat (<chat=uuid:...:>, <cmd=uuid>)
        String cleanMessage = IC_TAG_WRAPPER.matcher(rawMessage).replaceAll("$1");
        cleanMessage = IC_TAG_PLAIN.matcher(cleanMessage).replaceAll("").trim();
        if (cleanMessage.isEmpty()) {
            return;
        }

        final String finalMessage = cleanMessage;
        lastBubbleTime.put(player.getUniqueId(), now);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !player.isDead()) {
                manager.spawnBubble(player, finalMessage);
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
