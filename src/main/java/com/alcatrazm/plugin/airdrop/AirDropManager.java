package com.alcatrazm.plugin.airdrop;

import com.alcatrazm.plugin.protection.GriefPreventionHook;
import com.alcatrazm.plugin.protection.ProtectionManager;
import com.alcatrazm.plugin.protection.WorldGuardHook;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;

/**
 * Orquesta lanzamientos manuales y automaticos de AirDrops con validacion de terreno, protecciones y ciclo de vida.
 */
public final class AirDropManager {
    private final AirDropModule module;
    private final Map<String, DropInstance> activeDrops = new LinkedHashMap<>();
    private final NamespacedKey dropKey;
    private final Map<String, Integer> chunkTickets = new HashMap<>();
    private final File stateFile;
    private int pendingSpawns = 0;
    private long dropCounter = 1L;
    private long launchEpoch = 0L;
    private long nextAutoDropTick = -1L;
    private long lastConfiguredAutoIntervalTicks = -1L;

    public AirDropManager(AirDropModule module) {
        this.module = module;
        this.dropKey = new NamespacedKey(module.getPlugin(), "airdrop-entity");
        this.stateFile = new File(module.getPlugin().getDataFolder(), "airdrop_state.yml");
        this.loadState();
        this.resetAutoSchedule(module.getTicks());
    }

    private void loadState() {
        if (this.stateFile.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.stateFile);
            this.dropCounter = Math.max(1L, cfg.getLong("last-id", 1L));
        } else {
            this.dropCounter = Math.max(1L, this.module.getPlugin().getConfig().getLong("airdrop.drop.last-id", 1L));
        }
    }

    private void saveState() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("last-id", this.dropCounter);
        try {
            cfg.save(this.stateFile);
        } catch (IOException e) {
            this.module.getPlugin().getLogger().warning("[AirDrop] No se pudo guardar airdrop_state.yml: " + e.getMessage());
        }
    }

    public NamespacedKey getKey() {
        return this.dropKey;
    }

    public Collection<DropInstance> getActiveDrops() {
        return List.copyOf(this.activeDrops.values());
    }

    public int getActiveAndPendingCount() {
        return this.activeDrops.size() + this.pendingSpawns;
    }

    public DropInstance getDrop(String id) {
        return this.activeDrops.get(id.replace("#", ""));
    }

    public DropInstance findByEntity(Entity entity) {
        String id = entity.getPersistentDataContainer().get(this.dropKey, PersistentDataType.STRING);
        return id == null ? null : this.activeDrops.get(id);
    }

    public void resetAutoSchedule(long currentTick) {
        long intervalTicks = this.getAutoIntervalTicks();
        this.lastConfiguredAutoIntervalTicks = intervalTicks;
        if (this.module.getBoolean("automatic.enabled", false)) {
            this.nextAutoDropTick = currentTick + intervalTicks;
        } else {
            this.nextAutoDropTick = -1L;
        }
    }

    private long getAutoIntervalTicks() {
        return this.module.getInt("automatic.interval-minutes", 60, 1, 1440) * 1200L;
    }

    public long getSecondsUntilNextAutoDrop(long currentTick) {
        if (!this.module.getBoolean("automatic.enabled", false) || this.nextAutoDropTick < 0L) {
            return 0L;
        }
        long remainingTicks = Math.max(0L, this.nextAutoDropTick - currentTick);
        return (remainingTicks + 19L) / 20L;
    }

    public List<DropTier> getTiers() {
        return DropTier.loadTiers(this.module);
    }

    public boolean isDimensionAllowed(World.Environment env) {
        return switch (env) {
            case NORMAL -> this.module.getBoolean("location.dimensions.overworld", true);
            case NETHER -> this.module.getBoolean("location.dimensions.nether", false);
            case THE_END -> this.module.getBoolean("location.dimensions.end", false);
            default -> false;
        };
    }

    public void launch(String mode, CommandSender sender) {
        List<DropTier> tiers = this.getTiers();
        DropTier selectedTier;
        if ("LIBRE".equalsIgnoreCase(mode) || "ALEATORIO".equalsIgnoreCase(mode)) {
            List<DropTier> eligible = tiers.stream()
                .filter(t -> t.enabled() && this.module.getLootManager().getItemCount(t.id()) > 0)
                .toList();
            if (eligible.isEmpty()) {
                this.sendFeedback(sender, "No hay rarezas activas con objetos en airdrop_loot.yml");
                return;
            }
            selectedTier = DropTier.pickWeighted(eligible, this.module.getRandom());
        } else {
            selectedTier = DropTier.resolveTier(tiers, mode);
            if (selectedTier == null) {
                this.sendFeedback(sender, "Tipo de drop no disponible: " + mode + " (usa aleatorio, comun, epico o legendario)");
                return;
            }
            if (!selectedTier.enabled()) {
                this.sendFeedback(sender, "El tipo '" + selectedTier.displayName() + "' esta desactivado en config.yml");
                return;
            }
            if (this.module.getLootManager().getItemCount(selectedTier.id()) == 0) {
                this.sendFeedback(sender, "No hay objetos configurados para '" + selectedTier.displayName() + "' en airdrop_loot.yml");
                return;
            }
        }

        int maxActive = this.module.getInt("drop.max-active", 3, 1, 20);
        if (this.getActiveAndPendingCount() >= maxActive) {
            this.sendFeedback(sender, "Se alcanzo el maximo de drops activos (" + maxActive + ")");
            return;
        }

        String worldName = this.module.getString("location.world", "world");
        List<String> disabledWorlds = this.module.getStringList("location.disabled-worlds");
        if (disabledWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(worldName))) {
            this.sendFeedback(sender, "El mundo '" + worldName + "' esta bloqueado para AirDrops");
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            this.sendFeedback(sender, "Mundo no encontrado: " + worldName);
            return;
        }
        if (!this.isDimensionAllowed(world.getEnvironment())) {
            this.sendFeedback(sender, "La dimension '" + world.getEnvironment().name() + "' esta bloqueada para AirDrops en config.yml");
            return;
        }

        this.pendingSpawns++;
        if (sender != null) {
            this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().success("Buscando ubicacion segura para " + selectedTier.displayName() + "..."));
        }

        this.findAndSpawn(world, selectedTier, sender, 0, this.launchEpoch);
    }

    private void sendFeedback(CommandSender sender, String text) {
        if (sender != null) {
            this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().error(text));
        } else {
            this.module.getPlugin().getLogger().warning("[AirDrop] " + text);
        }
    }

    private void findAndSpawn(World world, DropTier tier, CommandSender sender, int attempt, long epoch) {
        if (!this.module.isActive() || this.module.isDisabling() || epoch != this.launchEpoch) {
            return;
        }

        int maxAttempts = this.module.getInt("location.attempts", 80, 1, 500);
        if (attempt >= maxAttempts) {
            this.pendingSpawns = Math.max(0, this.pendingSpawns - 1);
            this.sendFeedback(sender, "No se encontro una ubicacion valida tras " + maxAttempts + " intentos");
            return;
        }

        double radius = this.module.getInt("location.radius", 3000, 16, 30000000);
        DropMath.Coords coords = DropMath.generateRandomCoords(radius, this.module.getRandom());

        if (Math.floorMod(coords.x(), 16) >= 2 && Math.floorMod(coords.x(), 16) <= 13 &&
            Math.floorMod(coords.z(), 16) >= 2 && Math.floorMod(coords.z(), 16) <= 13) {

            Location targetLoc = new Location(world, coords.x() + 0.5, world.getSpawnLocation().getY(), coords.z() + 0.5);
            if (!world.getWorldBorder().isInside(targetLoc)) {
                Bukkit.getScheduler().runTask(this.module.getPlugin(), () -> this.findAndSpawn(world, tier, sender, attempt + 1, epoch));
                return;
            }

            world.getChunkAtAsync(coords.x() >> 4, coords.z() >> 4, true).whenComplete((chunk, err) -> {
                if (this.module.isActive() && !this.module.isDisabling()) {
                    Bukkit.getScheduler().runTask(this.module.getPlugin(), () -> {
                        if (!this.module.isActive() || this.module.isDisabling() || epoch != this.launchEpoch) return;

                        Location surfaceLoc = (err == null) ? this.validateGround(world, coords.x(), coords.z()) : null;
                        if (surfaceLoc == null) {
                            this.findAndSpawn(world, tier, sender, attempt + 1, epoch);
                        } else {
                            this.pendingSpawns = Math.max(0, this.pendingSpawns - 1);
                            List<org.bukkit.inventory.ItemStack> loot = this.module.getLootManager().rollLoot(tier);
                            if (loot.isEmpty()) {
                                this.sendFeedback(sender, "No hay loot disponible para " + tier.displayName());
                                return;
                            }

                            String dropId = String.format(Locale.ROOT, "%03d", this.dropCounter++);
                            this.saveState();

                            DropInstance drop = new DropInstance(this.module, dropId, tier, DropMath.DropType.LIBRE, surfaceLoc, loot, this.module.getTicks());
                            this.activeDrops.put(dropId, drop);
                            this.addChunkTicket(drop);

                            if (drop.countdownDurationTicks <= 0L) {
                                if (drop.fallDurationTicks <= 0L) {
                                    this.landDrop(drop, this.module.getTicks());
                                } else {
                                    this.startFalling(drop, this.module.getTicks());
                                }
                            } else {
                                Component bc = this.module.getMessageFormatter().gradient(drop.displayName + " #" + dropId + " ", tier.colorStart(), tier.colorEnd())
                                    .append(this.module.getMessageFormatter().detail("| "))
                                    .append(this.module.getMessageFormatter().coords(surfaceLoc))
                                    .append(this.module.getMessageFormatter().detail(" | " + world.getName()));
                                this.module.getMessageFormatter().broadcast(bc);
                            }

                            if (sender != null) {
                                this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().success(drop.displayName + " #" + dropId + " iniciado con exito"));
                            }
                        }
                    });
                }
            });
        } else {
            Bukkit.getScheduler().runTask(this.module.getPlugin(), () -> this.findAndSpawn(world, tier, sender, attempt + 1, epoch));
        }
    }

    private Location validateGround(World world, int x, int z) {
        int highestY = -1;
        if (world.getEnvironment() == World.Environment.NETHER) {
            for (int y = 115; y >= 32; y--) {
                Block ground = world.getBlockAt(x, y, z);
                if (ground.getType().isSolid() && !ground.isLiquid() && world.getBlockAt(x, y + 1, z).isPassable() && world.getBlockAt(x, y + 2, z).isPassable()) {
                    highestY = y;
                    break;
                }
            }
            if (highestY < 32) {
                return null;
            }
        } else {
            highestY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        }
        if (highestY <= world.getMinHeight() || highestY + 5 >= world.getMaxHeight()) {
            return null;
        }

        Block block = world.getBlockAt(x, highestY, z);
        Material mat = block.getType();
        if (!mat.isSolid() || block.isLiquid() || Set.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.POWDER_SNOW, Material.CAMPFIRE, Material.SOUL_CAMPFIRE).contains(mat)) {
            return null;
        }

        if (this.module.getBoolean("location.avoid-leaves", true) && Tag.LEAVES.isTagged(mat)) {
            return null;
        }

        if (!world.getBlockAt(x, highestY + 1, z).isPassable() || !world.getBlockAt(x, highestY + 2, z).isPassable()) {
            return null;
        }

        int maxSlope = this.module.getInt("location.maximum-slope", 4, 0, 30);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int neighborY = world.getHighestBlockYAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING);
                if (Math.abs(neighborY - highestY) > maxSlope) {
                    return null;
                }
                Material neighborMat = world.getBlockAt(x + dx, neighborY, z + dz).getType();
                if (neighborMat == Material.LAVA || neighborMat == Material.MAGMA_BLOCK || (this.module.getBoolean("location.avoid-water", true) && neighborMat == Material.WATER)) {
                    return null;
                }
            }
        }

        double yOffset = this.module.getDouble("presentation.chest-bob-height", 0.0, 0.0, 0.5) > 0.0 ? 1.25 : 1.0;
        Location loc = new Location(world, x + 0.5, highestY + yOffset, z + 0.5);
        double radius = this.module.getInt("location.radius", 3000, 16, 30000000);
        if (loc.getX() * loc.getX() + loc.getZ() * loc.getZ() > radius * radius) {
            return null;
        }

        if (!world.getWorldBorder().isInside(loc)) {
            return null;
        }

        if (this.module.getBoolean("location.avoid-protected-regions", true) && this.isInsideProtectedArea(loc)) {
            return null;
        }

        boolean tooClose = this.activeDrops.values().stream().anyMatch(d -> d.location.getWorld() == world && d.location.distanceSquared(loc) < 100.0);
        return tooClose ? null : loc;
    }

    private boolean isInsideProtectedArea(Location loc) {
        ProtectionManager pm = this.module.getPlugin().getProtectionManager();
        if (pm != null) {
            if (pm.isEnabled() && pm.getClaimAt(loc) != null) {
                return true;
            }
            GriefPreventionHook gp = pm.getGriefPreventionHook();
            if (gp != null && gp.isGriefPreventionPresent() && gp.getOverlappingClaim(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(), 2) != null) {
                return true;
            }
            WorldGuardHook wg = pm.getWorldGuardHook();
            if (wg != null && wg.isWorldGuardPresent() && wg.hasApplicableRegions(loc)) {
                return true;
            }
        }
        return false;
    }

    private String chunkKey(DropInstance drop) {
        return drop.location.getWorld().getUID() + ":" + (drop.location.getBlockX() >> 4) + ":" + (drop.location.getBlockZ() >> 4);
    }

    private void addChunkTicket(DropInstance drop) {
        String key = this.chunkKey(drop);
        int count = this.chunkTickets.merge(key, 1, Integer::sum);
        if (count == 1) {
            drop.location.getWorld().addPluginChunkTicket(drop.location.getBlockX() >> 4, drop.location.getBlockZ() >> 4, this.module.getPlugin());
        }
    }

    private void removeChunkTicket(DropInstance drop) {
        String key = this.chunkKey(drop);
        int count = this.chunkTickets.getOrDefault(key, 1) - 1;
        if (count <= 0) {
            this.chunkTickets.remove(key);
            drop.location.getWorld().removePluginChunkTicket(drop.location.getBlockX() >> 4, drop.location.getBlockZ() >> 4, this.module.getPlugin());
        } else {
            this.chunkTickets.put(key, count);
        }
    }

    private void tickAutoScheduler(long currentTick) {
        boolean autoEnabled = this.module.getBoolean("automatic.enabled", false);
        long intervalTicks = this.getAutoIntervalTicks();

        if (!autoEnabled) {
            this.nextAutoDropTick = -1L;
            this.lastConfiguredAutoIntervalTicks = intervalTicks;
            return;
        }

        if (this.nextAutoDropTick < 0L || intervalTicks != this.lastConfiguredAutoIntervalTicks) {
            this.lastConfiguredAutoIntervalTicks = intervalTicks;
            this.nextAutoDropTick = currentTick + intervalTicks;
            return;
        }

        if (currentTick >= this.nextAutoDropTick) {
            this.nextAutoDropTick = currentTick + intervalTicks;
            boolean requireOnline = this.module.getBoolean("automatic.require-online-players", true);
            if (requireOnline && Bukkit.getOnlinePlayers().isEmpty()) {
                return;
            }
            String mode = this.module.getString("automatic.mode", "ALEATORIO");
            this.launch(mode, null);
        }
    }

    public void tick(long currentTick) {
        this.tickAutoScheduler(currentTick);

        for (DropInstance drop : this.getActiveDrops()) {
            if (drop.state == DropInstance.State.COUNTDOWN) {
                long remainingTicks = drop.countdownDurationTicks - (currentTick - drop.creationTick);
                int remainingSeconds = (int) Math.max(0L, (remainingTicks + 19L) / 20L);

                if (remainingSeconds != drop.remainingLootSeconds && remainingSeconds > 0) {
                    drop.remainingLootSeconds = remainingSeconds;
                    for (Player p : drop.location.getWorld().getPlayers()) {
                        String titleMsg = this.module.getString("messages.countdown-title", "<name> EN <seconds>")
                            .replace("<seconds>", String.valueOf(remainingSeconds))
                            .replace("<name>", drop.displayName);
                        String subtitleMsg = this.module.getString("messages.countdown-subtitle", "ᴘʀᴇᴘᴀʀᴀᴛᴇ ᴘᴀʀᴀ ʟᴀ ᴄᴀɪᴅᴀ");
                        this.module.getMessageFormatter().showTitle(p, titleMsg, subtitleMsg);

                        if (remainingSeconds <= 10) {
                            this.module.getMessageFormatter().playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, remainingSeconds <= 3 ? 1.4F : 1.0F);
                        }
                    }
                }

                if (remainingTicks <= 0L) {
                    if (drop.fallDurationTicks <= 0L) {
                        this.landDrop(drop, currentTick);
                    } else {
                        this.startFalling(drop, currentTick);
                    }
                }
            } else if (drop.state == DropInstance.State.FALLING) {
                double progress = Math.clamp((double) (currentTick - drop.stateStartTick) / drop.fallDurationTicks, 0.0, 1.0);
                double fallHeight = Math.min(drop.location.getWorld().getMaxHeight() - 4, drop.location.getY() + this.module.getInt("drop.fall-height", 48, 8, 150));
                double eased = 1.0 - Math.pow(1.0 - progress, 2.0);

                Location currentLoc = drop.location.clone();
                currentLoc.setY(fallHeight + (drop.location.getY() - fallHeight) * eased);

                if (currentTick % 2L == 0L) {
                    this.updateEntityPositions(drop, currentLoc, currentTick, true);
                    this.module.getParticleRenderer().renderFallingTrail(currentLoc);
                }

                if (progress >= 1.0) {
                    this.landDrop(drop, currentTick);
                }
            } else if (drop.isInteractable()) {
                if (currentTick >= drop.stateStartTick) {
                    this.removeDrop(drop, "expirado");
                } else if (currentTick % 2L == 0L) {
                    double bobHeight = this.module.getDouble("presentation.chest-bob-height", 0.0, 0.0, 0.5);
                    if (bobHeight > 0.0 || this.module.getBoolean("presentation.particles", false)) {
                        double bob = Math.sin(currentTick * 0.08) * bobHeight;
                        Location bobLoc = drop.location.clone().add(0.0, bob, 0.0);
                        this.updateEntityPositions(drop, bobLoc, currentTick, false);
                    }
                }
            }
        }

        if (currentTick % 10L == 0L) {
            this.module.getDropListener().closeInvalidInventories();
        }
    }

    private <T extends Entity> T trackEntity(DropInstance drop, T entity) {
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.getPersistentDataContainer().set(this.dropKey, PersistentDataType.STRING, drop.id);
        drop.entities.add(entity);
        return entity;
    }

    private void ensureDisplayEntities(DropInstance drop, Location spawnLoc) {
        if (drop.blockDisplay == null || !drop.blockDisplay.isValid()) {
            drop.blockDisplay = this.trackEntity(drop, spawnLoc.getWorld().spawn(spawnLoc, BlockDisplay.class, e -> {
                e.setBlock(Bukkit.createBlockData(drop.tier.blockMaterial()));
                e.setBrightness(new Brightness(15, 15));
                e.setTransformationMatrix(new Matrix4f().translate(-0.5F, 0.0F, -0.5F));
                e.setTeleportDuration(2);
                e.setViewRange(2.0F);
                e.setShadowRadius(0.5F);
                e.setShadowStrength(0.25F);
            }));
        }

        if ((drop.textDisplay == null || !drop.textDisplay.isValid()) && this.module.getBoolean("presentation.holograms", true)) {
            drop.textDisplay = this.trackEntity(drop, spawnLoc.getWorld().spawn(spawnLoc.clone().add(0.0, 2.0, 0.0), TextDisplay.class, e -> {
                e.setBillboard(Billboard.CENTER);
                e.setDefaultBackground(false);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setShadowed(true);
                e.setSeeThrough(false);
                e.setTextOpacity((byte) -1);
                e.setLineWidth(350);
                e.setBrightness(new Brightness(15, 15));
                e.setTeleportDuration(2);
                e.setViewRange(2.0F);
            }));
        }
    }

    private void startFalling(DropInstance drop, long tick) {
        drop.state = DropInstance.State.FALLING;
        drop.stateStartTick = tick;

        Location spawnLoc = drop.location.clone().add(0.0, this.module.getInt("drop.fall-height", 48, 8, 150), 0.0);
        spawnLoc.setY(Math.min(drop.location.getWorld().getMaxHeight() - 4, spawnLoc.getY()));

        this.ensureDisplayEntities(drop, spawnLoc);
        this.updateHologramText(drop);

        String launchedTitle = this.module.getString("messages.launched-title", drop.displayName);
        String launchedSubtitle = this.module.getString("messages.launched-subtitle", drop.description);
        for (Player p : drop.location.getWorld().getPlayers()) {
            this.module.getMessageFormatter().showTitle(p, launchedTitle, launchedSubtitle);
            this.module.getMessageFormatter().playSound(p, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5F, 0.8F);
        }

        this.module.getCinematicManager().triggerCinematic(drop);
    }

    private void updateEntityPositions(DropInstance drop, Location loc, long tick, boolean falling) {
        if (drop.blockDisplay != null && drop.blockDisplay.isValid()) {
            drop.blockDisplay.teleport(loc);
            boolean animate = falling || this.module.getDouble("presentation.chest-bob-height", 0.0, 0.0, 0.5) > 0.0;
            double rot = !animate ? 0.0 : (falling ? tick * 0.025 : Math.sin(tick * 0.012) * 0.12);
            drop.blockDisplay.setTransformationMatrix(new Matrix4f().rotateY((float) rot).translate(-0.5F, 0.0F, -0.5F));
            drop.blockDisplay.setInterpolationDelay(0);
            drop.blockDisplay.setInterpolationDuration(2);
        }

        if (drop.textDisplay != null && drop.textDisplay.isValid()) {
            drop.textDisplay.teleport(loc.clone().add(0.0, 2.25, 0.0));
        }

        if (!drop.opened) {
            this.module.getParticleRenderer().renderOrbit(loc, tick);
        }
    }

    private void landDrop(DropInstance drop, long tick) {
        drop.state = DropInstance.State.LANDED;
        drop.lastTick = tick;
        int expiryMinutes = drop.tier.expiryMinutes();
        drop.stateStartTick = tick + expiryMinutes * 1200L;

        this.ensureDisplayEntities(drop, drop.location);

        drop.interaction = this.trackEntity(drop, drop.location.getWorld().spawn(drop.location.clone().add(0.0, -0.25, 0.0), Interaction.class, e -> {
            e.setInteractionWidth(1.6F);
            e.setInteractionHeight(1.7F);
            e.setResponsive(true);
        }));

        this.updateEntityPositions(drop, drop.location, tick, false);
        this.updateHologramText(drop);
        if (drop.fallDurationTicks > 0L) {
            this.module.getParticleRenderer().renderImpactBurst(drop.location);
            this.module.getCinematicManager().onDropLanded(drop);
            this.module.getMessageFormatter().playSound(drop.location, Sound.BLOCK_END_PORTAL_SPAWN, 0.65F, 1.6F);
        }

        Component msg = this.module.getMessageFormatter().gradient(drop.displayName + " #" + drop.id + " DISPONIBLE ", drop.tier.colorStart(), drop.tier.colorEnd())
            .append(this.module.getMessageFormatter().coords(drop.location))
            .append(this.module.getMessageFormatter().detail(" | " + drop.location.getWorld().getName() + " | " + expiryMinutes + "m"));
        this.module.getMessageFormatter().broadcast(msg);
    }

    public void updateHologramText(DropInstance drop) {
        if (drop.textDisplay != null) {
            Component subtitle = this.module.getMessageFormatter().detail("ʟɪᴍɪᴛᴇ: " + drop.tier.expiryMinutes() + "ᴍ | ᴄʟɪᴄᴋ ᴘᴀʀᴀ ʀᴇᴄᴏɢᴇʀ");
            Component text = this.module.getMessageFormatter().gradient(drop.displayName, drop.tier.colorStart(), drop.tier.colorEnd())
                .append(Component.newline())
                .append(this.module.getMessageFormatter().detail(drop.description));

            if (drop.state != DropInstance.State.FALLING) {
                text = text.append(Component.newline()).append(subtitle);
            }
            drop.textDisplay.text(text.append(Component.newline()).append(this.module.getMessageFormatter().detail("#" + drop.id)));
        }
    }

    public void removeDrop(DropInstance drop, String reason) {
        if (drop.state != DropInstance.State.REMOVED) {
            drop.state = DropInstance.State.REMOVED;
            this.module.getCinematicManager().cancelCinematicForDrop(drop.id);

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getOpenInventory().getTopInventory().getHolder() == drop) {
                    p.closeInventory();
                }
            }

            for (Entity e : drop.entities) {
                if (e.isValid()) {
                    e.remove();
                }
            }

            drop.entities.clear();
            this.activeDrops.remove(drop.id);
            this.removeChunkTicket(drop);

            if (!this.module.isDisabling() && ("expirado".equals(reason) || "cancelado".equals(reason))) {
                this.module.getMessageFormatter().broadcast(this.module.getMessageFormatter().detail("ᴅʀᴏᴘ #" + drop.id + " " + reason));
            }
        }
    }

    public void cancelAll() {
        this.launchEpoch++;
        this.pendingSpawns = 0;
        for (DropInstance drop : this.getActiveDrops()) {
            this.removeDrop(drop, "cancelado");
        }
    }

    public void shutdown() {
        this.cancelAll();
        for (World world : Bukkit.getWorlds()) {
            world.removePluginChunkTickets(this.module.getPlugin());
        }
        this.chunkTickets.clear();
    }

    public void purgeResidualEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(this.dropKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }

    public void handleChunkLoad(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity.getPersistentDataContainer().has(this.dropKey, PersistentDataType.STRING) && this.findByEntity(entity) == null) {
                entity.remove();
            }
        }
    }
}
