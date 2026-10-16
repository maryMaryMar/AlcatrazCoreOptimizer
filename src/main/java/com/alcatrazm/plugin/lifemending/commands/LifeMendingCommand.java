package com.alcatrazm.plugin.lifemending.commands;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.lifemending.managers.LifeMendingManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class LifeMendingCommand implements CommandExecutor, TabCompleter {

    private final AlcatrazPlugin plugin;
    private final LifeMendingManager manager;

    public LifeMendingCommand(AlcatrazPlugin plugin, LifeMendingManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String getPrefix() {
        return color(plugin.getConfig().getString("messages-lifemending.prefix", "&8[&4Life&cMending&8] &r"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = getPrefix();

        if (!sender.hasPermission("lifemending.admin")) {
            String noPerm = plugin.getConfig().getString("messages-lifemending.no-permission", "&cNo tienes permisos para usar este comando.");
            sender.sendMessage(prefix + color(noPerm));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage(prefix + color(plugin.getConfig().getString("messages-lifemending.only-players", "&cDebes especificar un jugador: /" + label + " <jugador> [cantidad]")));
                return true;
            }
            giveLifeMendingBook(sender, playerSender, 1, prefix);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help":
            case "?":
                sendHelp(sender, label);
                return true;

            case "reload":
                plugin.reloadConfig();
                String reloadMsg = plugin.getConfig().getString("messages-lifemending.reload-success", "&a¡Configuración recargada correctamente!");
                sender.sendMessage(prefix + color(reloadMsg));
                return true;

            case "book":
            case "libro":
            case "b":
            case "l":
            case "give":
                handleGiveSubcommand(sender, prefix, args, label);
                return true;

            case "apply":
            case "aplicar":
            case "enchant":
                handleApplySubcommand(sender, prefix, args, label);
                return true;

            default:
                // Check if args[0] is an integer (e.g. /lm 5)
                if (isInteger(args[0])) {
                    if (!(sender instanceof Player playerSender)) {
                        sender.sendMessage(prefix + color(plugin.getConfig().getString("messages-lifemending.only-players", "&cDebes especificar un jugador desde la consola: /" + label + " <jugador> [cantidad]")));
                        return true;
                    }
                    int amount = parseAmount(args[0], 1);
                    giveLifeMendingBook(sender, playerSender, amount, prefix);
                    return true;
                }

                // Check if args[0] is a player name (e.g. /lm Player 2)
                Player target = Bukkit.getPlayer(args[0]);
                if (target != null && target.isOnline()) {
                    int amount = args.length >= 2 ? parseAmount(args[1], 1) : 1;
                    giveLifeMendingBook(sender, target, amount, prefix);
                    return true;
                }

                sendHelp(sender, label);
                return true;
        }
    }

    private void handleGiveSubcommand(CommandSender sender, String prefix, String[] args, String label) {
        Player target;
        int amount = 1;

        if (args.length == 1) {
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage(prefix + color(plugin.getConfig().getString("messages-lifemending.only-players", "&cDebes especificar un jugador: /" + label + " give <jugador> [cantidad]")));
                return;
            }
            target = playerSender;
        } else if (isInteger(args[1])) {
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage(prefix + color(plugin.getConfig().getString("messages-lifemending.only-players", "&cDebes especificar un jugador: /" + label + " give <jugador> [cantidad]")));
                return;
            }
            target = playerSender;
            amount = parseAmount(args[1], 1);
        } else {
            target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(prefix + color(plugin.getConfig().getString("messages-lifemending.player-not-found", "&cEl jugador no fue encontrado o está desconectado.")));
                return;
            }
            if (args.length >= 3) {
                amount = parseAmount(args[2], 1);
            }
        }

        giveLifeMendingBook(sender, target, amount, prefix);
    }

    private void handleApplySubcommand(CommandSender sender, String prefix, String[] args, String label) {
        Player target;
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(prefix + color(plugin.getConfig().getString("messages-lifemending.player-not-found", "&cEl jugador no fue encontrado o está desconectado.")));
                return;
            }
        } else {
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage(prefix + color("&cDebes especificar un jugador: /" + label + " apply <jugador>"));
                return;
            }
            target = playerSender;
        }

        ItemStack item = target.getInventory().getItemInMainHand();
        if (!manager.isDamageableItem(item)) {
            sender.sendMessage(prefix + color("&cEl objeto en la mano principal de &e" + target.getName() + " &cno puede recibir Life Mending."));
            return;
        }

        if (manager.hasLifeMending(item)) {
            sender.sendMessage(prefix + color("&eEl objeto ya tiene el encantamiento Life Mending."));
            return;
        }

        ItemStack applied = manager.applyLifeMending(item);
        if (applied != null) {
            target.getInventory().setItemInMainHand(applied);
            target.playSound(target.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            target.sendMessage(prefix + color("&a¡Se ha imbuido el encantamiento &c&lLife Mending &aen tu objeto en mano!"));
            if (sender != target) {
                sender.sendMessage(prefix + color("&aHas aplicado &c&lLife Mending &aal objeto en mano de &e" + target.getName() + "&a."));
            }
        }
    }

    private void giveLifeMendingBook(CommandSender sender, Player target, int amount, String prefix) {
        ItemStack book = manager.createLifeMendingBook();
        deliverItem(target, book, amount);

        String targetMsg = color(plugin.getConfig().getString("messages-lifemending.book-given", "&aHas recibido &e%amount%x &cLibro Encantado de Life Mending&a.")
                .replace("%amount%", String.valueOf(amount))
                .replace("%player%", target.getName()));
        target.sendMessage(prefix + targetMsg);

        if (sender != target) {
            String senderMsg = color(plugin.getConfig().getString("messages-lifemending.book-given-target", "&aHas entregado &e%amount%x &clibro(s) a &e%player%&a.")
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%player%", target.getName()));
            sender.sendMessage(prefix + senderMsg);
        }
    }

    private void deliverItem(Player player, ItemStack itemTemplate, int amount) {
        boolean dropped = false;
        int maxStack = itemTemplate.getMaxStackSize();

        if (maxStack <= 1) {
            for (int i = 0; i < amount; i++) {
                ItemStack single = itemTemplate.clone();
                single.setAmount(1);
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(single);
                if (!leftover.isEmpty()) {
                    dropped = true;
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
            }
        } else {
            int remaining = amount;
            while (remaining > 0) {
                int count = Math.min(remaining, maxStack);
                ItemStack stack = itemTemplate.clone();
                stack.setAmount(count);
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                if (!leftover.isEmpty()) {
                    dropped = true;
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
                remaining -= count;
            }
        }

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        } catch (Exception ignored) {}

        if (dropped) {
            String invFullMsg = plugin.getConfig().getString("messages-lifemending.inventory-full", "&e¡Tu inventario estaba lleno! Los libros se soltaron en el suelo.");
            player.sendMessage(getPrefix() + color(invFullMsg));
        }
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

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(color("&8&m----------------------------------------"));
        sender.sendMessage(color("&4&lAlcatrazPlugin &7- &cMódulo Life Mending"));
        sender.sendMessage(color("&e/" + label + " [cantidad] &7- Te entrega libros de Life Mending."));
        sender.sendMessage(color("&e/" + label + " <jugador> [cant] &7- Entrega libros a un jugador."));
        sender.sendMessage(color("&e/" + label + " apply [jugador] &7- Aplica Life Mending al objeto en mano."));
        sender.sendMessage(color("&e/" + label + " reload &7- Recarga la configuración."));
        sender.sendMessage(color("&8&m----------------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("lifemending.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subs = Arrays.asList("give", "book", "libro", "apply", "reload", "help", "1", "2", "5", "10", "64");
            List<String> completions = new ArrayList<>();
            for (String sub : subs) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("book") || args[0].equalsIgnoreCase("libro") || args[0].equalsIgnoreCase("apply")) {
                List<String> completions = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
                for (String num : Arrays.asList("1", "2", "5", "10", "64")) {
                    if (num.startsWith(args[1])) completions.add(num);
                }
                return completions;
            } else if (!isInteger(args[0])) {
                return Arrays.asList("1", "2", "5", "10", "64");
            }
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("book") || args[0].equalsIgnoreCase("libro"))) {
            return Arrays.asList("1", "2", "5", "10", "64");
        }

        return Collections.emptyList();
    }
}
