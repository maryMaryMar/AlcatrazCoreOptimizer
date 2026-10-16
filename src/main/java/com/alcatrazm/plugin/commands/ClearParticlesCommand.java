package com.alcatrazm.plugin.commands;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ClearParticlesCommand implements CommandExecutor, TabCompleter {

    private final AlcatrazPlugin plugin;

    public ClearParticlesCommand(AlcatrazPlugin plugin) {
        this.plugin = plugin;
    }

    private String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("alcatraz.limpiarparticulas")) {
            sender.sendMessage(color("&cNo tienes permisos para usar este comando."));
            return true;
        }

        int removedCount = 0;

        if (sender instanceof Player player && args.length > 0 && isInteger(args[0])) {
            // Limpiar en un radio específico alrededor del jugador
            int radius = Integer.parseInt(args[0]);
            double radiusSq = radius * radius;

            for (Entity entity : player.getWorld().getEntities()) {
                if (entity instanceof AreaEffectCloud) {
                    if (entity.getLocation().distanceSquared(player.getLocation()) <= radiusSq) {
                        entity.remove();
                        removedCount++;
                    }
                }
            }
            player.sendMessage(color("&8[&bAlcatraz&cPlugin&8] &a¡Se han eliminado &e" + removedCount + " &anubes de partículas en un radio de &e" + radius + " &abloques!"));
            return true;
        }

        // Limpiar en todos los mundos cargados del servidor
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof AreaEffectCloud) {
                    entity.remove();
                    removedCount++;
                }
            }
        }

        String prefix = color(plugin.getConfig().getString("messages-particulas.prefix", "&8[&bAlcatraz&cPlugin&8] "));
        String msg = color(plugin.getConfig().getString("messages-particulas.cleared", "&a¡Se han eliminado &e%count% &anubes de partículas/efectos del servidor!")
                .replace("%count%", String.valueOf(removedCount)));

        sender.sendMessage(prefix + msg);
        return true;
    }

    private boolean isInteger(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("10", "25", "50", "100", "all");
        }
        return Collections.emptyList();
    }
}
