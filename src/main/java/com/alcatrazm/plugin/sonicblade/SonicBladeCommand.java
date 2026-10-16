package com.alcatrazm.plugin.sonicblade;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class SonicBladeCommand implements CommandExecutor, TabCompleter {

    private final AlcatrazPlugin plugin;
    private final SonicBladeManager bladeManager;

    public SonicBladeCommand(AlcatrazPlugin plugin, SonicBladeManager bladeManager) {
        this.plugin = plugin;
        this.bladeManager = bladeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sonicblade.admin")) {
            sender.sendMessage(bladeManager.getMessage("sin-permiso", "&cNo tienes permisos para ejecutar este comando."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(bladeManager.getMessage("recargado", "&a¡Configuración recargada con éxito!"));
            return true;
        }

        Player target;
        int amount = 1;

        if (args.length == 0) {
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage(bladeManager.getMessage("solo-jugadores", "&cDebes especificar un jugador desde la consola: /" + label + " <jugador> [cantidad]"));
                return true;
            }
            target = playerSender;
        } else if (isInteger(args[0])) {
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage(bladeManager.getMessage("solo-jugadores", "&cDebes especificar un jugador desde la consola: /" + label + " <jugador> [cantidad]"));
                return true;
            }
            target = playerSender;
            amount = parseAmount(args[0], 1);
        } else {
            target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(bladeManager.getMessage("jugador-no-encontrado", "&cJugador no encontrado o desconectado."));
                return true;
            }
            if (args.length >= 2) {
                amount = parseAmount(args[1], 1);
            }
        }

        giveBlade(target, amount);
        target.sendMessage(bladeManager.getMessage("recibida", "&aHas recibido el &b&lGrito del Centinela&a."));

        if (sender != target) {
            String msgEntregada = plugin.getConfig().getString("mensajes-hojasonica.entregada", "&aLe has entregado la espada a &e%jugador%&a.")
                    .replace("%jugador%", target.getName())
                    .replace("%amount%", String.valueOf(amount));
            sender.sendMessage(bladeManager.color(plugin.getConfig().getString("mensajes-hojasonica.prefijo", "&8[&bSonicBlade&8] ") + msgEntregada));
        }

        return true;
    }

    private void giveBlade(Player player, int amount) {
        ItemStack template = bladeManager.createSonicBlade();
        for (int i = 0; i < amount; i++) {
            ItemStack item = template.clone();
            item.setAmount(1);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }
        try {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        } catch (Exception ignored) {}
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

    private int parseAmount(String s, int def) {
        try {
            int val = Integer.parseInt(s);
            return Math.max(1, Math.min(val, 640));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sonicblade.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if ("reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
            for (String num : Arrays.asList("1", "2", "5", "10")) {
                if (num.startsWith(args[0])) {
                    completions.add(num);
                }
            }
            return completions;
        }

        if (args.length == 2 && !isInteger(args[0])) {
            return Arrays.asList("1", "2", "5", "10");
        }

        return Collections.emptyList();
    }
}
