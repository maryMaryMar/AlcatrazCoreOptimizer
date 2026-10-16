package com.alcatrazm.plugin.optimizer.monitor;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import java.util.*;

public class PerformanceMonitor {

    private final AlcatrazPlugin plugin;

    public PerformanceMonitor(AlcatrazPlugin plugin) {
        this.plugin = plugin;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public void sendMonitorReport(CommandSender sender) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        double[] tps = Bukkit.getTPS();
        double mspt = Bukkit.getAverageTickTime();

        sender.sendMessage(color("&8&m========================================"));
        sender.sendMessage(color("&b&l  ALCATRAZ PERFORMANCE & RAM MONITOR"));
        sender.sendMessage(color("&8&m========================================"));
        sender.sendMessage(color("&7⚡ &fTPS: " + formatTps(tps[0]) + " &7(1m)  " + formatTps(tps[1]) + " &7(5m)  " + formatTps(tps[2]) + " &7(15m)"));
        sender.sendMessage(color("&7⏱ &fMSPT: " + formatMspt(mspt) + " &8| &fJugadores Online: &e" + Bukkit.getOnlinePlayers().size()));
        sender.sendMessage(color("&7💾 &fMemoria RAM: &e" + usedMemory + "MB &7/ &a" + maxMemory + "MB &7(Libre: &b" + freeMemory + "MB&7)"));

        sender.sendMessage(color("&8&m----------------------------------------"));
        sender.sendMessage(color("&e&lMundos y Carga de Chunks:"));
        int totalLoadedChunks = 0;
        int totalEntities = 0;

        for (World world : Bukkit.getWorlds()) {
            int chunks = world.getLoadedChunks().length;
            int entities = world.getEntities().size();
            totalLoadedChunks += chunks;
            totalEntities += entities;
            sender.sendMessage(color(" &8▪ &f" + world.getName() + ": &b" + chunks + " &7chunks &8| &e" + entities + " &7entidades"));
        }
        sender.sendMessage(color("&7Total Global: &a" + totalLoadedChunks + " chunks &8| &a" + totalEntities + " entidades"));

        sender.sendMessage(color("&8&m----------------------------------------"));
        sender.sendMessage(color("&c&lTop 3 Zonas/Chunks con Más Entidades:"));
        List<ChunkData> topChunks = getTopEntityChunks(3);
        if (topChunks.isEmpty()) {
            sender.sendMessage(color(" &8▪ &7No hay chunks cargados."));
        } else {
            int rank = 1;
            for (ChunkData cd : topChunks) {
                sender.sendMessage(color(" &8" + rank + ". &f" + cd.worldName + " &7[&eX: " + (cd.x << 4) + ", Z: " + (cd.z << 4) + "&7] &8-> &c" + cd.entityCount + " entidades"));
                rank++;
            }
        }
        sender.sendMessage(color("&8&m========================================"));
    }

    public void cleanServer(CommandSender sender) {
        sender.sendMessage(color("&8[&bAlcatraz&cOptimizer&8] &7Iniciando limpieza profunda y recolección de memoria..."));

        int removedItems = 0;
        int removedArrows = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item) {
                    if (item.getTicksLived() > 200) { // más de 10s en el suelo
                        item.remove();
                        removedItems++;
                    }
                } else if (entity instanceof Arrow arrow) {
                    if (arrow.isInBlock()) {
                        arrow.remove();
                        removedArrows++;
                    }
                }
            }
        }

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);

        sender.sendMessage(color("&8[&bAlcatraz&cOptimizer&8] &a¡Limpieza completada con éxito!"));
        sender.sendMessage(color(" &8▪ &7Ítems eliminados: &e" + removedItems));
        sender.sendMessage(color(" &8▪ &7Flechas clavadas eliminadas: &e" + removedArrows));
        sender.sendMessage(color(" &8▪ &7Memoria RAM en uso: &e" + usedMemory + " MB / " + maxMemory + " MB"));
    }

    private List<ChunkData> getTopEntityChunks(int limit) {
        List<ChunkData> list = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                int count = chunk.getEntities().length;
                if (count > 0) {
                    list.add(new ChunkData(world.getName(), chunk.getX(), chunk.getZ(), count));
                }
            }
        }
        list.sort((a, b) -> Integer.compare(b.entityCount, a.entityCount));
        return list.subList(0, Math.min(limit, list.size()));
    }

    private String formatTps(double tps) {
        double val = Math.min(20.0, Math.round(tps * 100.0) / 100.0);
        if (val >= 19.0) return "&a" + val;
        if (val >= 16.0) return "&e" + val;
        return "&c" + val;
    }

    private String formatMspt(double mspt) {
        double val = Math.round(mspt * 10.0) / 10.0;
        if (val <= 35.0) return "&a" + val + "ms";
        if (val <= 50.0) return "&e" + val + "ms";
        return "&c" + val + "ms";
    }

    private static class ChunkData {
        final String worldName;
        final int x;
        final int z;
        final int entityCount;

        ChunkData(String worldName, int x, int z, int entityCount) {
            this.worldName = worldName;
            this.x = x;
            this.z = z;
            this.entityCount = entityCount;
        }
    }
}
