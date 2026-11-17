package com.alcatrazm.plugin.commandguard;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.module.AlcatrazModule;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Módulo nativo de AlcatrazCoreOptimizer que restringe la visibilidad en '/' y ejecución por rango/permisos reales.
 */
public final class CommandGuardModule implements AlcatrazModule {

    private final AlcatrazPlugin plugin;
    private final CommandGuardManager manager;
    private CommandGuardListener listener;
    private Object lpSubscription;
    private boolean active = false;

    public CommandGuardModule(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.manager = new CommandGuardManager(plugin);
    }

    @Override
    public String getId() {
        return "command-guard";
    }

    @Override
    public String getDisplayName() {
        return "Command Guard (Visibilidad por Rango y Permisos)";
    }

    @Override
    public boolean isEnabled() {
        return manager.isEnabled();
    }

    @Override
    public void enable() {
        if (active) {
            return;
        }
        manager.reload();
        this.listener = new CommandGuardListener(plugin, manager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        this.active = true;

        // Capturar también comandos de plugins habilitados después de AlcatrazCoreOptimizer
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!active) {
                return;
            }
            manager.refreshCommandMapSnapshot();
            updateAllOnlinePlayers();
        });

        hookLuckPermsEvents();
        updateAllOnlinePlayers();
    }

    @Override
    public void disable() {
        this.active = false;
        unhookLuckPermsEvents();
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        if (plugin.isEnabled()) {
            updateAllOnlinePlayers();
        }
    }

    public boolean isActive() {
        return active;
    }

    public CommandGuardManager getManager() {
        return manager;
    }

    public void updateAllOnlinePlayers() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::updateAllOnlinePlayers);
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.updateCommands();
        }
    }

    /**
     * Refresca automáticamente el árbol '/' de un jugador cuando LuckPerms recalcula sus permisos (rangos, vouchers, etc.).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void hookLuckPermsEvents() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            return;
        }
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object lpApi = providerClass.getMethod("get").invoke(null);
            Object eventBus = lpApi.getClass().getMethod("getEventBus").invoke(lpApi);
            Class<?> recalcEventClass = Class.forName("net.luckperms.api.event.user.UserDataRecalculateEvent");

            Method subscribeMethod = eventBus.getClass().getMethod("subscribe", Object.class, Class.class, Consumer.class);
            Consumer<Object> handler = event -> {
                if (!active) {
                    return;
                }
                try {
                    Object user = event.getClass().getMethod("getUser").invoke(event);
                    UUID uuid = (UUID) user.getClass().getMethod("getUniqueId").invoke(user);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!active) {
                            return;
                        }
                        Player target = Bukkit.getPlayer(uuid);
                        if (target != null && target.isOnline()) {
                            target.updateCommands();
                        }
                    });
                } catch (Throwable ignored) {
                }
            };
            this.lpSubscription = subscribeMethod.invoke(eventBus, plugin, recalcEventClass, handler);
        } catch (Throwable ex) {
            plugin.getLogger().fine("[CommandGuard] Hook opcional de LuckPerms no aplicado: " + ex.getMessage());
        }
    }

    private void unhookLuckPermsEvents() {
        if (lpSubscription != null) {
            try {
                lpSubscription.getClass().getMethod("close").invoke(lpSubscription);
            } catch (Throwable ignored) {
            }
            lpSubscription = null;
        }
    }
}
