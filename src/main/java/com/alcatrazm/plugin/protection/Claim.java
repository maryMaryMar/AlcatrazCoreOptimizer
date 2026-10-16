package com.alcatrazm.plugin.protection;

import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Claim {

    private final String id;
    private String name;
    private UUID ownerUUID;
    private String ownerName;
    private ClaimType type;
    private ProtectionBlockType blockType;
    private long expirationTimeMillis; // 0 para permanentes, timestamp para temporales
    private String worldName;
    private int blockX;
    private int blockY;
    private int blockZ;
    private int radius;
    private boolean trustAll = false;

    private final Set<UUID> trustedPlayers = new HashSet<>();
    private final Map<ClaimFlag, Boolean> flags = new EnumMap<>(ClaimFlag.class);

    public Claim(String id, String name, UUID ownerUUID, String ownerName, ClaimType type, 
                 ProtectionBlockType blockType, long expirationTimeMillis,
                 String worldName, int blockX, int blockY, int blockZ, int radius) {
        this.id = id;
        this.name = name;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.type = type;
        this.blockType = blockType;
        this.expirationTimeMillis = expirationTimeMillis;
        this.worldName = worldName;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.radius = radius;

        for (ClaimFlag f : ClaimFlag.values()) {
            flags.put(f, f.getDefaultNormalAllow());
        }
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equalsIgnoreCase(worldName)) return false;

        int minX = blockX - radius;
        int maxX = blockX + radius;
        int minZ = blockZ - radius;
        int maxZ = blockZ + radius;

        int x = loc.getBlockX();
        int z = loc.getBlockZ();

        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean contains3D(Location loc) {
        if (!contains(loc)) return false;
        int minY = Math.max(loc.getWorld().getMinHeight(), blockY - radius);
        int maxY = Math.min(loc.getWorld().getMaxHeight(), blockY + radius);
        int y = loc.getBlockY();
        return y >= minY && y <= maxY;
    }

    public boolean isProtectionBlockLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return loc.getWorld().getName().equalsIgnoreCase(worldName)
                && loc.getBlockX() == blockX
                && loc.getBlockY() == blockY
                && loc.getBlockZ() == blockZ;
    }

    public boolean isExpired() {
        return type == ClaimType.TEMPORARY && expirationTimeMillis > 0 && System.currentTimeMillis() > expirationTimeMillis;
    }

    public String getRemainingTimeFormatted() {
        if (type != ClaimType.TEMPORARY || expirationTimeMillis <= 0) {
            return "&aPermanente";
        }
        long diff = expirationTimeMillis - System.currentTimeMillis();
        if (diff <= 0) return "&c¡EXPIRADA!";

        long days = TimeUnit.MILLISECONDS.toDays(diff);
        long hours = TimeUnit.MILLISECONDS.toHours(diff) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        sb.append(minutes).append("m restantes");

        return sb.toString();
    }

    public boolean isOwner(UUID uuid) {
        if (uuid == null) return false;
        return ownerUUID != null && ownerUUID.equals(uuid);
    }

    public boolean isTrusted(UUID uuid) {
        if (trustAll) return true;
        if (uuid == null) return false;
        if (isOwner(uuid)) return true;
        return trustedPlayers.contains(uuid);
    }

    public void addTrust(UUID uuid) {
        if (uuid != null) {
            trustedPlayers.add(uuid);
        }
    }

    public void removeTrust(UUID uuid) {
        if (uuid != null) {
            trustedPlayers.remove(uuid);
        }
    }

    public boolean isTrustAll() {
        return trustAll;
    }

    public void setTrustAll(boolean trustAll) {
        this.trustAll = trustAll;
    }

    public boolean getFlag(ClaimFlag flag) {
        if (flag == null) return false;
        return flags.getOrDefault(flag, flag.getDefaultNormalAllow());
    }

    public void setFlag(ClaimFlag flag, boolean allow) {
        if (flag != null) {
            flags.put(flag, allow);
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public String getOwnerName() { return ownerName; }
    public ClaimType getType() { return type; }
    public void setType(ClaimType type) { this.type = type; }
    public ProtectionBlockType getBlockType() { return blockType; }
    public long getExpirationTimeMillis() { return expirationTimeMillis; }
    public void setExpirationTimeMillis(long exp) { this.expirationTimeMillis = exp; }
    public String getWorldName() { return worldName; }
    public int getBlockX() { return blockX; }
    public int getBlockY() { return blockY; }
    public int getBlockZ() { return blockZ; }
    public int getRadius() { return radius; }
    public Set<UUID> getTrustedPlayers() { return Collections.unmodifiableSet(trustedPlayers); }
    public Map<ClaimFlag, Boolean> getFlags() { return Collections.unmodifiableMap(flags); }
}
