package com.alcatrazm.plugin.mounts;

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

public class MountCommand implements CommandExecutor, TabCompleter {

    private final MountManager manager;

    public MountCommand(MountManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(color("&cSolo los jugadores pueden abrir el menú de monturas."));
                return true;
            }
            if (!manager.hasAnyMountPermission(player)) {
                player.sendMessage(color("&8[&6Monturas&8] &cNo tienes acceso al establo ni posees monturas desbloqueadas."));
                player.sendMessage(color("&7¡Adquiere monturas en la tienda del servidor para utilizarlas!"));
                return true;
            }
            MountGUI.open(player, manager);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "spawn", "summon", "invocar" -> {
                if (args.length < 2) {
                    sender.sendMessage(color("&cUso: /" + label + " spawn <tipo> [jugador]"));
                    return true;
                }
                MountType type = MountType.fromString(args[1]);
                if (type == null) {
                    sender.sendMessage(color("&cTipo de montura desconocido. Usa el menú o presiona TAB para ver las disponibles."));
                    return true;
                }

                Player target;
                if (args.length >= 3) {
                    if (!sender.hasPermission("alcatraz.mounts.admin")) {
                        sender.sendMessage(color("&cNo tienes permiso para invocar monturas a otros jugadores."));
                        return true;
                    }
                    target = Bukkit.getPlayer(args[2]);
                    if (target == null) {
                        sender.sendMessage(color("&cJugador no encontrado o desconectado."));
                        return true;
                    }
                } else {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(color("&cDebes especificar un jugador desde la consola."));
                        return true;
                    }
                    target = player;
                }

                manager.spawnMount(target, type);
                if (target != sender) {
                    sender.sendMessage(color("&aHas invocado un &e" + type.getDisplayName() + " &apara &e" + target.getName() + "&a."));
                }
                return true;
            }

            case "dismiss", "despedir", "guardar", "clear" -> {
                Player target;
                if (args.length >= 2) {
                    if (!sender.hasPermission("alcatraz.mounts.admin")) {
                        sender.sendMessage(color("&cNo tienes permiso para retirar monturas de otros jugadores."));
                        return true;
                    }
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(color("&cJugador no encontrado o desconectado."));
                        return true;
                    }
                } else {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(color("&cDebes especificar un jugador desde la consola."));
                        return true;
                    }
                    target = player;
                }

                if (!manager.hasActiveMount(target.getUniqueId())) {
                    sender.sendMessage(color("&cEl jugador no tiene ninguna montura activa."));
                    return true;
                }

                manager.dismissMount(target.getUniqueId(), true);
                if (target != sender) {
                    sender.sendMessage(color("&aHas retirado la montura de &e" + target.getName() + "&a."));
                }
                return true;
            }

            case "reload" -> {
                if (!sender.hasPermission("alcatraz.mounts.admin")) {
                    sender.sendMessage(color("&cNo tienes permiso para recargar el sistema de monturas."));
                    return true;
                }
                manager.reload();
                sender.sendMessage(color("&8[&6Monturas&8] &aConfiguración de monturas recargada exitosamente."));
                return true;
            }

            case "help", "ayuda" -> {
                sender.sendMessage(color("&8&m----------------------------------------"));
                sender.sendMessage(color("&6&lSISTEMA DE MONTURAS - ALCATRAZ CORE"));
                sender.sendMessage(color("&e/" + label + " &7- Abre el menú interactivo del establo."));
                sender.sendMessage(color("&e/" + label + " spawn <tipo> [jugador] &7- Invoca una montura específica."));
                sender.sendMessage(color("&e/" + label + " dismiss [jugador] &7- Guarda la montura activa."));
                sender.sendMessage(color("&e/" + label + " reload &7- Recarga la configuración del módulo."));
                sender.sendMessage(color("&8&m----------------------------------------"));
                return true;
            }

            default -> {
                sender.sendMessage(color("&cSubcomando no reconocido. Usa &e/" + label + " help"));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = Arrays.asList("gui", "spawn", "dismiss", "reload", "help");
            for (String sub : subs) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("summon"))) {
            for (MountType type : MountType.values()) {
                if (type.getId().startsWith(args[1].toLowerCase())) {
                    completions.add(type.getId());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("spawn")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("dismiss") || args[0].equalsIgnoreCase("guardar"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
