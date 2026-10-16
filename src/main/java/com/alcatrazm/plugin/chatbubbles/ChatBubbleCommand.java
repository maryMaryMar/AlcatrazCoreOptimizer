package com.alcatrazm.plugin.chatbubbles;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChatBubbleCommand implements CommandExecutor, TabCompleter {

    private final ChatBubbleManager manager;

    public ChatBubbleCommand(ChatBubbleManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("alcatraz.admin")) {
                sender.sendMessage(ChatColor.RED + "No tienes permiso para recargar los globos de texto.");
                return true;
            }
            manager.loadConfig();
            sender.sendMessage(ChatColor.GREEN + "[Alcatraz] Configuración de ChatBubbles recargada correctamente.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("toggle")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
                return true;
            }
            Player player = (Player) sender;
            boolean newState = manager.togglePlayer(player.getUniqueId());
            if (newState) {
                player.sendMessage(ChatColor.GREEN + "[Alcatraz] Globos de texto " + ChatColor.BOLD + "ACTIVADOS" + ChatColor.GREEN + " sobre tu cabeza.");
            } else {
                player.sendMessage(ChatColor.RED + "[Alcatraz] Globos de texto " + ChatColor.BOLD + "DESACTIVADOS" + ChatColor.RED + " sobre tu cabeza.");
            }
            return true;
        }

        // Ayuda por defecto o toggle directo si es jugador
        if (sender instanceof Player) {
            Player player = (Player) sender;
            boolean newState = manager.togglePlayer(player.getUniqueId());
            if (newState) {
                player.sendMessage(ChatColor.GREEN + "[Alcatraz] Globos de texto " + ChatColor.BOLD + "ACTIVADOS" + ChatColor.GREEN + ".");
            } else {
                player.sendMessage(ChatColor.RED + "[Alcatraz] Globos de texto " + ChatColor.BOLD + "DESACTIVADOS" + ChatColor.RED + ".");
            }
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Uso: /" + label + " [toggle|reload]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> sub = new ArrayList<>();
            sub.add("toggle");
            if (sender.hasPermission("alcatraz.admin")) {
                sub.add("reload");
            }
            return sub;
        }
        return Collections.emptyList();
    }
}
