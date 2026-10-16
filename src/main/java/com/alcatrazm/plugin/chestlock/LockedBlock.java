package com.alcatrazm.plugin.chestlock;

import java.util.*;

public class LockedBlock {

    private final String key; // world:x:y:z
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private UUID ownerUUID;
    private String ownerName;
    private LockType lockType;
    private final Set<String> trustedNames = new HashSet<>();
    private int autoCloseSeconds;
    private boolean door;
    private boolean hiddenSignOnDoor;

    public LockedBlock(String key, String worldName, int x, int y, int z, UUID ownerUUID, String ownerName, 
                       LockType lockType, int autoCloseSeconds, boolean door, boolean hiddenSignOnDoor) {
        this.key = key;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.lockType = lockType != null ? lockType : LockType.PRIVATE;
        this.autoCloseSeconds = autoCloseSeconds;
        this.door = door;
        this.hiddenSignOnDoor = hiddenSignOnDoor;
    }

    public boolean isOwner(UUID uuid) {
        return ownerUUID != null && ownerUUID.equals(uuid);
    }

    public boolean isTrusted(String name) {
        if (name == null) return false;
        if (ownerName != null && ownerName.equalsIgnoreCase(name)) return true;
        for (String t : trustedNames) {
            if (t.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public void addTrust(String name) {
        if (name != null && !name.trim().isEmpty()) {
            trustedNames.add(name.trim());
        }
    }

    public void removeTrust(String name) {
        if (name != null) {
            trustedNames.removeIf(t -> t.equalsIgnoreCase(name.trim()));
        }
    }

    public String getKey() { return key; }
    public String getWorldName() { return worldName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public void setOwnerUUID(UUID ownerUUID) { this.ownerUUID = ownerUUID; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public LockType getLockType() { return lockType; }
    public void setLockType(LockType lockType) { this.lockType = lockType; }
    public Set<String> getTrustedNames() { return Collections.unmodifiableSet(trustedNames); }
    public int getAutoCloseSeconds() { return autoCloseSeconds; }
    public void setAutoCloseSeconds(int autoCloseSeconds) { this.autoCloseSeconds = autoCloseSeconds; }
    public boolean isDoor() { return door; }
    public void setDoor(boolean door) { this.door = door; }
    public boolean isHiddenSignOnDoor() { return hiddenSignOnDoor; }
    public void setHiddenSignOnDoor(boolean hiddenSignOnDoor) { this.hiddenSignOnDoor = hiddenSignOnDoor; }
}
