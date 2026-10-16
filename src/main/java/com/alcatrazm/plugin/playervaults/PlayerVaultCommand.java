package com.alcatrazm.plugin.playervaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PlayerVaultCommand implements CommandExecutor, TabCompleter {

    private final PlayerVaultManager vaultManager;

    public PlayerVaultCommand(PlayerVaultManager vaultManager) {
        this.vaultManager = vaultManager;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cEste comando solo puede ser ejecutado por jugadores administradores."));
            return true;
        }

        // Permiso exclusivo para administradores
        if (!player.hasPermission("alcatraz.pv.admin") && !player.hasPermission("alcatraz.admin")) {
            player.sendMessage(color("&cLas bóvedas virtuales infinitas son de uso exclusivo para Administradores."));
            return true;
        }

        if (args.length == 0) {
            // Abrir por defecto la bóveda 1
            vaultManager.openVault(player, player.getUniqueId(), player.getName(), 1);
            player.sendMessage(color("&8[&6Bóvedas Admin&8] &aAbriendo tu bóveda infinita &e#1&a."));
            return true;
        }

        // Caso 1: /pv <numero>
        if (args.length == 1) {
            int vaultNumber = parseVaultNumber(args[0]);
            if (vaultNumber <= 0) {
                player.sendMessage(color("&cEspecifica un número de bóveda válido (ejemplo: /pv 1, /pv 2, /pv 10...)."));
                return true;
            }
            vaultManager.openVault(player, player.getUniqueId(), player.getName(), vaultNumber);
            player.sendMessage(color("&8[&6Bóvedas Admin&8] &aAbriendo tu bóveda infinita &e#" + vaultNumber + "&a."));
            return true;
        }

        // Caso 2: /pv <jugador> <numero>
        String targetName = args[0];
        int vaultNumber = parseVaultNumber(args[1]);
        if (vaultNumber <= 0) {
            player.sendMessage(color("&cEspecifica un número de bóveda válido: /" + label + " <jugador> <número>"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getUniqueId() == null) {
            player.sendMessage(color("&cJugador no encontrado: " + targetName));
            return true;
        }

        vaultManager.openVault(player, target.getUniqueId(), target.getName() != null ? target.getName() : targetName, vaultNumber);
        player.sendMessage(color("&8[&6Bóvedas Admin&8] &aAbriendo la bóveda &e#" + vaultNumber + " &ade &e" + (target.getName() != null ? target.getName() : targetName) + "&a."));
        return true;
    }

    private int parseVaultNumber(String s) {
        try {
            int n = Integer.parseInt(s);
            return n > 0 ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("alcatraz.pv.admin") && !sender.hasPermission("alcatraz.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> list = new ArrayList<>(Arrays.asList("1", "2", "3", "4", "5", "10", "20", "100"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                list.add(p.getName());
            }
            List<String> results = new ArrayList<>();
            for (String item : list) {
                if (item.toLowerCase().startsWith(args[0].toLowerCase())) {
                    results.add(item);
                }
            }
            return results;
        }

        if (args.length == 2) {
            List<String> list = Arrays.asList("1", "2", "3", "4", "5", "10");
            List<String> results = new ArrayList<>();
            for (String item : list) {
                if (item.startsWith(args[1])) {
                    results.add(item);
                }
            }
            return results;
        }

        return Collections.emptyList();
    }
}
