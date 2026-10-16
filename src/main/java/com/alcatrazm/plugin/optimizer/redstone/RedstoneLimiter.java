package com.alcatrazm.plugin.optimizer.redstone;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador y protector contra Máquinas de Lag de Redstone (Clocks infinitos rápidos).
 */
public class RedstoneLimiter implements Listener {

    private final AlcatrazPlugin plugin;
    // Map de LocationHash -> RedstoneTracker
    private final Map<Long, RedstoneTracker> activityMap = new ConcurrentHashMap<>();
    private long lastCleanup = System.currentTimeMillis();

    public RedstoneLimiter(AlcatrazPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("optimizer.redstone.anti-lag-clocks", true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        if (!isEnabled()) return;

        // Solo medir cambios de estado de apagado a encendido
        if (event.getOldCurrent() != 0 || event.getNewCurrent() <= 0) return;

        Block block = event.getBlock();
        Location loc = block.getLocation();
        long posKey = getBlockKey(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        long now = System.currentTimeMillis();
        RedstoneTracker tracker = activityMap.computeIfAbsent(posKey, k -> new RedstoneTracker(now));

        int maxPulsesPerSec = plugin.getConfig().getInt("optimizer.redstone.max-pulses-per-second", 20);

        if (tracker.recordPulse(now) > maxPulsesPerSec) {
            // Cancelar la señal para congelar el reloj excesivo
            event.setNewCurrent(0);

            if (tracker.shouldWarn(now)) {
                plugin.getLogger().warning("[AlcatrazOptimizer] Reloj de Redstone excesivo detectado y mitigado en X: " + 
                        loc.getBlockX() + " Y: " + loc.getBlockY() + " Z: " + loc.getBlockZ() + " (" + loc.getWorld().getName() + ")");
            }
        }

        // Limpieza periódica cada 10 segundos
        if (now - lastCleanup > 10000) {
            lastCleanup = now;
            cleanupOldTrackers(now);
        }
    }

    private void cleanupOldTrackers(long now) {
        Iterator<Map.Entry<Long, RedstoneTracker>> it = activityMap.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastPulse > 5000) {
                it.remove();
            }
        }
    }

    private static long getBlockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (long) (z & 0x3FFFFFF);
    }

    private static class RedstoneTracker {
        int count = 0;
        long windowStart;
        long lastPulse;
        long lastWarn = 0;

        RedstoneTracker(long now) {
            this.windowStart = now;
            this.lastPulse = now;
        }

        int recordPulse(long now) {
            this.lastPulse = now;
            if (now - windowStart >= 1000) {
                windowStart = now;
                count = 1;
            } else {
                count++;
            }
            return count;
        }

        boolean shouldWarn(long now) {
            if (now - lastWarn >= 10000) {
                lastWarn = now;
                return true;
            }
            return false;
        }
    }
}
