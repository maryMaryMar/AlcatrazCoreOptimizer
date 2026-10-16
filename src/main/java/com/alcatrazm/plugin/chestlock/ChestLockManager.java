package com.alcatrazm.plugin.chestlock;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ChestLockManager {

    private final AlcatrazPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    private final Map<String, LockedBlock> locks = new HashMap<>(); // key -> LockedBlock
    private boolean enabled;
    private boolean doorAutoCloseDefault;
    private int defaultDoorTimerSeconds;

    // Keys para PDC Híbrido
    public final NamespacedKey KEY_OWNER_UUID;
    public final NamespacedKey KEY_OWNER_NAME;
    public final NamespacedKey KEY_LOCK_TYPE;
    public final NamespacedKey KEY_LOCK_TIMER;
    public final NamespacedKey KEY_LOCK_TRUSTED;

    public ChestLockManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.KEY_OWNER_UUID = new NamespacedKey(plugin, "lock_owner_uuid");
        this.KEY_OWNER_NAME = new NamespacedKey(plugin, "lock_owner_name");
        this.KEY_LOCK_TYPE = new NamespacedKey(plugin, "lock_type");
        this.KEY_LOCK_TIMER = new NamespacedKey(plugin, "lock_timer");
        this.KEY_LOCK_TRUSTED = new NamespacedKey(plugin, "lock_trusted");

        this.file = new File(plugin.getDataFolder(), "locks.yml");
        loadConfig();
        loadLocks();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("chest-lock.enabled", true);
        this.doorAutoCloseDefault = plugin.getConfig().getBoolean("chest-lock.door-auto-close", true);
        this.defaultDoorTimerSeconds = plugin.getConfig().getInt("chest-lock.default-door-timer-seconds", 3);
    }

    public void reload() {
        loadConfig();
        loadLocks();
    }

    public String getBlockKey(Block block) {
        if (block == null || block.getWorld() == null) return null;
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    public boolean isLockable(Block block) {
        if (block == null) return false;
        Material mat = block.getType();
        String name = mat.name();

        return name.contains("CHEST") || name.contains("BARREL") || name.contains("SHULKER")
                || name.contains("FURNACE") || name.contains("SMOKER") || name.contains("HOPPER")
                || name.contains("DISPENSER") || name.contains("DROPPER") || name.contains("BREWING_STAND")
                || name.contains("CRAFTER") || name.contains("DECORATED_POT")
                || isDoor(block);
    }

    public boolean isDoor(Block block) {
        if (block == null) return false;
        BlockData data = block.getBlockData();
        return data instanceof Door || data instanceof TrapDoor || data instanceof Gate;
    }

    public Block getRelatedDoorPart(Block block) {
        if (block == null) return null;
        BlockData data = block.getBlockData();
        if (data instanceof Door door) {
            if (door.getHalf() == Bisected.Half.BOTTOM) {
                return block.getRelative(BlockFace.UP);
            } else {
                return block.getRelative(BlockFace.DOWN);
            }
        }
        return null;
    }

    public Block getRelatedDoubleChestPart(Block block) {
        if (block == null) return null;
        BlockData data = block.getBlockData();
        if (data instanceof Chest chest) {
            if (chest.getType() == Chest.Type.SINGLE) return null;
            BlockFace facing = chest.getFacing();
            BlockFace leftFace = getLeftFace(facing);
            BlockFace rightFace = getRightFace(facing);

            if (chest.getType() == Chest.Type.LEFT) {
                Block neighbor = block.getRelative(rightFace);
                if (neighbor.getBlockData() instanceof Chest otherChest && otherChest.getType() == Chest.Type.RIGHT) {
                    return neighbor;
                }
            } else if (chest.getType() == Chest.Type.RIGHT) {
                Block neighbor = block.getRelative(leftFace);
                if (neighbor.getBlockData() instanceof Chest otherChest && otherChest.getType() == Chest.Type.LEFT) {
                    return neighbor;
                }
            }
        }
        return null;
    }

    private BlockFace getLeftFace(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.WEST;
            case SOUTH -> BlockFace.EAST;
            case WEST -> BlockFace.SOUTH;
            case EAST -> BlockFace.NORTH;
            default -> BlockFace.SELF;
        };
    }

    private BlockFace getRightFace(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.EAST;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            case EAST -> BlockFace.SOUTH;
            default -> BlockFace.SELF;
        };
    }

    public LockedBlock getLock(Block block) {
        if (block == null) return null;
        String key = getBlockKey(block);
        if (locks.containsKey(key)) {
            return locks.get(key);
        }

        // Si es puerta, revisar la otra mitad
        Block doorPart = getRelatedDoorPart(block);
        if (doorPart != null) {
            String doorKey = getBlockKey(doorPart);
            if (locks.containsKey(doorKey)) {
                return locks.get(doorKey);
            }
        }

        // Si es cofre doble, revisar la otra mitad
        Block chestPart = getRelatedDoubleChestPart(block);
        if (chestPart != null) {
            String chestKey = getBlockKey(chestPart);
            if (locks.containsKey(chestKey)) {
                return locks.get(chestKey);
            }
        }

        // Si es un cartel puesto sobre un cofre/puerta
        if (block.getState() instanceof Sign) {
            Block attached = getAttachedBlock(block);
            if (attached != null) {
                return getLock(attached);
            }
        }

        // Auto-Recuperación Híbrida desde el PDC del Bloque / Chunk
        LockedBlock recovered = recoverLockFromPDC(block);
        if (recovered != null) {
            locks.put(recovered.getKey(), recovered);
            saveLocks();
            return recovered;
        }

        return null;
    }

    private LockedBlock recoverLockFromPDC(Block block) {
        if (block.getState() instanceof TileState tileState) {
            PersistentDataContainer pdc = tileState.getPersistentDataContainer();
            if (pdc.has(KEY_OWNER_UUID, PersistentDataType.STRING)) {
                String uuidStr = pdc.get(KEY_OWNER_UUID, PersistentDataType.STRING);
                UUID uuid = uuidStr != null ? UUID.fromString(uuidStr) : null;
                String name = pdc.getOrDefault(KEY_OWNER_NAME, PersistentDataType.STRING, "Desconocido");
                String typeStr = pdc.getOrDefault(KEY_LOCK_TYPE, PersistentDataType.STRING, "PRIVATE");
                LockType type = LockType.fromString(typeStr);
                int timer = pdc.getOrDefault(KEY_LOCK_TIMER, PersistentDataType.INTEGER, 0);
                String trustedRaw = pdc.getOrDefault(KEY_LOCK_TRUSTED, PersistentDataType.STRING, "");

                String key = getBlockKey(block);
                LockedBlock lock = new LockedBlock(key, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), uuid, name, type, timer, isDoor(block), false);
                if (!trustedRaw.isEmpty()) {
                    for (String t : trustedRaw.split(",")) {
                        lock.addTrust(t);
                    }
                }
                return lock;
            }
        }

        // Para puertas guardadas en el PDC del Chunk
        Chunk chunk = block.getChunk();
        PersistentDataContainer chunkPDC = chunk.getPersistentDataContainer();
        NamespacedKey doorKey = new NamespacedKey(plugin, "door_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
        if (chunkPDC.has(doorKey, PersistentDataType.STRING)) {
            String raw = chunkPDC.get(doorKey, PersistentDataType.STRING);
            if (raw != null && !raw.isEmpty()) {
                String[] parts = raw.split(";;");
                if (parts.length >= 4) {
                    UUID uuid = UUID.fromString(parts[0]);
                    String name = parts[1];
                    LockType type = LockType.fromString(parts[2]);
                    int timer = Integer.parseInt(parts[3]);
                    boolean hidden = parts.length > 4 && Boolean.parseBoolean(parts[4]);

                    String key = getBlockKey(block);
                    LockedBlock lock = new LockedBlock(key, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), uuid, name, type, timer, true, hidden);
                    if (parts.length > 5 && !parts[5].isEmpty()) {
                        for (String t : parts[5].split(",")) {
                            lock.addTrust(t);
                        }
                    }
                    return lock;
                }
            }
        }

        return null;
    }

    private Block getAttachedBlock(Block signBlock) {
        BlockData data = signBlock.getBlockData();
        if (data instanceof org.bukkit.block.data.type.WallSign wallSign) {
            return signBlock.getRelative(wallSign.getFacing().getOppositeFace());
        }
        return signBlock.getRelative(BlockFace.DOWN);
    }

    public LockedBlock lockBlock(Block block, Player player, LockType lockType, int timerSeconds, boolean hiddenSignOnDoor) {
        if (block == null || player == null) return null;
        String key = getBlockKey(block);
        String worldName = block.getWorld().getName();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        boolean isDoor = isDoor(block);
        int timer = isDoor ? (timerSeconds > 0 ? timerSeconds : defaultDoorTimerSeconds) : 0;

        LockedBlock lock = new LockedBlock(key, worldName, x, y, z, player.getUniqueId(), player.getName(), lockType, timer, isDoor, hiddenSignOnDoor);
        locks.put(key, lock);

        // 1. Guardado en YML
        saveLocks();

        // 2. Guardado Híbrido en PDC de TileState (Contenedores / Carteles)
        if (block.getState() instanceof TileState tileState) {
            PersistentDataContainer pdc = tileState.getPersistentDataContainer();
            pdc.set(KEY_OWNER_UUID, PersistentDataType.STRING, player.getUniqueId().toString());
            pdc.set(KEY_OWNER_NAME, PersistentDataType.STRING, player.getName());
            pdc.set(KEY_LOCK_TYPE, PersistentDataType.STRING, lockType.name());
            pdc.set(KEY_LOCK_TIMER, PersistentDataType.INTEGER, timer);
            pdc.set(KEY_LOCK_TRUSTED, PersistentDataType.STRING, String.join(",", lock.getTrustedNames()));
            tileState.update();
        }

        // 3. Guardado Híbrido para Puertas en el PDC del Chunk
        if (isDoor) {
            Chunk chunk = block.getChunk();
            NamespacedKey doorKey = new NamespacedKey(plugin, "door_" + x + "_" + y + "_" + z);
            String raw = player.getUniqueId() + ";;" + player.getName() + ";;" + lockType.name() + ";;" + timer + ";;" + hiddenSignOnDoor + ";;" + String.join(",", lock.getTrustedNames());
            chunk.getPersistentDataContainer().set(doorKey, PersistentDataType.STRING, raw);
        }

        return lock;
    }

    public void updateLockPDC(LockedBlock lock, Block block) {
        if (lock == null || block == null) return;

        if (block.getState() instanceof TileState tileState) {
            PersistentDataContainer pdc = tileState.getPersistentDataContainer();
            pdc.set(KEY_OWNER_UUID, PersistentDataType.STRING, lock.getOwnerUUID().toString());
            pdc.set(KEY_OWNER_NAME, PersistentDataType.STRING, lock.getOwnerName());
            pdc.set(KEY_LOCK_TYPE, PersistentDataType.STRING, lock.getLockType().name());
            pdc.set(KEY_LOCK_TIMER, PersistentDataType.INTEGER, lock.getAutoCloseSeconds());
            pdc.set(KEY_LOCK_TRUSTED, PersistentDataType.STRING, String.join(",", lock.getTrustedNames()));
            tileState.update();
        }

        if (lock.isDoor()) {
            Chunk chunk = block.getChunk();
            NamespacedKey doorKey = new NamespacedKey(plugin, "door_" + lock.getX() + "_" + lock.getY() + "_" + lock.getZ());
            String raw = lock.getOwnerUUID() + ";;" + lock.getOwnerName() + ";;" + lock.getLockType().name() + ";;" + lock.getAutoCloseSeconds() + ";;" + lock.isHiddenSignOnDoor() + ";;" + String.join(",", lock.getTrustedNames());
            chunk.getPersistentDataContainer().set(doorKey, PersistentDataType.STRING, raw);
        }
    }

    public void unlockBlock(Block block) {
        if (block == null) return;
        LockedBlock lock = getLock(block);
        if (lock != null) {
            locks.remove(lock.getKey());
            saveLocks();

            // Limpiar PDC
            if (block.getState() instanceof TileState tileState) {
                PersistentDataContainer pdc = tileState.getPersistentDataContainer();
                pdc.remove(KEY_OWNER_UUID);
                pdc.remove(KEY_OWNER_NAME);
                pdc.remove(KEY_LOCK_TYPE);
                pdc.remove(KEY_LOCK_TIMER);
                pdc.remove(KEY_LOCK_TRUSTED);
                tileState.update();
            }

            if (lock.isDoor()) {
                Chunk chunk = block.getChunk();
                NamespacedKey doorKey = new NamespacedKey(plugin, "door_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
                chunk.getPersistentDataContainer().remove(doorKey);
            }
        }
    }

    public boolean canAccess(Block block, Player player) {
        if (player == null) return false;
        if (player.hasPermission("alcatraz.lock.admin") || player.hasPermission("alcatraz.admin")) return true;

        LockedBlock lock = getLock(block);
        if (lock == null) return true;

        if (lock.getLockType() == LockType.PUBLIC) return true;
        if (lock.isOwner(player.getUniqueId())) return true;
        return lock.isTrusted(player.getName());
    }

    public boolean canBreak(Block block, Player player) {
        if (player == null) return false;
        if (player.hasPermission("alcatraz.lock.admin") || player.hasPermission("alcatraz.admin")) return true;

        LockedBlock lock = getLock(block);
        if (lock == null) return true;

        return lock.isOwner(player.getUniqueId());
    }

    public void toggleDoorWithTimer(Block block, Player player) {
        if (block == null) return;
        BlockData data = block.getBlockData();
        if (!(data instanceof Openable openable)) return;

        LockedBlock lock = getLock(block);
        int timer = (lock != null && lock.getAutoCloseSeconds() > 0) ? lock.getAutoCloseSeconds() : (doorAutoCloseDefault ? defaultDoorTimerSeconds : 0);

        boolean newState = !openable.isOpen();
        openable.setOpen(newState);
        block.setBlockData(openable);

        Block otherHalf = getRelatedDoorPart(block);
        if (otherHalf != null && otherHalf.getBlockData() instanceof Openable otherOpenable) {
            otherOpenable.setOpen(newState);
            otherHalf.setBlockData(otherOpenable);
        }

        block.getWorld().playSound(block.getLocation(), newState ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE, 1.0f, 1.0f);

        if (newState && timer > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (block.getBlockData() instanceof Openable o && o.isOpen()) {
                        o.setOpen(false);
                        block.setBlockData(o);

                        if (otherHalf != null && otherHalf.getBlockData() instanceof Openable o2) {
                            o2.setOpen(false);
                            otherHalf.setBlockData(o2);
                        }
                        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE, 1.0f, 1.0f);
                    }
                }
            }.runTaskLater(plugin, timer * 20L);
        }
    }

    public void loadLocks() {
        locks.clear();
        if (!file.exists()) {
            try {
                if (file.getParentFile() != null && !file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear locks.yml: " + e.getMessage());
            }
            return;
        }

        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = config.getConfigurationSection("locks");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            String world = sec.getString(key + ".world", "world");
            int x = sec.getInt(key + ".x");
            int y = sec.getInt(key + ".y");
            int z = sec.getInt(key + ".z");
            String ownerUUIDStr = sec.getString(key + ".owner-uuid");
            UUID ownerUUID = ownerUUIDStr != null ? UUID.fromString(ownerUUIDStr) : null;
            String ownerName = sec.getString(key + ".owner-name", "Desconocido");
            String lockTypeStr = sec.getString(key + ".type", "PRIVATE");
            LockType type = LockType.fromString(lockTypeStr);
            int timer = sec.getInt(key + ".timer", 0);
            boolean isDoor = sec.getBoolean(key + ".is-door", false);
            boolean hiddenSign = sec.getBoolean(key + ".hidden-sign", false);

            LockedBlock lock = new LockedBlock(key, world, x, y, z, ownerUUID, ownerName, type, timer, isDoor, hiddenSign);
            List<String> trusted = sec.getStringList(key + ".trusted");
            for (String t : trusted) {
                lock.addTrust(t);
            }

            locks.put(key, lock);
        }
    }

    public void saveLocks() {
        if (config == null) {
            config = new YamlConfiguration();
        }
        config.set("locks", null);

        for (LockedBlock lock : locks.values()) {
            String path = "locks." + lock.getKey();
            config.set(path + ".world", lock.getWorldName());
            config.set(path + ".x", lock.getX());
            config.set(path + ".y", lock.getY());
            config.set(path + ".z", lock.getZ());
            config.set(path + ".owner-uuid", lock.getOwnerUUID() != null ? lock.getOwnerUUID().toString() : null);
            config.set(path + ".owner-name", lock.getOwnerName());
            config.set(path + ".type", lock.getLockType().name());
            config.set(path + ".timer", lock.getAutoCloseSeconds());
            config.set(path + ".is-door", lock.isDoor());
            config.set(path + ".hidden-sign", lock.isHiddenSignOnDoor());
            config.set(path + ".trusted", new ArrayList<>(lock.getTrustedNames()));
        }

        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Error al guardar locks.yml: " + e.getMessage());
        }
    }

    public boolean isEnabled() { return enabled; }
}
