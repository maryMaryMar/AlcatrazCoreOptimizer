package com.alcatrazm.plugin.netheriteelytra.commands;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.netheriteelytra.managers.ItemManager;
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

public class NetheriteElytraCommand implements CommandExecutor, TabCompleter {

    private final AlcatrazPlugin plugin;
    private final ItemManager itemManager;

    public NetheriteElytraCommand(AlcatrazPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    private String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String getPrefix() {
        return color(plugin.getConfig().getString("messages-elytra.prefix", "&8[&5Netherite&dElytra&8] &r"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = getPrefix();

        if (!sender.hasPermission("netheriteelytra.admin")) {
            String noPerm = plugin.getConfig().getString("messages-elytra.no-permission", "&cNo tienes permisos para usar este comando.");
            sender.sendMessage(prefix + color(noPerm));
            return true;
        }

        String lowerLabel = label.toLowerCase();

        // Direct commands for chestplate: /pechera, /elytrachest, etc.
        if (isChestplateAlias(lowerLabel)) {
            handleDirectItemCommand(sender, prefix, args, true, label);
            return true;
        }

        // Direct commands for book: /libro, /elytrabook, etc.
        if (isBookAlias(lowerLabel)) {
            handleDirectItemCommand(sender, prefix, args, false, label);
            return true;
        }

        // Main command: /netheriteelytra, /ne, etc.
        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload":
                plugin.reloadConfig();
                String reloadMsg = plugin.getConfig().getString("messages-elytra.reload-success", "&a¡Configuración recargada correctamente!");
                sender.sendMessage(prefix + color(reloadMsg));
                return true;

            case "give":
                handleGiveSubcommand(sender, prefix, args, label);
                return true;

            case "book":
            case "libro":
            case "b":
            case "l":
                handleGiveItem(sender, prefix, args, 1, false, label);
                return true;

            case "chestplate":
            case "pechera":
            case "chest":
            case "c":
            case "armor":
            case "elitra":
            case "elytra":
                handleGiveItem(sender, prefix, args, 1, true, label);
                return true;

            default:
                // Check if args[0] is a player name
                Player possiblePlayer = Bukkit.getPlayer(args[0]);
                if (possiblePlayer != null && possiblePlayer.isOnline()) {
                    if (args.length >= 2 && isBookKeyword(args[1].toLowerCase())) {
                        int amount = args.length >= 3 ? parseAmount(args[2], 1) : 1;
                        giveElytraBook(sender, possiblePlayer, amount, prefix);
                        return true;
                    } else {
                        int amount = (args.length >= 2 && !isChestKeyword(args[1].toLowerCase())) ? parseAmount(args[1], 1) : (args.length >= 3 ? parseAmount(args[2], 1) : 1);
                        giveElytraChestplate(sender, possiblePlayer, amount, prefix);
                        return true;
                    }
                }
                sendHelp(sender, label);
                return true;
        }
    }

    private boolean isChestplateAlias(String label) {
        return label.equals("elytrachest") || label.equals("pechera") || label.equals("elytrachestplate")
                || label.equals("pecheraelitra") || label.equals("pecheraelytra") || label.equals("givepechera");
    }

    private boolean isBookAlias(String label) {
        return label.equals("elytrabook") || label.equals("libro") || label.equals("libroelitra")
                || label.equals("libroelytra") || label.equals("givelibro");
    }

    private boolean isChestKeyword(String text) {
        return text.equals("chestplate") || text.equals("pechera") || text.equals("chest")
                || text.equals("c") || text.equals("armor") || text.equals("elitra") || text.equals("elytra");
    }

    private boolean isBookKeyword(String text) {
        return text.equals("book") || text.equals("libro") || text.equals("b") || text.equals("l");
    }

    private void handleDirectItemCommand(CommandSender sender, String prefix, String[] args, boolean isChestplate, String label) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            String reloadMsg = plugin.getConfig().getString("messages-elytra.reload-success", "&a¡Configuración recargada correctamente!");
            sender.sendMessage(prefix + color(reloadMsg));
            return;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?"))) {
            sendHelp(sender, label);
            return;
        }

        Player target;
        int amount = 1;

        if (args.length == 0) {
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage(prefix + color(plugin.getConfig().getString("messages-elytra.only-players", "&cDebes especificar un jugador desde la consola: /" + label + " <jugador> [cantidad]")));
                return;
            }
            target = playerSender;
        } else if (isInteger(args[0])) {
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage(prefix + color(plugin.getConfig().getString("messages-elytra.only-players", "&cDebes especificar un jugador desde la consola: /" + label + " <jugador> [cantidad]")));
                return;
            }
            target = playerSender;
            amount = parseAmount(args[0], 1);
        } else {
            target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(prefix + color(plugin.getConfig().getString("messages-elytra.player-not-found", "&cEl jugador no fue encontrado o está desconectado.")));
                return;
            }
            if (args.length >= 2) {
                amount = parseAmount(args[1], 1);
            }
        }

        if (isChestplate) {
            giveElytraChestplate(sender, target, amount, prefix);
        } else {
            giveElytraBook(sender, target, amount, prefix);
        }
    }

    private void handleGiveSubcommand(CommandSender sender, String prefix, String[] args, String label) {
        if (args.length < 2) {
            sender.sendMessage(prefix + color("&cUso: &e/" + label + " give <pechera|libro> [jugador] [cantidad]"));
            return;
        }

        String itemType = args[1].toLowerCase();
        boolean isChestplate;

        if (isChestKeyword(itemType)) {
            isChestplate = true;
        } else if (isBookKeyword(itemType)) {
            isChestplate = false;
        } else {
            sender.sendMessage(prefix + color("&cTipo de objeto desconocido: '&e" + args[1] + "&c'. Usa &bpechera &co &blibro&c."));
            return;
        }

        handleGiveItem(sender, prefix, args, 2, isChestplate, label);
    }

    private void handleGiveItem(CommandSender sender, String prefix, String[] args, int startIndex, boolean isChestplate, String label) {
        Player target;
        int amount = 1;

        if (args.length <= startIndex) {
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage(prefix + color(plugin.getConfig().getString("messages-elytra.only-players", "&cDebes especificar un jugador desde la consola: /" + label + " " + (isChestplate ? "pechera" : "libro") + " <jugador> [cantidad]")));
                return;
            }
            target = playerSender;
        } else if (isInteger(args[startIndex])) {
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage(prefix + color(plugin.getConfig().getString("messages-elytra.only-players", "&cDebes especificar un jugador desde la consola: /" + label + " " + (isChestplate ? "pechera" : "libro") + " <jugador> [cantidad]")));
                return;
            }
            target = playerSender;
            amount = parseAmount(args[startIndex], 1);
        } else {
            target = Bukkit.getPlayer(args[startIndex]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(prefix + color(plugin.getConfig().getString("messages-elytra.player-not-found", "&cEl jugador no fue encontrado o está desconectado.")));
                return;
            }
            if (args.length > startIndex + 1) {
                amount = parseAmount(args[startIndex + 1], 1);
            }
        }

        if (isChestplate) {
            giveElytraChestplate(sender, target, amount, prefix);
        } else {
            giveElytraBook(sender, target, amount, prefix);
        }
    }

    private void giveElytraChestplate(CommandSender sender, Player target, int amount, String prefix) {
        ItemStack chestplate = itemManager.createElytraChestplate();
        deliverItem(target, chestplate, amount);

        String targetMsg = color(plugin.getConfig().getString("messages-elytra.chestplate-given", "&aHas recibido &e%amount%x &5Pechera de Netherite con Élitra&a.")
                .replace("%amount%", String.valueOf(amount))
                .replace("%player%", target.getName()));
        target.sendMessage(prefix + targetMsg);

        if (sender != target) {
            String senderMsg = color(plugin.getConfig().getString("messages-elytra.chestplate-given-target", "&aHas entregado &e%amount%x &5pechera(s) a &e%player%&a.")
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%player%", target.getName()));
            sender.sendMessage(prefix + senderMsg);
        }
    }

    private void giveElytraBook(CommandSender sender, Player target, int amount, String prefix) {
        ItemStack book = itemManager.createElytraBook();
        deliverItem(target, book, amount);

        String targetMsg = color(plugin.getConfig().getString("messages-elytra.book-given", "&aHas recibido &e%amount%x &6Libro Encantado de Alas del Inframundo&a.")
                .replace("%amount%", String.valueOf(amount))
                .replace("%player%", target.getName()));
        target.sendMessage(prefix + targetMsg);

        if (sender != target) {
            String senderMsg = color(plugin.getConfig().getString("messages-elytra.book-given-target", "&aHas entregado &e%amount%x &6libro(s) a &e%player%&a.")
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
            String invFullMsg = plugin.getConfig().getString("messages-elytra.inventory-full", "&e¡Tu inventario estaba lleno! Los objetos se soltaron en el suelo.");
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
        sender.sendMessage(color("&5&lAlcatrazPlugin &7- &dMódulo Netherite Elytra"));
        sender.sendMessage(color("&e/" + label + " pechera [jugador] [cant] &7- Entrega la pechera con élitra."));
        sender.sendMessage(color("&e/" + label + " libro [jugador] [cant] &7- Entrega el libro 'Alas del Inframundo'."));
        sender.sendMessage(color("&e/" + label + " give <pechera|libro> [jugador] [cant] &7- Comando /give alternativo."));
        sender.sendMessage(color("&e/pechera [jugador] [cant] &7- Acceso directo a la pechera."));
        sender.sendMessage(color("&e/libro [jugador] [cant] &7- Acceso directo al libro encantado."));
        sender.sendMessage(color("&e/" + label + " reload &7- Recarga la configuración del plugin."));
        sender.sendMessage(color("&8&m----------------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("netheriteelytra.admin")) {
            return Collections.emptyList();
        }

        String lowerAlias = alias.toLowerCase();

        // Direct commands tab completion: /pechera, /libro, /elytrachest, /elytrabook
        if (isChestplateAlias(lowerAlias) || isBookAlias(lowerAlias)) {
            if (args.length == 1) {
                List<String> list = new ArrayList<>();
                if ("reload".startsWith(args[0].toLowerCase())) list.add("reload");
                if ("help".startsWith(args[0].toLowerCase())) list.add("help");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        list.add(p.getName());
                    }
                }
                for (String num : Arrays.asList("1", "2", "5", "10", "64")) {
                    if (num.startsWith(args[0])) list.add(num);
                }
                return list;
            } else if (args.length == 2 && !isInteger(args[0])) {
                return Arrays.asList("1", "2", "5", "10", "64");
            }
            return Collections.emptyList();
        }

        // Main command tab completion: /netheriteelytra, /ne
        if (args.length == 1) {
            List<String> subs = Arrays.asList("pechera", "libro", "chestplate", "book", "give", "reload", "help");
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
            if (args[0].equalsIgnoreCase("give")) {
                List<String> items = Arrays.asList("pechera", "libro", "chestplate", "book");
                List<String> completions = new ArrayList<>();
                for (String item : items) {
                    if (item.toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(item);
                    }
                }
                return completions;
            } else if (isChestKeyword(args[0].toLowerCase()) || isBookKeyword(args[0].toLowerCase())) {
                List<String> completions = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
                for (String num : Arrays.asList("1", "2", "5", "10", "64")) {
                    if (num.startsWith(args[1])) completions.add(num);
                }
                return completions;
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give")) {
                List<String> completions = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
                for (String num : Arrays.asList("1", "2", "5", "10", "64")) {
                    if (num.startsWith(args[2])) completions.add(num);
                }
                return completions;
            } else if (isChestKeyword(args[0].toLowerCase()) || isBookKeyword(args[0].toLowerCase())) {
                return Arrays.asList("1", "2", "5", "10", "64");
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return Arrays.asList("1", "2", "5", "10", "64");
        }

        return Collections.emptyList();
    }
}
