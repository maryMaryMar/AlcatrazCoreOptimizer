package com.alcatrazm.plugin.autorestart;

import com.alcatrazm.plugin.AlcatrazPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Expansión de PlaceholderAPI compatible con los placeholders de UltimateAutoRestart.
 */
public class AutoRestartPlaceholderExpansion extends PlaceholderExpansion {

    private final AlcatrazPlugin plugin;
    private final AutoRestartManager manager;

    public AutoRestartPlaceholderExpansion(AlcatrazPlugin plugin, AutoRestartManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @NotNull String getAuthor() {
        return "AlcatrazM";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ultimateautorestart";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        return switch (params.toLowerCase()) {
            case "seconds" -> Integer.toString(Math.max(0, manager.getRemainingSeconds()));
            case "formatted" -> manager.formatSeconds(manager.getRemainingSeconds());
            case "hhmmss" -> manager.formatHHMMSS(manager.getRemainingSeconds());
            case "last_seconds" -> Integer.toString(manager.getAfterRebootSeconds());
            case "last_formatted" -> manager.formatSeconds(manager.getAfterRebootSeconds());
            case "last_hhmmss" -> manager.formatHHMMSS(manager.getAfterRebootSeconds());
            default -> null;
        };
    }
}
