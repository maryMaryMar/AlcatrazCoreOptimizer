package com.alcatrazm.plugin.cosmetics;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.announcer.AutoAnnouncer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CosmeticsCommand implements CommandExecutor, TabCompleter {

    private final AlcatrazPlugin plugin;
    private final CosmeticsManager manager;

    public CosmeticsCommand(AlcatrazPlugin plugin, CosmeticsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(AutoAnnouncer.colorize("&cEste comando solo puede ser ejecutado por jugadores en el servidor."));
                return true;
            }
            if (!sender.hasPermission("alcatraz.cosmetics.use")) {
                sender.sendMessage(AutoAnnouncer.colorize("&cNo tienes permiso para acceder al menú de cosméticos."));
                return true;
            }
            CosmeticsGUI.open(player, manager);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("clear") || sub.equals("remove") || sub.equals("off") || sub.equals("quitar")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(AutoAnnouncer.colorize("&cEste comando solo es para jugadores."));
                return true;
            }
            manager.removeCosmetic(player, true);
            return true;
        }

        if (sub.equals("equip") || sub.equals("poner")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(AutoAnnouncer.colorize("&cEste comando solo es para jugadores."));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(AutoAnnouncer.colorize("&cUso: /" + label + " equip <blaze_swords|tenebris_halo>"));
                return true;
            }
            CosmeticType type = CosmeticType.fromString(args[1]);
            if (type == null) {
                sender.sendMessage(AutoAnnouncer.colorize("&cCosmético no encontrado. Opciones: &eblaze_swords&c, &etenebris_halo"));
                return true;
            }
            if (!manager.hasPermission(player, type)) {
                sender.sendMessage(AutoAnnouncer.colorize("&cNo tienes permiso para equipar este cosmético: " + type.getDisplayName()));
                return true;
            }
            manager.equipCosmetic(player, type);
            return true;
        }

        if (sub.equals("give") || sub.equals("dar")) {
            if (!sender.hasPermission("alcatraz.cosmetics.admin")) {
                sender.sendMessage(AutoAnnouncer.colorize("&cNo tienes permiso para entregar cosméticos."));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(AutoAnnouncer.colorize("&cUso: /" + label + " give <jugador> <blaze_swords|tenebris_halo>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(AutoAnnouncer.colorize("&cEl jugador " + args[1] + " no está conectado."));
                return true;
            }
            CosmeticType type = CosmeticType.fromString(args[2]);
            if (type == null) {
                sender.sendMessage(AutoAnnouncer.colorize("&cCosmético no válido. Opciones: &eblaze_swords&c, &etenebris_halo"));
                return true;
            }

            manager.equipCosmetic(target, type);
            sender.sendMessage(AutoAnnouncer.colorize("&aHas equipado el cosmético " + type.getDisplayName() + " a &e" + target.getName()));
            return true;
        }

        sender.sendMessage(AutoAnnouncer.colorize("&8[&bCosméticos&8] &7Comandos disponibles:"));
        sender.sendMessage(AutoAnnouncer.colorize("&e/" + label + " &7- Abre el menú interactivo"));
        sender.sendMessage(AutoAnnouncer.colorize("&e/" + label + " equip <tipo> &7- Equipa un cosmético"));
        sender.sendMessage(AutoAnnouncer.colorize("&e/" + label + " clear &7- Desequipa tu cosmético actual"));
        if (sender.hasPermission("alcatraz.cosmetics.admin")) {
            sender.sendMessage(AutoAnnouncer.colorize("&e/" + label + " give <jugador> <tipo> &7- Otorga y equipa cosmético (Vouchers)"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("menu", "equip", "clear"));
            if (sender.hasPermission("alcatraz.cosmetics.admin")) {
                subs.add("give");
            }
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("equip") || args[0].equalsIgnoreCase("give"))) {
            if (args[0].equalsIgnoreCase("equip")) {
                for (CosmeticType t : CosmeticType.values()) {
                    if (t.getId().startsWith(args[1].toLowerCase())) completions.add(t.getId());
                }
            } else {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(p.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (CosmeticType t : CosmeticType.values()) {
                if (t.getId().startsWith(args[2].toLowerCase())) completions.add(t.getId());
            }
        }
        return completions;
    }
}
