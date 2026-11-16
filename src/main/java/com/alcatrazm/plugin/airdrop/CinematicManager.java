package com.alcatrazm.plugin.airdrop;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Gestiona sesiones cinematicas de espectador durante la caida de un AirDrop con recuperacion atomica en disco.
 */
public final class CinematicManager implements Listener {
    private final AirDropModule module;
    private final File recoveryFile;
    private final YamlConfiguration recoveryConfig;
    private final NamespacedKey disabledKey;
    private final Map<UUID, CinematicSession> activeSessions = new HashMap<>();
    private final Map<UUID, Long> lastCombatTicks = new HashMap<>();
    private final Set<UUID> restoringPlayers = new HashSet<>();

    public CinematicManager(AirDropModule module) {
        this.module = module;
        this.disabledKey = new NamespacedKey(module.getPlugin(), "airdrop-cinematic-disabled");
        this.recoveryFile = new File(module.getPlugin().getDataFolder(), "airdrop_recovery.yml");
        this.recoveryConfig = YamlConfiguration.loadConfiguration(this.recoveryFile);
    }

    public boolean isInCinematic(UUID uuid) {
        return this.activeSessions.containsKey(uuid);
    }

    public boolean isEnabled() {
        return this.module.getBoolean("cinematic.enabled", true);
    }

    public boolean toggleCinematicForPlayer(Player player) {
        boolean disabled = player.getPersistentDataContainer().has(this.disabledKey, PersistentDataType.BYTE);
        if (disabled) {
            player.getPersistentDataContainer().remove(this.disabledKey);
        } else {
            player.getPersistentDataContainer().set(this.disabledKey, PersistentDataType.BYTE, (byte) 1);
            this.stopCinematic(player.getUniqueId());
        }
        return disabled;
    }

    public void triggerCinematic(DropInstance drop) {
        if (!drop.removed && this.isEnabled() && drop.location.getWorld() != null && !drop.location.getWorld().getPlayers().isEmpty()) {
            World world = drop.location.getWorld();
            int cx = drop.location.getBlockX() >> 4;
            int cz = drop.location.getBlockZ() >> 4;
            List<CompletableFuture<?>> futures = new ArrayList<>();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    futures.add(world.getChunkAtAsync(cx + dx, cz + dz, true));
                }
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((res, err) -> {
                if (this.module.isActive() && !this.module.isDisabling() && err == null) {
                    Bukkit.getScheduler().runTask(this.module.getPlugin(), () -> {
                        if (drop.state == DropInstance.State.FALLING && !drop.removed && this.isEnabled()) {
                            double maxRadius = this.module.getDouble("cinematic.trigger-radius-blocks", 0.0, 0.0, 100000.0);
                            double maxRadiusSq = maxRadius * maxRadius;
                            for (Player p : world.getPlayers()) {
                                if (maxRadius > 0.0 && p.getLocation().distanceSquared(drop.location) > maxRadiusSq) {
                                    continue;
                                }
                                this.startCinematic(p, drop);
                            }
                        }
                    });
                }
            });
        }
    }

    private void startCinematic(Player player, DropInstance drop) {
        if (this.isInCinematic(player.getUniqueId()) || player.isDead() || player.isInsideVehicle() || player.isSleeping() || player.isGliding() || player.getSpectatorTarget() != null) {
            return;
        }
        if (player.getPersistentDataContainer().has(this.disabledKey, PersistentDataType.BYTE)) {
            return;
        }

        long graceTicks = this.module.getInt("cinematic.combat-grace-seconds", 10, 0, 120) * 20L;
        if (this.module.getTicks() - this.lastCombatTicks.getOrDefault(player.getUniqueId(), -10000L) < graceTicks) {
            return;
        }

        String path = "players." + player.getUniqueId();
        if (this.recoveryConfig.contains(path)) {
            this.restorePlayer(player);
            return;
        }

        Location playerLoc = player.getLocation();
        this.recoveryConfig.set(path + ".world", playerLoc.getWorld().getUID().toString());
        this.recoveryConfig.set(path + ".world-name", playerLoc.getWorld().getName());
        this.recoveryConfig.set(path + ".x", playerLoc.getX());
        this.recoveryConfig.set(path + ".y", playerLoc.getY());
        this.recoveryConfig.set(path + ".z", playerLoc.getZ());
        this.recoveryConfig.set(path + ".yaw", playerLoc.getYaw());
        this.recoveryConfig.set(path + ".pitch", playerLoc.getPitch());
        this.recoveryConfig.set(path + ".mode", player.getGameMode().name());
        this.recoveryConfig.set(path + ".flight", player.getAllowFlight());
        this.recoveryConfig.set(path + ".flying", player.isFlying());

        if (!this.saveRecovery()) {
            this.recoveryConfig.set(path, null);
            return;
        }

        player.closeInventory();

        double orbitRadius = this.module.getDouble("cinematic.orbit-radius", 12.0, 4.0, 16.0);
        Location camLoc = this.computeInitialCameraLoc(drop, orbitRadius);
        BlockDisplay camEntity = camLoc.getWorld().spawn(camLoc, BlockDisplay.class, e -> {
            e.setBlock(Bukkit.createBlockData(Material.AIR));
            e.setGravity(false);
            e.setInvulnerable(true);
            e.setSilent(true);
            e.setPersistent(false);
            e.setTeleportDuration(3);
            e.setViewRange(2.0F);
            e.getPersistentDataContainer().set(this.module.getAirDropManager().getKey(), PersistentDataType.STRING, drop.id);
        });

        long leadTicks = 24L;
        long fallRemaining = Math.max(0L, drop.stateStartTick + drop.fallDurationTicks - this.module.getTicks());
        long maxCinematicTicks = Math.max(this.module.getInt("cinematic.seconds", 7, 2, 20) * 20L, fallRemaining + leadTicks);

        CinematicSession session = new CinematicSession(player.getUniqueId(), drop, camEntity, this.module.getTicks(), this.module.getTicks() + maxCinematicTicks, orbitRadius);
        this.activeSessions.put(player.getUniqueId(), session);
        drop.entities.add(camEntity);
        this.restoringPlayers.add(player.getUniqueId());

        try {
            player.setGameMode(GameMode.SPECTATOR);
            if (player.getGameMode() == GameMode.SPECTATOR && player.teleport(camLoc)) {
                this.module.getMessageFormatter().sendMessage(player, this.module.getMessageFormatter().detail(MessageFormatter.toSmallCaps("Cinematica | /uad omitir para volver | /uad cine para desactivar")));
                return;
            }
            this.stopCinematic(player.getUniqueId());
        } catch (RuntimeException e) {
            this.module.getPlugin().getLogger().warning("[AirDrop] Se omite la cinematica de " + player.getName() + ": " + e.getMessage());
            this.stopCinematic(player.getUniqueId());
        } finally {
            this.restoringPlayers.remove(player.getUniqueId());
        }
    }

    private Location getDropCurrentCenter(DropInstance drop) {
        Location loc = drop.location.clone().add(0.0, 0.4, 0.0);
        if (drop.state == DropInstance.State.FALLING) {
            double progress = Math.clamp((double) (this.module.getTicks() - drop.stateStartTick) / drop.fallDurationTicks, 0.0, 1.0);
            double fallY = Math.min(loc.getWorld().getMaxHeight() - 4, drop.location.getY() + this.module.getInt("drop.fall-height", 48, 8, 150));
            loc.setY(fallY + (drop.location.getY() - fallY) * (1.0 - Math.pow(1.0 - progress, 2.0)) + 0.4);
        }
        return loc;
    }

    private Location computeInitialCameraLoc(DropInstance drop, double radius) {
        Location center = this.getDropCurrentCenter(drop);
        Location cam = center.clone().add(Math.cos(0.4) * radius, 6.0, Math.sin(0.4) * radius);
        cam.setY(Math.min(cam.getWorld().getMaxHeight() - 2, cam.getY()));
        cam.setDirection(center.toVector().subtract(cam.toVector()));
        return cam;
    }

    private Location computeOrbitLocation(CinematicSession s, long tick) {
        double p = Math.clamp((double) (tick - s.orbitStartTick) / s.orbitDurationTicks, 0.0, 1.0);
        double smooth = p * p * p * (p * (p * 6.0 - 15.0) + 10.0);
        double angle = 0.4 + smooth * 1.4;
        Location target = this.getDropCurrentCenter(s.drop);
        Location cam = s.drop.location.clone();
        double targetY = s.drop.location.getY() + 6.4;
        cam.add(Math.cos(angle) * s.orbitRadius, 0.0, Math.sin(angle) * s.orbitRadius);
        cam.setY(Math.min(cam.getWorld().getMaxHeight() - 2, s.startY + (targetY - s.startY) * smooth + Math.sin(smooth * Math.PI) * 1.5));
        cam.setDirection(target.toVector().subtract(cam.toVector()));

        float yawDelta = (cam.getYaw() - s.currentYaw + 540.0F) % 360.0F - 180.0F;
        s.currentYaw += yawDelta * 0.22F;
        s.currentPitch += (cam.getPitch() - s.currentPitch) * 0.22F;
        cam.setYaw(s.currentYaw);
        cam.setPitch(s.currentPitch);
        return cam;
    }

    public void onDropLanded(DropInstance drop) {
        for (CinematicSession s : this.activeSessions.values()) {
            if (s.drop == drop) {
                s.endTick = Math.max(s.endTick, this.module.getTicks() + 24L);
            }
        }
        if (drop.location.getWorld() != null) {
            for (Player p : drop.location.getWorld().getPlayers()) {
                if (this.isInCinematic(p.getUniqueId()) || p.getLocation().distanceSquared(drop.location) <= 1024.0) {
                    this.module.getMessageFormatter().playSound(p, Sound.ENTITY_GENERIC_EXPLODE, 0.45F, 0.75F);
                }
            }
        }
    }

    public void tick(long tick) {
        for (CinematicSession s : new ArrayList<>(this.activeSessions.values())) {
            Player p = Bukkit.getPlayer(s.playerId);
            if (p != null && p.isOnline() && tick < s.endTick && s.drop.state != DropInstance.State.REMOVED && s.cameraEntity.isValid()) {
                if (p.getGameMode() != GameMode.SPECTATOR) {
                    this.stopCinematic(s.playerId);
                } else if (!s.spectating) {
                    if (tick - s.startTick >= 3L && s.cameraEntity.getTrackedBy().contains(p)) {
                        this.restoringPlayers.add(s.playerId);
                        try {
                            p.setSpectatorTarget(s.cameraEntity);
                            s.spectating = p.getSpectatorTarget() == s.cameraEntity;
                        } finally {
                            this.restoringPlayers.remove(s.playerId);
                        }
                        if (s.spectating) {
                            s.orbitStartTick = tick;
                            s.orbitDurationTicks = Math.max(20L, s.drop.stateStartTick + s.drop.fallDurationTicks - tick);
                        }
                    }
                } else {
                    Location nextLoc = this.computeOrbitLocation(s, tick);
                    if ((tick - s.orbitStartTick) % 2L == 0L) {
                        s.cameraEntity.teleport(nextLoc);
                    }
                }
            } else {
                this.stopCinematic(s.playerId);
            }
        }
    }

    public void cancelCinematicForDrop(String dropId) {
        for (CinematicSession s : new ArrayList<>(this.activeSessions.values())) {
            if (s.drop.id.equals(dropId)) {
                this.stopCinematic(s.playerId);
            }
        }
    }

    public void stopCinematic(UUID uuid) {
        CinematicSession s = this.activeSessions.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && s != null) {
            this.restorePlayer(p);
        }
        if (s != null && s.cameraEntity.isValid()) {
            s.cameraEntity.remove();
        }
    }

    public void restorePlayer(Player player) {
        String path = "players." + player.getUniqueId();
        if (this.recoveryConfig.contains(path)) {
            World w = null;
            try {
                w = Bukkit.getWorld(UUID.fromString(this.recoveryConfig.getString(path + ".world", "")));
            } catch (IllegalArgumentException ignored) {}

            if (w == null) {
                w = Bukkit.getWorld(this.recoveryConfig.getString(path + ".world-name", "world"));
            }

            if (w == null) {
                this.module.getPlugin().getLogger().warning("[AirDrop] No se encuentra el mundo original de " + player.getName() + "; se conserva airdrop_recovery.yml");
            } else {
                Location loc = new Location(
                    w,
                    this.recoveryConfig.getDouble(path + ".x"),
                    this.recoveryConfig.getDouble(path + ".y"),
                    this.recoveryConfig.getDouble(path + ".z"),
                    (float) this.recoveryConfig.getDouble(path + ".yaw"),
                    (float) this.recoveryConfig.getDouble(path + ".pitch")
                );

                GameMode mode;
                try {
                    mode = GameMode.valueOf(this.recoveryConfig.getString(path + ".mode", "SURVIVAL"));
                } catch (IllegalArgumentException e) {
                    mode = GameMode.SURVIVAL;
                }

                this.restoringPlayers.add(player.getUniqueId());
                try {
                    if (player.getGameMode() == GameMode.SPECTATOR) {
                        player.setSpectatorTarget(null);
                    }
                    if (player.teleport(loc)) {
                        player.setGameMode(mode);
                        player.setAllowFlight(this.recoveryConfig.getBoolean(path + ".flight"));
                        player.setFlying(player.getAllowFlight() && this.recoveryConfig.getBoolean(path + ".flying"));
                        player.setVelocity(new Vector());
                        player.setFallDistance(0.0F);
                        this.recoveryConfig.set(path, null);
                        this.saveRecovery();
                        return;
                    }
                    this.module.getPlugin().getLogger().warning("[AirDrop] La restauracion de " + player.getName() + " fue cancelada; se conserva airdrop_recovery.yml");
                } finally {
                    this.restoringPlayers.remove(player.getUniqueId());
                }
            }
        }
    }

    public boolean saveRecovery() {
        Path target = this.recoveryFile.toPath();
        Path tmp = target.resolveSibling("airdrop_recovery.yml.tmp");
        try {
            Files.writeString(tmp, this.recoveryConfig.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            this.module.getPlugin().getLogger().severe("[AirDrop] No se pudo guardar airdrop_recovery.yml: " + e.getMessage());
            return false;
        }
    }

    public void stopAll() {
        for (UUID uuid : new ArrayList<>(this.activeSessions.keySet())) {
            this.stopCinematic(uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        if (this.isInCinematic(event.getPlayer().getUniqueId()) && !this.restoringPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        if (this.isInCinematic(event.getPlayer().getUniqueId()) && !this.restoringPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            if (this.isInCinematic(p.getUniqueId())) {
                event.setCancelled(true);
            } else if (!event.isCancelled()) {
                this.lastCombatTicks.put(p.getUniqueId(), this.module.getTicks());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (this.isInCinematic(event.getPlayer().getUniqueId()) && !event.getMessage().toLowerCase(Locale.ROOT).matches("/(uad|ud|universairdrop|universairdrops|airdropfree|airdrop) (omitir|cine)( .*)?")) {
            this.stopCinematic(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.stopCinematic(event.getPlayer().getUniqueId());
        this.lastCombatTicks.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this.module.getPlugin(), () -> this.restorePlayer(event.getPlayer()));
    }

    static final class CinematicSession {
        final UUID playerId;
        final DropInstance drop;
        final BlockDisplay cameraEntity;
        final long startTick;
        final double orbitRadius;
        final double startY;
        float currentYaw;
        float currentPitch;
        long endTick;
        long orbitStartTick = -1L;
        long orbitDurationTicks;
        boolean spectating;

        CinematicSession(UUID playerId, DropInstance drop, BlockDisplay cameraEntity, long startTick, long endTick, double orbitRadius) {
            this.playerId = playerId;
            this.drop = drop;
            this.cameraEntity = cameraEntity;
            this.startTick = startTick;
            this.endTick = endTick;
            this.orbitRadius = orbitRadius;
            Location loc = cameraEntity.getLocation();
            this.startY = loc.getY();
            this.currentYaw = loc.getYaw();
            this.currentPitch = loc.getPitch();
        }
    }
}
