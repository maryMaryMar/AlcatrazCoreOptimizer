package com.alcatrazm.plugin.commandguard;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.TabCompleteEvent;

/**
 * Filtra la lista de comandos en '/', la tabulación de argumentos y la ejecución por rango/permisos.
 */
public final class CommandGuardListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final CommandGuardManager manager;

    public CommandGuardListener(AlcatrazPlugin plugin, CommandGuardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandSend(PlayerCommandSendEvent event) {
        if (!manager.isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (manager.hasBypass(player)) {
            return;
        }
        event.getCommands().removeIf(cmd -> !manager.isCommandVisible(player, cmd));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (!manager.isEnabled() || !event.isCommand()) {
            return;
        }
        if (!(event.getSender() instanceof Player player) || manager.hasBypass(player)) {
            return;
        }

        String buffer = event.getBuffer();
        if (buffer == null || buffer.isEmpty()) {
            return;
        }
        List<String> filtered = filterCompletions(player, buffer, event.getCompletions());
        if (filtered == null) {
            event.setCancelled(true);
            event.setCompletions(Collections.emptyList());
        } else if (filtered != event.getCompletions()) {
            event.setCompletions(filtered);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        if (!manager.isEnabled()) {
            return;
        }
        if (!(event.getSender() instanceof Player player) || manager.hasBypass(player)) {
            return;
        }

        String buffer = event.getBuffer();
        if (buffer == null || !buffer.startsWith("/")) {
            return;
        }
        List<String> filtered = filterCompletions(player, buffer, event.getCompletions());
        if (filtered == null) {
            event.setCancelled(true);
            event.setCompletions(Collections.emptyList());
        } else if (filtered != event.getCompletions()) {
            event.setCompletions(filtered);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!manager.isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (manager.hasBypass(player)) {
            return;
        }

        String msg = event.getMessage();
        if (msg == null || !msg.startsWith("/")) {
            return;
        }
        String withoutLeadingSlash = msg.substring(1);
        if (withoutLeadingSlash.isBlank()) {
            return;
        }

        String[] parts = withoutLeadingSlash.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return;
        }
        String cmdLabel = parts[0];
        String firstArg = parts.length > 1 ? parts[1] : null;

        if (!manager.canExecute(player, cmdLabel, firstArg)) {
            event.setCancelled(true);
            player.sendMessage(manager.getDeniedMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!manager.isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.updateCommands();
            }
        }, 2L);
    }

    /**
     * Filtra las sugerencias según si el jugador está escribiendo el comando raíz o sus argumentos.
     * Devuelve null si debe cancelarse toda la completación.
     */
    private List<String> filterCompletions(Player player, String buffer, List<String> current) {
        String withoutSlash = buffer.startsWith("/") ? buffer.substring(1) : buffer;
        int spaceIdx = withoutSlash.indexOf(' ');

        if (spaceIdx < 0) {
            if (current == null || current.isEmpty()) {
                return current;
            }
            List<String> allowed = new ArrayList<>(current.size());
            for (String candidate : current) {
                String normalized = CommandGuardManager.stripSingleLeadingSlash(candidate);
                if (manager.isCommandVisible(player, normalized)) {
                    allowed.add(candidate);
                }
            }
            return allowed;
        }

        String rootCmd = withoutSlash.substring(0, spaceIdx);
        String argsPart = withoutSlash.substring(spaceIdx + 1);
        int secondSpace = argsPart.indexOf(' ');

        if (secondSpace < 0) {
            if (!manager.canExecute(player, rootCmd, null)) {
                return null;
            }
            if (current == null || current.isEmpty() || !manager.hasSubcommandRules(rootCmd)) {
                return current;
            }
            List<String> allowed = new ArrayList<>(current.size());
            for (String subCandidate : current) {
                if (manager.isSubcommandAllowed(player, rootCmd, subCandidate)) {
                    allowed.add(subCandidate);
                }
            }
            return allowed;
        }

        String firstArg = argsPart.substring(0, secondSpace);
        if (!manager.canExecute(player, rootCmd, firstArg)) {
            return null;
        }
        return current;
    }
}
