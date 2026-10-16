package com.alcatrazm.plugin.optimizer.chunks;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Descargador ultra-ligero de Chunks de Paso Rápido (Fly-by / Transient Chunks).
 * Utiliza primitivos 'long' (packed chunk keys) para consumir casi 0 bytes en memoria RAM.
 */
public class TransientChunkUnloader implements Listener {

    private final AlcatrazPlugin plugin;
    // Mundo -> (ChunkKey -> timestamp de carga en millis). Separado por mundo para evitar colisiones de coordenadas
    private final Map<java.util.UUID, Map<Long, Long>> loadedChunkTimestamps = new ConcurrentHashMap<>();
    private BukkitTask cleanupTask;

    public TransientChunkUnloader(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        startTask();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("optimizer.transient-chunks.enabled", true);
    }

    public int getUnloadDelaySeconds() {
        return plugin.getConfig().getInt("optimizer.transient-chunks.unload-delay-seconds", 5);
    }

    public void reload() {
        stopTask();
        loadedChunkTimestamps.clear();
        if (isEnabled()) {
            startTask();
        }
    }

    private void startTask() {
        if (!isEnabled()) return;

        // Ejecutar cada 60 ticks (3 segundos) para chequear chunks inactivos
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAndUnloadTransientChunks, 60L, 60L);
    }

    public void stopTask() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isEnabled()) return;
        if (event.isNewChunk()) return;

        Chunk chunk = event.getChunk();
        long key = getChunkKey(chunk.getX(), chunk.getZ());
        loadedChunkTimestamps.computeIfAbsent(chunk.getWorld().getUID(), w -> new ConcurrentHashMap<>())
                .put(key, System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        Map<Long, Long> worldMap = loadedChunkTimestamps.get(chunk.getWorld().getUID());
        if (worldMap != null) {
            worldMap.remove(getChunkKey(chunk.getX(), chunk.getZ()));
        }
    }

    private void checkAndUnloadTransientChunks() {
        if (loadedChunkTimestamps.isEmpty()) return;

        long now = System.currentTimeMillis();
        long thresholdMillis = getUnloadDelaySeconds() * 1000L;
        int simDistanceBlocks = plugin.getConfig().getInt("optimizer.transient-chunks.check-radius-blocks", 64);
        double simDistanceSq = (double) simDistanceBlocks * simDistanceBlocks;

        for (World world : Bukkit.getWorlds()) {
            Chunk spawnChunk = world.getSpawnLocation().getChunk();
            int spawnX = spawnChunk.getX();
            int spawnZ = spawnChunk.getZ();

            Map<Long, Long> worldMap = loadedChunkTimestamps.get(world.getUID());
            if (worldMap == null || worldMap.isEmpty()) continue;

            Iterator<Map.Entry<Long, Long>> it = worldMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, Long> entry = it.next();
                long key = entry.getKey();
                long loadedAt = entry.getValue();

                if (now - loadedAt >= thresholdMillis) {
                    int chunkX = (int) (key >> 32);
                    int chunkZ = (int) key;

                    // No descargar chunks del spawn principal
                    if (Math.abs(chunkX - spawnX) <= 2 && Math.abs(chunkZ - spawnZ) <= 2) {
                        it.remove();
                        continue;
                    }

                    if (!world.isChunkLoaded(chunkX, chunkZ)) {
                        it.remove();
                        continue;
                    }

                    // Verificar si hay algún jugador cerca
                    double centerX = (chunkX << 4) + 8.0;
                    double centerZ = (chunkZ << 4) + 8.0;

                    boolean hasPlayerNearby = false;
                    for (Player p : world.getPlayers()) {
                        double dx = p.getLocation().getX() - centerX;
                        double dz = p.getLocation().getZ() - centerZ;
                        if ((dx * dx + dz * dz) <= simDistanceSq) {
                            hasPlayerNearby = true;
                            break;
                        }
                    }

                    if (!hasPlayerNearby) {
                        world.unloadChunkRequest(chunkX, chunkZ);
                        it.remove();
                    } else {
                        // Si hay un jugador cerca, renovar timestamp
                        entry.setValue(now);
                    }
                }
            }
        }
    }

    private static long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
