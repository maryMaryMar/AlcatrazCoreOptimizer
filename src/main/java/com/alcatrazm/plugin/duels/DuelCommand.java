package com.alcatrazm.plugin.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DuelCommand implements CommandExecutor, TabCompleter {

    private final DuelMenuManager manager;

    public DuelCommand(DuelMenuManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser ejecutado por jugadores.");
            return true;
        }

        if (args.length == 0) {
            manager.openModalityMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("accept") || sub.equals("deny") || sub.equals("cancel")) {
            player.performCommand("versus:duel " + String.join(" ", args));
            return true;
        }

        if (sub.equals("menu") || sub.equals("gui") || sub.equals("modos")) {
            manager.openModalityMenu(player);
            return true;
        }

        // Si pasaron un nombre de jugador directamente (/duel <jugador>), abrimos el menú de modalidades
        Player target = Bukkit.getPlayer(args[0]);
        if (target != null && target.isOnline() && !target.getUniqueId().equals(player.getUniqueId())) {
            manager.openModalityMenu(player);
            return true;
        }

        player.performCommand("versus:duel " + String.join(" ", args));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = Arrays.asList("accept", "deny", "cancel", "menu");
            for (String s : subs) {
                if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(s);
                }
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player pl && p.getUniqueId().equals(pl.getUniqueId())) continue;
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}
