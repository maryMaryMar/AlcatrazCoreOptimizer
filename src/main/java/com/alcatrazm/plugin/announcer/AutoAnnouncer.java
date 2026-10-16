package com.alcatrazm.plugin.announcer;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AutoAnnouncer {

    private final AlcatrazPlugin plugin;
    private BukkitTask task;
    private boolean enabled;
    private int intervalSeconds;
    private List<String> messages = new ArrayList<>();
    private int currentIndex = 0;

    public AutoAnnouncer(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        startTask();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("announcer.enabled", true);
        this.intervalSeconds = plugin.getConfig().getInt("announcer.interval-seconds", 300);
        this.messages = plugin.getConfig().getStringList("announcer.messages");
        if (this.messages.isEmpty()) {
            this.messages = Arrays.asList(
                    "&8[&bAlcatraz&8] &7¡Protege tu terreno de 80 a 150 bloques con &e/claim create <nombre>&7!",
                    "&8[&bAlcatraz&8] &7¿Tienes basura acumulada? Usa &e/trash &7para destruirla al instante sin generar lag.",
                    "&8[&bAlcatraz&8] &7¡Consigue la poderosa &b&lHoja Sónica &7o la &5&lPechera con Élitra &7del inframundo!",
                    "&8[&bAlcatraz&8] &7Optimiza tus granjas evitando acumular más de 20 animales por chunk."
            );
        }
    }

    public void reload() {
        stopTask();
        loadConfig();
        startTask();
    }

    public void startTask() {
        if (!enabled || messages.isEmpty()) return;
        long ticks = Math.max(200L, intervalSeconds * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty() || messages.isEmpty()) return;

            String msg = messages.get(currentIndex);
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));

            currentIndex = (currentIndex + 1) % messages.size();
        }, ticks, ticks);
    }

    public void stopTask() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
    }
}
