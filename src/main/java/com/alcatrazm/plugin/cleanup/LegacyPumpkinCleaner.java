package com.alcatrazm.plugin.cleanup;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.module.AlcatrazModule;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limpiador pasivo y temporal para retirar bloques PLAYER_HEAD heredados del antiguo evento de Halloween.
 * Se activa únicamente mientras exista data/halloween_spawns.yml y se autodestruye al terminar.
 */
public class LegacyPumpkinCleaner implements AlcatrazModule, Listener {

    private static final int BATCH_SIZE = 4;
    private static final int MAX_IN_FLIGHT = 8;
    private static final long MIN_FREE_MEM_BYTES = 400L * 1024L * 1024L;
    private static final double MIN_TPS = 19.0;

    private static final String[] LEGACY_DATA_FILES = {
            "halloween_spawns.yml",
            "halloween_spawns.yml.bak_pre_near_cooldown",
            "halloween_chunks.yml",
            "halloween_players.yml",
            "halloween_progress.yml"
    };

    private final AlcatrazPlugin plugin;
    private final Map<ChunkKey, List<BlockCoord>> pendingByChunk = new ConcurrentHashMap<>();
    private final Queue<ChunkKey> pendingQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final AtomicInteger removedBlocks = new AtomicInteger(0);
    private final AtomicInteger processedChunks = new AtomicInteger(0);

    private BukkitTask sweeperTask;
    private int totalChunks = 0;
    private boolean completed = false;

    public LegacyPumpkinCleaner(AlcatrazPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "legacy-pumpkin-cleanup";
    }

    @Override
    public String getDisplayName() {
        return "Limpieza de Calabazas Legacy";
    }

    @Override
    public boolean isEnabled() {
        return findSpawnsFile() != null;
    }

    @Override
    public void enable() {
        File spawnsFile = findSpawnsFile();
        if (spawnsFile == null) {
            return;
        }

        completed = false;
        pendingByChunk.clear();
        pendingQueue.clear();
        inFlight.set(0);
        removedBlocks.set(0);
        processedChunks.set(0);

        loadCoordinates(spawnsFile);
        if (pendingByChunk.isEmpty()) {
            finishAndPurgeFiles();
            return;
        }

        totalChunks = pendingByChunk.size();
        plugin.getLogger().info("[Cleanup] Detectadas coordenadas legacy de Halloween en " + totalChunks
                + " chunks. Iniciando limpieza asíncrona regulada por TPS/RAM...");

        // Limpiar inmediatamente los chunks que ya estén cargados
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                clearChunkNow(chunk);
            }
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.sweeperTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sweepTick, 40L, 2L);
    }

    @Override
    public void disable() {
        if (sweeperTask != null && !sweeperTask.isCancelled()) {
            sweeperTask.cancel();
        }
        sweeperTask = null;
        HandlerList.unregisterAll(this);

        if (!completed && !pendingByChunk.isEmpty()) {
            writeRemainingToDisk(snapshotPending());
        }
        pendingByChunk.clear();
        pendingQueue.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        clearChunkNow(event.getChunk());
    }

    private void sweepTick() {
        if (pendingQueue.isEmpty()) {
            if (inFlight.get() == 0 && pendingByChunk.isEmpty()) {
                finishAndPurgeFiles();
            }
            return;
        }

        if (inFlight.get() >= MAX_IN_FLIGHT || !hasHealthyHeadroom()) {
            return;
        }

        for (int i = 0; i < BATCH_SIZE; i++) {
            ChunkKey key = pendingQueue.poll();
            if (key == null) {
                break;
            }

            // Si ya fue limpiado al cargarse el chunk por un jugador, omitir
            if (!pendingByChunk.containsKey(key)) {
                onChunkProcessed();
                continue;
            }

            World world = Bukkit.getWorld(key.worldName);
            if (world == null || !world.isChunkGenerated(key.cx, key.cz)) {
                pendingByChunk.remove(key);
                onChunkProcessed();
                continue;
            }

            if (world.isChunkLoaded(key.cx, key.cz)) {
                clearChunkNow(world.getChunkAt(key.cx, key.cz));
                onChunkProcessed();
                continue;
            }

            inFlight.incrementAndGet();
            world.getChunkAtAsync(key.cx, key.cz).whenComplete((chunk, err) -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (err == null && chunk != null) {
                            clearChunkNow(chunk);
                            if (chunk.isLoaded()) {
                                world.unloadChunkRequest(key.cx, key.cz);
                            }
                        } else {
                            pendingByChunk.remove(key);
                        }
                    } finally {
                        inFlight.decrementAndGet();
                        onChunkProcessed();
                    }
                });
            });
        }
    }

    private void clearChunkNow(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        ChunkKey key = new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        List<BlockCoord> coords = pendingByChunk.remove(key);
        if (coords == null || coords.isEmpty()) {
            return;
        }

        World world = chunk.getWorld();
        int minH = world.getMinHeight();
        int maxH = world.getMaxHeight();
        for (BlockCoord c : coords) {
            if (c.y < minH || c.y >= maxH) {
                continue;
            }
            Block block = world.getBlockAt(c.x, c.y, c.z);
            if (block.getType() == Material.PLAYER_HEAD) {
                block.setType(Material.AIR, false);
                removedBlocks.incrementAndGet();
            }
        }
    }

    private void onChunkProcessed() {
        int done = processedChunks.incrementAndGet();
        if (done % 2500 == 0 && totalChunks > 0) {
            plugin.getLogger().info("[Cleanup] Progreso calabazas legacy: " + done + "/" + totalChunks
                    + " chunks (" + removedBlocks.get() + " eliminadas).");
        }
        if (done % 5000 == 0 && !pendingByChunk.isEmpty()) {
            List<Map.Entry<String, List<BlockCoord>>> snap = snapshotPending();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeRemainingToDisk(snap));
        }
        if (pendingQueue.isEmpty() && inFlight.get() == 0 && pendingByChunk.isEmpty()) {
            finishAndPurgeFiles();
        }
    }

    private void finishAndPurgeFiles() {
        if (completed) {
            return;
        }
        completed = true;

        if (sweeperTask != null && !sweeperTask.isCancelled()) {
            sweeperTask.cancel();
        }
        sweeperTask = null;
        HandlerList.unregisterAll(this);

        File dataDir = new File(plugin.getDataFolder(), "data");
        for (String name : LEGACY_DATA_FILES) {
            File f = new File(dataDir, name);
            if (f.exists() && !f.delete()) {
                plugin.getLogger().warning("[Cleanup] No se pudo borrar " + f.getName());
            }
        }
        File rootSpawns = new File(plugin.getDataFolder(), "halloween_spawns.yml");
        if (rootSpawns.exists()) {
            rootSpawns.delete();
        }

        plugin.getLogger().info("[Cleanup] ✔ Limpieza completa: " + removedBlocks.get()
                + " calabazas retiradas del mapa y archivos legacy de Halloween eliminados.");
    }

    private boolean hasHealthyHeadroom() {
        double[] tps = Bukkit.getTPS();
        if (tps.length > 0 && tps[0] < MIN_TPS) {
            return false;
        }
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long available = rt.maxMemory() - used;
        return available >= MIN_FREE_MEM_BYTES;
    }

    private File findSpawnsFile() {
        File dataSpawns = new File(plugin.getDataFolder(), "data/halloween_spawns.yml");
        if (dataSpawns.exists()) {
            return dataSpawns;
        }
        File rootSpawns = new File(plugin.getDataFolder(), "halloween_spawns.yml");
        return rootSpawns.exists() ? rootSpawns : null;
    }

    private void loadCoordinates(File file) {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || Character.isWhitespace(line.charAt(0))) {
                    continue;
                }
                int colonIdx = line.lastIndexOf(':');
                if (colonIdx <= 0) {
                    continue;
                }
                String rawKey = line.substring(0, colonIdx).trim();
                String[] parts = rawKey.split(",", -1);
                if (parts.length != 4) {
                    continue;
                }
                try {
                    String world = parts[0];
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    ChunkKey ck = new ChunkKey(world, x >> 4, z >> 4);
                    pendingByChunk.computeIfAbsent(ck, k -> {
                        pendingQueue.add(k);
                        return new ArrayList<>(2);
                    }).add(new BlockCoord(x, y, z));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("[Cleanup] Error leyendo " + file.getName() + ": " + ex.getMessage());
        }
    }

    private List<Map.Entry<String, List<BlockCoord>>> snapshotPending() {
        List<Map.Entry<String, List<BlockCoord>>> copy = new ArrayList<>(pendingByChunk.size());
        for (Map.Entry<ChunkKey, List<BlockCoord>> entry : pendingByChunk.entrySet()) {
            copy.add(Map.entry(entry.getKey().worldName, List.copyOf(entry.getValue())));
        }
        return copy;
    }

    private synchronized void writeRemainingToDisk(List<Map.Entry<String, List<BlockCoord>>> snapshot) {
        File out = new File(plugin.getDataFolder(), "data/halloween_spawns.yml");
        out.getParentFile().mkdirs();
        try (BufferedWriter writer = Files.newBufferedWriter(out.toPath(), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, List<BlockCoord>> entry : snapshot) {
                String world = entry.getKey();
                for (BlockCoord c : entry.getValue()) {
                    writer.write(world);
                    writer.write(',');
                    writer.write(Integer.toString(c.x));
                    writer.write(',');
                    writer.write(Integer.toString(c.y));
                    writer.write(',');
                    writer.write(Integer.toString(c.z));
                    writer.write(":\n  id: legacy\n");
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("[Cleanup] Error guardando checkpoint: " + ex.getMessage());
        }
    }

    private record ChunkKey(String worldName, int cx, int cz) {}

    private record BlockCoord(int x, int y, int z) {}
}
