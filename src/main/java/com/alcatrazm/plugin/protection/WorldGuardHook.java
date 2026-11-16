package com.alcatrazm.plugin.protection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class WorldGuardHook {

    private final boolean worldGuardPresent;
    private static boolean initialized = false;
    private static Method getInstanceMethod;
    private static Method getPlatformMethod;
    private static Method getRegionContainerMethod;
    private static Method createQueryMethod;
    private static Method adaptLocMethod;
    private static Method adaptPlayerMethod;
    private static Method testBuildMethod;
    private static Method getApplicableRegionsMethod;

    public WorldGuardHook() {
        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        this.worldGuardPresent = (wg != null && wg.isEnabled());
        if (this.worldGuardPresent && !initialized) {
            initReflection();
        }
    }

    private static synchronized void initReflection() {
        if (initialized) return;
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            getInstanceMethod = wgClass.getMethod("getInstance");
            Object wgInstance = getInstanceMethod.invoke(null);

            getPlatformMethod = wgInstance.getClass().getMethod("getPlatform");
            Object platform = getPlatformMethod.invoke(wgInstance);

            getRegionContainerMethod = platform.getClass().getMethod("getRegionContainer");
            Object container = getRegionContainerMethod.invoke(platform);

            createQueryMethod = container.getClass().getMethod("createQuery");

            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            adaptLocMethod = adapterClass.getMethod("adapt", Location.class);
            adaptPlayerMethod = adapterClass.getMethod("adapt", Player.class);

            Object query = createQueryMethod.invoke(container);
            for (Method m : query.getClass().getMethods()) {
                if (m.getName().equals("testBuild") && m.getParameterCount() == 2) {
                    testBuildMethod = m;
                } else if (m.getName().equals("getApplicableRegions") && m.getParameterCount() == 1) {
                    getApplicableRegionsMethod = m;
                }
            }
            initialized = true;
        } catch (Throwable ignored) {
            initialized = false;
        }
    }

    public boolean isWorldGuardPresent() {
        return worldGuardPresent;
    }

    /**
     * Indica si existen regiones de WorldGuard definidas en la ubicacion dada (excluyendo __global__).
     */
    public boolean hasApplicableRegions(Location location) {
        if (!worldGuardPresent || location == null) {
            return false;
        }
        if (!initialized) {
            initReflection();
            if (!initialized) return false;
        }
        if (getApplicableRegionsMethod == null) {
            return false;
        }
        try {
            Object wgInstance = getInstanceMethod.invoke(null);
            Object platform = getPlatformMethod.invoke(wgInstance);
            Object container = getRegionContainerMethod.invoke(platform);
            Object query = createQueryMethod.invoke(container);
            Object adaptedLoc = adaptLocMethod.invoke(null, location);
            Object regionSet = getApplicableRegionsMethod.invoke(query, adaptedLoc);
            if (regionSet != null) {
                Object sizeObj = regionSet.getClass().getMethod("size").invoke(regionSet);
                if (sizeObj instanceof Integer size) {
                    return size > 0;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public boolean canBuildWithWorldGuard(Player player, Location location) {
        if (!worldGuardPresent) {
            return true;
        }
        if (!initialized) {
            initReflection();
            if (!initialized) return true;
        }

        try {
            Object wgInstance = getInstanceMethod.invoke(null);
            Object platform = getPlatformMethod.invoke(wgInstance);
            Object container = getRegionContainerMethod.invoke(platform);
            Object query = createQueryMethod.invoke(container);

            Object adaptedLoc = adaptLocMethod.invoke(null, location);
            Object adaptedPlayer = adaptPlayerMethod.invoke(null, player);

            Object result = testBuildMethod.invoke(query, adaptedLoc, adaptedPlayer);
            if (result instanceof Boolean b) {
                return b;
            }
        } catch (Throwable ignored) {
        }

        return true;
    }
}
