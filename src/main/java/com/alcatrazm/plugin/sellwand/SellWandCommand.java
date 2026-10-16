package com.alcatrazm.plugin.sellwand;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.*;

public class SellWandCommand implements CommandExecutor, TabCompleter {

    private final AlcatrazPlugin plugin;
    private final SellWandManager manager;

    private static final DecimalFormat PRICE_FMT = new DecimalFormat("#,##0.00");

    public SellWandCommand(AlcatrazPlugin plugin, SellWandManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "give":
            case "dar":
                handleGive(sender, args);
                break;
            case "list":
            case "lista":
                handleList(sender);
                break;
            case "prices":
            case "precios":
                handlePrices(sender);
                break;
            case "reload":
            case "recargar":
                handleReload(sender);
                break;
            default:
                sender.sendMessage(SellWandTier.colorize("&cSubcomando desconocido. Usa &e/sellwand help&c."));
                break;
        }

        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alcatraz.sellwand.admin")) {
            sender.sendMessage(SellWandTier.colorize("&cNo tienes permiso para ejecutar este comando."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(SellWandTier.colorize("&cUso: /sellwand give <jugador> <tier> [cantidad] [usos] [multiplicador]"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(SellWandTier.colorize("&cEl jugador &e" + args[1] + " &cno está conectado."));
            return;
        }

        String tierId = args[2].toUpperCase();
        SellWandTier tier = manager.getTierById(tierId);
        if (tier == null) {
            sender.sendMessage(SellWandTier.colorize("&cEl tier &e" + tierId + " &cno existe. Tiers disponibles: &e" + String.join(", ", manager.getTiers().keySet())));
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                sender.sendMessage(SellWandTier.colorize("&cCantidad inválida. Usando 1."));
            }
        }

        int uses = tier.isInfinite() ? -1 : tier.getMaxUses();
        if (args.length >= 5 && !tier.isInfinite()) {
            try {
                uses = Math.max(1, Integer.parseInt(args[4]));
            } catch (NumberFormatException e) {
                sender.sendMessage(SellWandTier.colorize("&cUsos inválidos. Usando valor por defecto: " + uses));
            }
        }

        double multiplier = tier.getMultiplier();
        if (args.length >= 6) {
            try {
                multiplier = Math.max(0.01, Double.parseDouble(args[5]));
            } catch (NumberFormatException e) {
                sender.sendMessage(SellWandTier.colorize("&cMultiplicador inválido. Usando valor del tier: " + multiplier));
            }
        }

        for (int i = 0; i < amount; i++) {
            ItemStack wand = manager.createSellWand(tier, uses, multiplier);
            target.getInventory().addItem(wand);
        }

        sender.sendMessage(SellWandTier.colorize("&aLe has entregado &e" + amount + "x " + tier.getName() + " &7(" + multiplier + "x) &aa &e" + target.getName() + "&a."));
        target.sendMessage(SellWandTier.colorize("&a¡Has recibido &e" + amount + "x " + tier.getName() + " &7(" + multiplier + "x)&a!"));
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(SellWandTier.colorize("&6&l=== TIERS DE SELLWANDS DISPONIBLES ==="));
        for (SellWandTier t : manager.getTiers().values()) {
            String uses = t.isInfinite() ? "&dInfinitos" : String.valueOf(t.getMaxUses());
            sender.sendMessage(SellWandTier.colorize(" &8▪ &r" + t.getName() + " &7(ID: &f" + t.getId() + "&7) " +
                    "&8| &eMultiplicador: &a" + t.getMultiplier() + "x &8| &eUsos: &b" + uses + " &8| &eCMD: &6" + t.getCustomModelData()));
        }
    }

    private void handlePrices(CommandSender sender) {
        Map<Material, Double> prices = manager.getPrices();
        sender.sendMessage(SellWandTier.colorize("&6&l=== PRECIOS DE VENTA ACTIVOS (" + prices.size() + " ítems) ==="));
        int count = 0;
        for (Map.Entry<Material, Double> entry : prices.entrySet()) {
            sender.sendMessage(SellWandTier.colorize(" &8▪ &e" + entry.getKey().name() + "&7: &a$" + PRICE_FMT.format(entry.getValue())));
            if (++count >= 30) {
                sender.sendMessage(SellWandTier.colorize(" &7... y &e" + (prices.size() - 30) + " &7ítems más en &fconfig.yml"));
                break;
            }
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("alcatraz.sellwand.admin")) {
            sender.sendMessage(SellWandTier.colorize("&cNo tienes permiso para recargar el plugin."));
            return;
        }

        plugin.reloadConfig();
        manager.loadConfig();
        sender.sendMessage(SellWandTier.colorize("&a¡Configuración y precios de &eSellwands &arecargados con éxito!"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(SellWandTier.colorize("&6&l=== ALCATRAZ SELLWANDS (VaraVenta) ==="));
        sender.sendMessage(SellWandTier.colorize(" &8▪ &e/sellwand give <jugador> <tier> [cant] [usos] [mult] &7- Entrega una varita."));
        sender.sendMessage(SellWandTier.colorize(" &8▪ &e/sellwand list &7- Muestra todos los tiers configurados."));
        sender.sendMessage(SellWandTier.colorize(" &8▪ &e/sellwand prices &7- Muestra la lista de precios de venta."));
        sender.sendMessage(SellWandTier.colorize(" &8▪ &e/sellwand reload &7- Recarga tiers y precios en vivo."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String[] subs = {"give", "list", "prices", "reload", "help"};
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    completions.add(s);
                }
            }
            return completions;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("dar"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("dar"))) {
            for (String tId : manager.getTiers().keySet()) {
                if (tId.toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(tId);
                }
            }
            return completions;
        }

        if (args.length == 4 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("dar"))) {
            completions.addAll(Arrays.asList("1", "2", "3", "5", "10"));
            return completions;
        }

        if (args.length == 5 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("dar"))) {
            completions.addAll(Arrays.asList("100", "250", "500", "1000", "-1"));
            return completions;
        }

        if (args.length == 6 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("dar"))) {
            completions.addAll(Arrays.asList("1.0", "1.2", "1.5", "1.8", "2.0", "3.0"));
            return completions;
        }

        return completions;
    }
}
