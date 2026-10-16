package com.alcatrazm.plugin.newbieprotection;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NewbieProtectionCommand implements CommandExecutor, TabCompleter {

    private final AlcatrazPlugin plugin;
    private final NewbieProtectionManager manager;

    public NewbieProtectionCommand(AlcatrazPlugin plugin, NewbieProtectionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cEste comando solo puede ser usado por jugadores dentro del juego."));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("info")) {
            if (manager.hasProtection(player.getUniqueId())) {
                String remaining = manager.getRemainingFormatted(player.getUniqueId());
                player.sendMessage(color("&8[&aProtección Novato&8] &aTienes protección PvP activa: &e" + remaining + " restantes&a."));
                player.sendMessage(color("&7Si quieres desactivarla para poder pelear, escribe: &e/pvp on"));
            } else {
                player.sendMessage(color("&8[&aProtección Novato&8] &7Tu protección de novato no está activa (PvP habilitado)."));
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("on") || sub.equals("enable") || sub.equals("desactivar") || sub.equals("off")) {
            if (!manager.hasProtection(player.getUniqueId())) {
                player.sendMessage(color("&8[&aProtección Novato&8] &eTu protección ya expiró o está desactivada."));
                return true;
            }

            manager.removeProtection(player.getUniqueId());
            player.sendMessage(color("&8[&cProtección Novato&8] &c¡Has desactivado tu protección de novato! Ahora puedes pelear y recibir daño de otros jugadores."));
            return true;
        }

        player.sendMessage(color("&eUso: /pvp [status|on]"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("status", "on");
        }
        return Collections.emptyList();
    }
}
