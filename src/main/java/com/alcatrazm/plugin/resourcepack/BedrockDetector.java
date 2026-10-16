package com.alcatrazm.plugin.resourcepack;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Detector y gestor de compatibilidad para jugadores de Minecraft Bedrock Edition (Geyser / Floodgate).
 * Evita fallos, desconexiones y pantallas de congelamiento al interactuar con paquetes de recursos de Java.
 */
public class BedrockDetector {

    private static volatile boolean initialized = false;
    private static volatile boolean floodgateAvailable = false;
    private static Method floodgateIsBedrockMethod = null;
    private static Object floodgateInstance = null;

    private static volatile boolean geyserAvailable = false;
    private static Method geyserIsBedrockMethod = null;
    private static Object geyserInstance = null;

    /**
     * Inicializa los hooks de reflexión una sola vez de forma thread-safe.
     */
    private static void ensureInitialized() {
        if (!initialized) {
            synchronized (BedrockDetector.class) {
                if (!initialized) {
                    initFloodgate();
                    initGeyser();
                    initialized = true;
                }
            }
        }
    }

    private static void initFloodgate() {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Method getMethod = apiClass.getMethod("getInstance");
                floodgateInstance = getMethod.invoke(null);
                floodgateIsBedrockMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
                floodgateAvailable = true;
            }
        } catch (Throwable ignored) {
            floodgateAvailable = false;
        }
    }

    private static void initGeyser() {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) {
                Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Method apiMethod = apiClass.getMethod("api");
                geyserInstance = apiMethod.invoke(null);
                geyserIsBedrockMethod = apiClass.getMethod("isBedrockPlayer", UUID.class);
                geyserAvailable = true;
            }
        } catch (Throwable ignored) {
            geyserAvailable = false;
        }
    }

    /**
     * Determina con precisión si el jugador está conectado desde Bedrock Edition.
     */
    public static boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        return isBedrock(player.getUniqueId(), player.getName());
    }

    /**
     * Determina con precisión si un UUID/Nombre corresponde a una sesión de Bedrock.
     */
    public static boolean isBedrock(UUID uuid, String name) {
        if (uuid == null) return false;

        // 1. Verificación determinista instantánea por UUID de Floodgate (MSB == 0)
        if (uuid.getMostSignificantBits() == 0L) {
            return true;
        }

        // 2. Verificación por Floodgate API (Reflexión rápida en caché)
        if (checkFloodgate(uuid)) {
            return true;
        }

        // 3. Verificación por Geyser API (Reflexión rápida en caché)
        if (checkGeyser(uuid)) {
            return true;
        }

        return false;
    }

    /**
     * Verifica si un nombre de usuario utiliza un prefijo reservado de Bedrock ('.' o '*').
     */
    public static boolean hasBedrockPrefix(String name) {
        if (name == null) return false;
        return name.startsWith(".") || name.startsWith("*");
    }

    private static boolean checkFloodgate(UUID uuid) {
        ensureInitialized();
        if (!floodgateAvailable || floodgateInstance == null || floodgateIsBedrockMethod == null) {
            return false;
        }
        try {
            Object result = floodgateIsBedrockMethod.invoke(floodgateInstance, uuid);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean checkGeyser(UUID uuid) {
        ensureInitialized();
        if (!geyserAvailable || geyserInstance == null || geyserIsBedrockMethod == null) {
            return false;
        }
        try {
            Object result = geyserIsBedrockMethod.invoke(geyserInstance, uuid);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
