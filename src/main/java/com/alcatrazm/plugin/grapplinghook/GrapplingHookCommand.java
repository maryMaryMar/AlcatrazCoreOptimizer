package com.alcatrazm.plugin.grapplinghook;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GrapplingHookCommand implements CommandExecutor, TabCompleter {

    private final AlcatrazPlugin plugin;
    private final GrapplingHookManager manager;

    public GrapplingHookCommand(AlcatrazPlugin plugin, GrapplingHookManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("alcatraz.grapplinghook.admin")) {
            sender.sendMessage(color("&cNo tienes permisos para usar este comando."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "give":
            case "dar":
                if (args.length < 2) {
                    sender.sendMessage(color("&cUso: /" + label + " give <jugador> [usos]"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(color("&cEl jugador no está conectado."));
                    return true;
                }

                int uses = 50;
                if (args.length >= 3) {
                    try {
                        uses = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(color("&cCantidad de usos inválida."));
                        return true;
                    }
                }

                ItemStack hook = manager.createGrapplingHook(uses);
                target.getInventory().addItem(hook);
                target.sendMessage(color("&aHas recibido un &b&lGancho de Agarre Alcatraz&a."));
                sender.sendMessage(color("&aEntregado Gancho de Agarre a &e" + target.getName() + " &a(Usos: " + (uses > 0 ? uses : "Infinito") + ")."));
                break;

            case "reload":
                manager.loadConfig();
                sender.sendMessage(color("&aConfiguración de Gancho de Agarre recargada correctamente."));
                break;

            default:
                sendHelp(sender, label);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(color("&8&m----------------------------------------"));
        sender.sendMessage(color("&b&lAlcatraz Grappling Hook &8» &fComandos"));
        sender.sendMessage(color("&e/" + label + " give <jugador> [usos] &8- &7Entrega un gancho de agarre."));
        sender.sendMessage(color("&e/" + label + " reload &8- &7Recarga la configuración del módulo."));
        sender.sendMessage(color("&8&m----------------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("alcatraz.grapplinghook.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("give", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return Arrays.asList("25", "50", "100", "-1");
        }
        return Collections.emptyList();
    }
}
