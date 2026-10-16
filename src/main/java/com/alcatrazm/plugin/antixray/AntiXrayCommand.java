package com.alcatrazm.plugin.antixray;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class AntiXrayCommand implements CommandExecutor, TabCompleter {

    private final AlcatrazPlugin plugin;
    private final AntiXrayManager manager;

    public AntiXrayCommand(AlcatrazPlugin plugin, AntiXrayManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("alcatraz.xray.admin")) {
            sender.sendMessage(color("&cNo tienes permisos para usar el comando de Anti-Xray."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "alerts":
            case "alertas":
            case "toggle":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(color("&cSolo jugadores pueden alternar alertas."));
                    return true;
                }
                Player player = (Player) sender;
                boolean active = manager.toggleStaffAlerts(player);
                if (active) {
                    player.sendMessage(color("&8[&4Anti-Xray&8] &aAlertas activadas. Recibirás avisos de minería sospechosa."));
                } else {
                    player.sendMessage(color("&8[&4Anti-Xray&8] &cAlertas desactivadas temporalmente."));
                }
                break;

            case "check":
            case "inspeccionar":
            case "info":
                if (args.length < 2) {
                    sender.sendMessage(color("&cUso: /" + label + " check <jugador>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                UUID targetUUID = (target != null) ? target.getUniqueId() : null;
                if (targetUUID == null) {
                    // Try to find in recorded data
                    for (Map.Entry<UUID, AntiXrayManager.PlayerMiningData> entry : manager.getAllPlayerData().entrySet()) {
                        if (entry.getValue().getPlayerName().equalsIgnoreCase(args[1])) {
                            targetUUID = entry.getKey();
                            break;
                        }
                    }
                }

                if (targetUUID == null) {
                    sender.sendMessage(color("&cNo hay registros de minería para el jugador &e" + args[1] + "&c."));
                    return true;
                }

                AntiXrayManager.PlayerMiningData data = manager.getPlayerData(targetUUID);
                if (data == null) {
                    sender.sendMessage(color("&cNo hay datos disponibles para ese jugador."));
                    return true;
                }

                sender.sendMessage(color("&8&m----------------------------------------"));
                sender.sendMessage(color("&4&lAlcatraz Anti-Xray &8» &eEstadísticas de &f" + data.getPlayerName()));
                sender.sendMessage(color("&7Ventana activa (2 min): &b" + data.getRecentOreCount() + " minerales &7| &f" + data.getRecentStoneCount() + " rocas"));
                sender.sendMessage(color("&7Ratio reciente: &e" + String.format(Locale.US, "%.2f", data.calculateRatio()) + "%"));
                sender.sendMessage(color("&7Total rocas picadas: &f" + data.getTotalStonesMined()));
                sender.sendMessage(color("&7Historial total de minerales:"));
                for (Map.Entry<Material, Integer> entry : data.getTotalOresMined().entrySet()) {
                    sender.sendMessage(color("  &8▪ &b" + entry.getKey().name() + ": &e" + entry.getValue()));
                }
                sender.sendMessage(color("&8&m----------------------------------------"));
                break;

            case "clear":
            case "reset":
                if (args.length < 2) {
                    sender.sendMessage(color("&cUso: /" + label + " clear <jugador>"));
                    return true;
                }
                Player pTarget = Bukkit.getPlayer(args[1]);
                if (pTarget != null) {
                    manager.clearPlayerData(pTarget.getUniqueId());
                    sender.sendMessage(color("&aRegistros de Anti-Xray reiniciados para &e" + pTarget.getName() + "&a."));
                } else {
                    sender.sendMessage(color("&cJugador no encontrado."));
                }
                break;

            case "reload":
                manager.loadConfig();
                sender.sendMessage(color("&aMódulo Anti-Xray recargado correctamente desde config.yml."));
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&8&m----------------------------------------"));
        sender.sendMessage(color("&4&lAlcatraz Anti-Xray &8» &fComandos"));
        sender.sendMessage(color("&e/xray alerts &8- &7Activa o desactiva alertas de staff."));
        sender.sendMessage(color("&e/xray check <jugador> &8- &7Consulta ratio y datos de un jugador."));
        sender.sendMessage(color("&e/xray clear <jugador> &8- &7Limpia el historial de un jugador."));
        sender.sendMessage(color("&e/xray reload &8- &7Recarga la configuración del módulo."));
        sender.sendMessage(color("&8&m----------------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("alcatraz.xray.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("alerts", "check", "clear", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("clear"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
