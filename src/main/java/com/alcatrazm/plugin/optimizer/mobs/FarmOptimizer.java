package com.alcatrazm.plugin.optimizer.mobs;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Optimizador de Granjas, Mobs Hacinados y Colisiones Físicas.
 * Elimina la carga de CPU de búsqueda de caminos (AI) y colisiones (cramming) en granjas masivas.
 */
public class FarmOptimizer implements Listener {

    private final AlcatrazPlugin plugin;
    private final Set<EntityType> affectedMobTypes = new HashSet<>();
    private BukkitTask optimizerTask;

    public FarmOptimizer(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
        startTask();
    }

    public void loadConfiguration() {
        affectedMobTypes.clear();
        List<String> types = plugin.getConfig().getStringList("optimizer.farm-control.affected-mobs");
        if (types.isEmpty()) {
            affectedMobTypes.addAll(Arrays.asList(
                    EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN,
                    EntityType.ZOMBIE, EntityType.SKELETON,
                    EntityType.ENDERMAN, EntityType.ZOMBIFIED_PIGLIN, EntityType.IRON_GOLEM
            ));
        } else {
            for (String t : types) {
                try {
                    EntityType et = EntityType.valueOf(t.toUpperCase());
                    if (et != EntityType.VILLAGER && et != EntityType.TURTLE) {
                        affectedMobTypes.add(et);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void reload() {
        stopTask();
        loadConfiguration();
        startTask();
    }

    private void startTask() {
        if (!plugin.getConfig().getBoolean("optimizer.farm-control.enabled", true) &&
            !plugin.getConfig().getBoolean("optimizer.collisions.enabled", true)) {
            return;
        }

        int interval = plugin.getConfig().getInt("optimizer.farm-control.check-interval-ticks", 60);
        optimizerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::optimizeCrowdedMobs, interval, interval);
    }

    public void stopTask() {
        if (optimizerTask != null && !optimizerTask.isCancelled()) {
            optimizerTask.cancel();
            optimizerTask = null;
        }
    }

    private void optimizeCrowdedMobs() {
        boolean farmControlEnabled = plugin.getConfig().getBoolean("optimizer.farm-control.enabled", true);
        boolean collisionOptimizerEnabled = plugin.getConfig().getBoolean("optimizer.collisions.enabled", true);
        int crowdThreshold = plugin.getConfig().getInt("optimizer.farm-control.crowd-threshold", 6);
        double crowdRadius = plugin.getConfig().getDouble("optimizer.farm-control.crowd-radius", 2.5);
        double radiusSq = crowdRadius * crowdRadius;

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                // Agrupar por tipo los mobs elegibles del chunk
                Map<EntityType, List<LivingEntity>> byType = new HashMap<>();
                for (Entity e : chunk.getEntities()) {
                    if (e instanceof LivingEntity living && affectedMobTypes.contains(e.getType())) {
                        if (living.isValid() && !living.isDead() && !(living instanceof Player)) {
                            byType.computeIfAbsent(e.getType(), k -> new ArrayList<>()).add(living);
                        }
                    }
                }

                for (List<LivingEntity> list : byType.values()) {
                    boolean groupCrowded = list.size() >= crowdThreshold;
                    for (LivingEntity mob : list) {
                        boolean crowded = false;
                        if (groupCrowded && isOptimizable(mob)) {
                            int nearbySame = 0;
                            for (LivingEntity other : list) {
                                if (other != mob && mob.getLocation().distanceSquared(other.getLocation()) <= radiusSq) {
                                    if (++nearbySame >= crowdThreshold - 1) break;
                                }
                            }
                            crowded = nearbySame >= crowdThreshold - 1;
                        }

                        if (crowded) {
                            // Hacinado: Desactivar AI y colisiones
                            if (farmControlEnabled && mob instanceof Mob m) m.setAware(false);
                            if (collisionOptimizerEnabled) mob.setCollidable(false);
                        } else {
                            // No hacinado (o grupo reducido): restaurar SIEMPRE para evitar mobs congelados
                            if (mob instanceof Mob m && !m.isAware()) m.setAware(true);
                            if (!mob.isCollidable()) mob.setCollidable(true);
                        }
                    }
                }
            }
        }
    }

    /**
     * Mobs que nunca deben congelarse: con nombre, NPCs, mobs de plugins (MythicMobs) o en combate.
     */
    private boolean isOptimizable(LivingEntity mob) {
        if (mob.getCustomName() != null || mob.hasMetadata("NPC")) return false;
        for (org.bukkit.NamespacedKey key : mob.getPersistentDataContainer().getKeys()) {
            if (key.getNamespace().equals("mythicmobs")) return false;
        }
        return !(mob instanceof Mob m) || m.getTarget() == null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("optimizer.chunk-hard-cap.enabled", true)) return;

        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;

        // Tortugas, huevos de tortuga y aldeanos SIEMPRE permitidos al 100% natural
        if (entity.getType() == EntityType.TURTLE || entity.getType() == EntityType.VILLAGER) return;
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.EGG
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.BREEDING
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.DISPENSE_EGG) {
            return;
        }

        int maxAnimals = plugin.getConfig().getInt("optimizer.chunk-hard-cap.max-animals-per-chunk", 25);
        int maxMonsters = plugin.getConfig().getInt("optimizer.chunk-hard-cap.max-monsters-per-chunk", 40);

        Chunk chunk = entity.getLocation().getChunk();
        int animalCount = 0;
        int monsterCount = 0;

        for (Entity e : chunk.getEntities()) {
            if (e instanceof Animals) animalCount++;
            else if (e instanceof Monster) monsterCount++;
        }

        if (entity instanceof Animals && animalCount >= maxAnimals) {
            event.setCancelled(true);
        } else if (entity instanceof Monster && monsterCount >= maxMonsters) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        // Restaurar awareness inmediatamente si el jugador interactúa con el mob (tradeo, crianza, etc.)
        if (event.getRightClicked() instanceof Mob mob) {
            if (!mob.isAware()) {
                mob.setAware(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        // Restaurar awareness si el mob es golpeado
        if (event.getEntity() instanceof Mob mob) {
            if (!mob.isAware()) {
                mob.setAware(true);
            }
        }
    }
}
