package com.alcatrazm.plugin.airdrop;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Representa un AirDrop activo en el mundo con su inventario compartido y entidades de visualizacion.
 */
public final class DropInstance implements InventoryHolder {
    public final String id;
    public final DropTier tier;
    public final String displayName;
    public final String description;
    public final DropMath.DropType type;
    public final Location location;
    public final Inventory inventory;
    public final List<Entity> entities = new ArrayList<>();
    public final long creationTick;

    public State state = State.COUNTDOWN;
    public long lastTick;
    public long stateStartTick;
    public long countdownDurationTicks;
    public long fallDurationTicks;
    public int remainingLootSeconds = -1;
    public boolean opened;
    public boolean removed;

    public BlockDisplay blockDisplay;
    public TextDisplay textDisplay;
    public Interaction interaction;

    public DropInstance(AirDropModule module, String id, DropTier tier, DropMath.DropType type, Location location, List<ItemStack> items, long tick) {
        this.id = id;
        this.tier = tier;
        this.type = type;
        this.location = location.clone();
        this.creationTick = tick;
        this.lastTick = tick;
        this.displayName = MessageFormatter.cleanUpper(tier.displayName());
        this.description = MessageFormatter.toSmallCaps(tier.description());
        this.countdownDurationTicks = module.getInt("drop.countdown-seconds", 0, 0, 120) * 20L;
        this.fallDurationTicks = module.getInt("drop.fall-seconds", 0, 0, 30) * 20L;

        int size = Math.max(9, Math.min(54, (items.size() + 8) / 9 * 9));
        this.inventory = Bukkit.createInventory(this, size, module.getMessageFormatter().gradient(this.displayName + " #" + id, tier.colorStart(), tier.colorEnd()));

        for (int i = 0; i < items.size() && i < size; i++) {
            this.inventory.setItem(i, items.get(i).clone());
        }
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    public boolean isEmpty() {
        for (ItemStack item : this.inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    public boolean isInteractable() {
        return this.state == State.LANDED || this.state == State.LOOTED;
    }

    public boolean canAccess(UUID uuid) {
        return this.isInteractable();
    }

    public enum State {
        COUNTDOWN,
        FALLING,
        LANDED,
        LOOTED,
        REMOVED
    }
}
