package com.alcatrazm.plugin.mobcatcher;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MobCatcherCommand implements CommandExecutor, TabCompleter {

    private final MobCatcherManager manager;

    public MobCatcherCommand(MobCatcherManager manager) {
        this.manager = manager;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("alcatraz.admin") && !sender.hasPermission("alcatraz.mobcatcher.admin")) {
            sender.sendMessage(color("&cNo tienes permisos para usar este comando."));
            return true;
        }

        Player target;
        int amount = 1;

        if (args.length >= 1) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                if (sender instanceof Player p && isInteger(args[0])) {
                    target = p;
                    amount = Integer.parseInt(args[0]);
                } else {
                    sender.sendMessage(color("&cJugador no encontrado: " + args[0]));
                    return true;
                }
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(color("&cUso desde consola: /" + label + " <jugador> [cantidad]"));
            return true;
        }

        if (args.length >= 2 && isInteger(args[1])) {
            amount = Integer.parseInt(args[1]);
        }

        ItemStack catcher = manager.createEmptyCatcher(amount);
        if (target.getInventory().addItem(catcher).size() > 0) {
            target.getWorld().dropItemNaturally(target.getLocation(), catcher);
        }

        sender.sendMessage(color("&8[&dEsfera Cuántica&8] &aEntregadas &e" + amount + "x &dEsfera(s) de Contención &aa &e" + target.getName() + "&a."));
        target.sendMessage(color("&8[&dEsfera Cuántica&8] &aHas recibido &e" + amount + "x &dEsfera(s) de Contención Alcatraz&a."));
        return true;
    }

    private boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("alcatraz.admin")) return Collections.emptyList();
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                list.add(p.getName());
            }
            list.addAll(Arrays.asList("1", "2", "5", "10"));
            return list;
        }
        if (args.length == 2) {
            return Arrays.asList("1", "2", "5", "10", "64");
        }
        return Collections.emptyList();
    }
}
