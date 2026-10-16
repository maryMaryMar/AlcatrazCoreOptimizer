package com.alcatrazm.plugin.elevator;

import com.alcatrazm.plugin.AlcatrazPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ElevatorListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final Set<Material> elevatorMaterials = new HashSet<>();
    private final Map<UUID, Long> debounce = new ConcurrentHashMap<>();

    public ElevatorListener(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        loadMaterials();
    }

    public void loadMaterials() {
        elevatorMaterials.clear();
        List<String> list = plugin.getConfig().getStringList("elevator.block-materials");
        if (list.isEmpty()) {
            elevatorMaterials.add(Material.IRON_BLOCK);
            elevatorMaterials.add(Material.GOLD_BLOCK);
            elevatorMaterials.add(Material.DIAMOND_BLOCK);
            elevatorMaterials.add(Material.EMERALD_BLOCK);
            elevatorMaterials.add(Material.COPPER_BLOCK);
        } else {
            for (String s : list) {
                try {
                    elevatorMaterials.add(Material.valueOf(s.toUpperCase()));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("elevator.enabled", true);
    }

    private int getMaxDistance() {
        return plugin.getConfig().getInt("elevator.max-distance", 20);
    }

    private boolean isDebounced(Player player) {
        Long last = debounce.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < 350) {
            return true;
        }
        debounce.put(player.getUniqueId(), now);
        return false;
    }

    private Block getBlockUnderPlayer(Location loc) {
        Block direct = loc.getBlock();
        if (elevatorMaterials.contains(direct.getType())) {
            return direct;
        }
        Block below = loc.clone().subtract(0, 0.1, 0).getBlock();
        if (elevatorMaterials.contains(below.getType())) {
            return below;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerJump(PlayerMoveEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Detectar salto hacia arriba
        if (to.getY() <= from.getY() + 0.05) return;

        Block currentBlock = getBlockUnderPlayer(from);
        if (currentBlock == null) return;

        if (isDebounced(player)) return;

        Material targetMat = currentBlock.getType();
        int maxDist = getMaxDistance();
        World world = currentBlock.getWorld();
        int startY = currentBlock.getY();
        int maxY = Math.min(world.getMaxHeight() - 2, startY + maxDist);

        for (int y = startY + 2; y <= maxY; y++) {
            Block candidate = world.getBlockAt(currentBlock.getX(), y, currentBlock.getZ());
            if (candidate.getType() == targetMat) {
                Block feet = world.getBlockAt(currentBlock.getX(), y + 1, currentBlock.getZ());
                Block head = world.getBlockAt(currentBlock.getX(), y + 2, currentBlock.getZ());

                if (isPassable(feet) && isPassable(head)) {
                    Location dest = player.getLocation().clone();
                    dest.setX(currentBlock.getX() + 0.5);
                    dest.setY(y + 1);
                    dest.setZ(currentBlock.getZ() + 0.5);

                    teleportElevator(player, dest, true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!isEnabled()) return;
        if (!event.isSneaking()) return;

        Player player = event.getPlayer();
        Block currentBlock = getBlockUnderPlayer(player.getLocation());
        if (currentBlock == null) return;

        if (isDebounced(player)) return;

        Material targetMat = currentBlock.getType();
        int maxDist = getMaxDistance();
        World world = currentBlock.getWorld();
        int startY = currentBlock.getY();
        int minY = Math.max(world.getMinHeight() + 1, startY - maxDist);

        for (int y = startY - 2; y >= minY; y--) {
            Block candidate = world.getBlockAt(currentBlock.getX(), y, currentBlock.getZ());
            if (candidate.getType() == targetMat) {
                Block feet = world.getBlockAt(currentBlock.getX(), y + 1, currentBlock.getZ());
                Block head = world.getBlockAt(currentBlock.getX(), y + 2, currentBlock.getZ());

                if (isPassable(feet) && isPassable(head)) {
                    Location dest = player.getLocation().clone();
                    dest.setX(currentBlock.getX() + 0.5);
                    dest.setY(y + 1);
                    dest.setZ(currentBlock.getZ() + 0.5);

                    teleportElevator(player, dest, false);
                    return;
                }
            }
        }
    }

    private boolean isPassable(Block block) {
        return block.isPassable() || block.getType().isAir();
    }

    private void teleportElevator(Player player, Location dest, boolean goingUp) {
        player.teleport(dest);

        // Sonido
        String soundName = plugin.getConfig().getString("elevator.sound", "ENTITY_ENDERMAN_TELEPORT");
        try {
            player.playSound(dest, Sound.valueOf(soundName), 0.7f, goingUp ? 1.4f : 0.8f);
        } catch (Exception ignored) {}

        // Partículas
        dest.getWorld().spawnParticle(Particle.CLOUD, dest.clone().add(0, 0.2, 0), 10, 0.2, 0.1, 0.2, 0.05);

        // Mensaje en action bar
        String dir = goingUp ? "▲ Subiendo" : "▼ Bajando";
        String msg = ChatColor.translateAlternateColorCodes('&', "&b[Elevador] &e" + dir + " &7(Piso Y: " + dest.getBlockY() + ")");
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        debounce.remove(event.getPlayer().getUniqueId());
    }
}
