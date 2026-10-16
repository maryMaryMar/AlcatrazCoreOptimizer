package com.alcatrazm.plugin.optimizer.tnt;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;

import java.util.HashMap;
import java.util.Map;

public class TNTOptimizer implements Listener {

    private final AlcatrazPlugin plugin;

    private boolean enabled;
    private int maxTNTPerChunk;
    private int maxFallingBlocksPerChunk;
    private boolean optimizeExplosions;
    private int maxExplosionBlocks;

    // Conteo por tick para evitar lag masivo en explosiones concurrentes
    private long currentTick = -1;
    private int explosionsThisTick = 0;
    private final int maxExplosionsPerTick = 12;

    public TNTOptimizer(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("optimizer.tnt.enabled", true);
        this.maxTNTPerChunk = plugin.getConfig().getInt("optimizer.tnt.max-primed-tnt-per-chunk", 25);
        this.maxFallingBlocksPerChunk = plugin.getConfig().getInt("optimizer.tnt.max-falling-blocks-per-chunk", 40);
        this.optimizeExplosions = plugin.getConfig().getBoolean("optimizer.tnt.optimize-explosions", true);
        this.maxExplosionBlocks = plugin.getConfig().getInt("optimizer.tnt.max-explosion-blocks", 150);
    }

    public void reload() {
        loadConfig();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!enabled) return;

        Entity entity = event.getEntity();
        if (entity instanceof TNTPrimed) {
            Chunk chunk = entity.getLocation().getChunk();
            int tntCount = 0;
            for (Entity e : chunk.getEntities()) {
                if (e instanceof TNTPrimed) {
                    tntCount++;
                    if (tntCount > maxTNTPerChunk) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        } else if (entity instanceof FallingBlock) {
            Chunk chunk = entity.getLocation().getChunk();
            int fallingCount = 0;
            for (Entity e : chunk.getEntities()) {
                if (e instanceof FallingBlock) {
                    fallingCount++;
                    if (fallingCount > maxFallingBlocksPerChunk) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (!enabled) return;
        long tick = plugin.getServer().getCurrentTick();
        if (tick != currentTick) {
            currentTick = tick;
            explosionsThisTick = 0;
        }

        explosionsThisTick++;
        if (explosionsThisTick > maxExplosionsPerTick) {
            // Cancelar daño a bloques de explosiones excesivas en el mismo tick para mantener 20 TPS
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!enabled || !optimizeExplosions) return;

        // Limitar la lista de bloques destruidos si es una explosión colosal
        if (event.blockList().size() > maxExplosionBlocks) {
            while (event.blockList().size() > maxExplosionBlocks) {
                event.blockList().remove(event.blockList().size() - 1);
            }
        }
    }
}
