package com.alcatrazm.plugin.protection;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

public class GriefPreventionHook {

    private final boolean griefPreventionPresent;

    public GriefPreventionHook() {
        Plugin gp = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        this.griefPreventionPresent = (gp != null && gp.isEnabled());
    }

    public boolean isGriefPreventionPresent() {
        return griefPreventionPresent;
    }

    /**
     * Verifica si el área de una protección PS colisiona con algún claim existente de GriefPrevention.
     * @return El claim de GriefPrevention con el que colisiona, o null si no hay colisión.
     */
    public Claim getOverlappingClaim(String worldName, int centerX, int centerZ, int radius) {
        if (!griefPreventionPresent) return null;

        try {
            GriefPrevention gp = GriefPrevention.instance;
            if (gp == null || gp.dataStore == null) return null;

            int psMinX = centerX - radius;
            int psMaxX = centerX + radius;
            int psMinZ = centerZ - radius;
            int psMaxZ = centerZ + radius;

            Collection<Claim> claims = gp.dataStore.getClaims();
            if (claims == null) return null;

            for (Claim claim : claims) {
                if (claim == null) continue;
                Location lesser = claim.getLesserBoundaryCorner();
                Location greater = claim.getGreaterBoundaryCorner();
                if (lesser == null || greater == null || lesser.getWorld() == null) continue;

                if (!lesser.getWorld().getName().equalsIgnoreCase(worldName)) continue;

                int gpMinX = Math.min(lesser.getBlockX(), greater.getBlockX());
                int gpMaxX = Math.max(lesser.getBlockX(), greater.getBlockX());
                int gpMinZ = Math.min(lesser.getBlockZ(), greater.getBlockZ());
                int gpMaxZ = Math.max(lesser.getBlockZ(), greater.getBlockZ());

                if (psMinX <= gpMaxX && psMaxX >= gpMinX && psMinZ <= gpMaxZ && psMaxZ >= gpMinZ) {
                    return claim;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    /**
     * Verifica si un jugador tiene permiso de construcción en la ubicación dada según GriefPrevention.
     */
    public boolean canBuild(Player player, Location location) {
        if (!griefPreventionPresent || player == null || location == null) return true;

        try {
            GriefPrevention gp = GriefPrevention.instance;
            if (gp == null) return true;

            return gp.allowBuild(player, location) == null;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
