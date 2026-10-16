package com.alcatrazm.plugin.protection;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimResizeEvent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class GriefPreventionListener implements Listener {

    private final ProtectionManager protectionManager;

    public GriefPreventionListener(ProtectionManager protectionManager) {
        this.protectionManager = protectionManager;
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClaimCreated(ClaimCreatedEvent event) {
        if (!protectionManager.isEnabled()) return;
        Claim gpClaim = event.getClaim();
        if (gpClaim == null) return;

        Location lesser = gpClaim.getLesserBoundaryCorner();
        Location greater = gpClaim.getGreaterBoundaryCorner();
        if (lesser == null || greater == null || lesser.getWorld() == null) return;

        String worldName = lesser.getWorld().getName();
        int minX = Math.min(lesser.getBlockX(), greater.getBlockX());
        int maxX = Math.max(lesser.getBlockX(), greater.getBlockX());
        int minZ = Math.min(lesser.getBlockZ(), greater.getBlockZ());
        int maxZ = Math.max(lesser.getBlockZ(), greater.getBlockZ());

        com.alcatrazm.plugin.protection.Claim psClaim = protectionManager.getOverlappingPSClaim(worldName, minX, maxX, minZ, maxZ);
        if (psClaim != null) {
            event.setCancelled(true);
            if (event.getCreator() instanceof Player player) {
                player.sendMessage(color("&8[&cProtección&8] &cNo puedes reclamar con pala de oro aquí: colisiona con la Piedra de Protección (PS) de &e" + psClaim.getOwnerName() + "&c."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClaimResize(ClaimResizeEvent event) {
        if (!protectionManager.isEnabled()) return;
        Claim gpClaim = event.getTo();
        if (gpClaim == null) return;

        Location lesser = gpClaim.getLesserBoundaryCorner();
        Location greater = gpClaim.getGreaterBoundaryCorner();
        if (lesser == null || greater == null || lesser.getWorld() == null) return;

        String worldName = lesser.getWorld().getName();
        int minX = Math.min(lesser.getBlockX(), greater.getBlockX());
        int maxX = Math.max(lesser.getBlockX(), greater.getBlockX());
        int minZ = Math.min(lesser.getBlockZ(), greater.getBlockZ());
        int maxZ = Math.max(lesser.getBlockZ(), greater.getBlockZ());

        com.alcatrazm.plugin.protection.Claim psClaim = protectionManager.getOverlappingPSClaim(worldName, minX, maxX, minZ, maxZ);
        if (psClaim != null) {
            event.setCancelled(true);
            if (event.getModifier() instanceof Player player) {
                player.sendMessage(color("&8[&cProtección&8] &cNo puedes redimensionar tu claim aquí: colisiona con la Piedra de Protección (PS) de &e" + psClaim.getOwnerName() + "&c."));
            }
        }
    }
}
