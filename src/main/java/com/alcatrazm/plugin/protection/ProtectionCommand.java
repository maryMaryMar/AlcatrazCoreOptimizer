package com.alcatrazm.plugin.protection;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ProtectionCommand implements CommandExecutor, TabCompleter {

    private final ProtectionManager protectionManager;

    public ProtectionCommand(ProtectionManager protectionManager) {
        this.protectionManager = protectionManager;
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("ayuda")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "give":
            case "dar":
                if (!sender.hasPermission("alcatraz.admin") && !sender.hasPermission("alcatraz.claim.admin")) {
                    sender.sendMessage(color("&cNo tienes permisos para entregar piedras de protección."));
                    return true;
                }
                handleGive(sender, args, label);
                return true;

            case "trust":
            case "add":
            case "confiar":
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(color("&cEste comando solo puede ser usado por jugadores."));
                    return true;
                }
                handleTrust(p, args);
                return true;

            case "untrust":
            case "remove":
            case "desconfiar":
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(color("&cEste comando solo puede ser usado por jugadores."));
                    return true;
                }
                handleUntrust(p, args);
                return true;

            case "trustall":
            case "publico":
            case "public":
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(color("&cEste comando solo puede ser usado por jugadores."));
                    return true;
                }
                handleTrustAll(p, true);
                return true;

            case "untrustall":
            case "privado":
            case "private":
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(color("&cEste comando solo puede ser usado por jugadores."));
                    return true;
                }
                handleTrustAll(p, false);
                return true;

            case "flag":
            case "regla":
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(color("&cEste comando solo puede ser usado por jugadores."));
                    return true;
                }
                handleFlag(p, args);
                return true;

            case "flags":
            case "reglas":
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(color("&cEste comando solo puede ser usado por jugadores."));
                    return true;
                }
                handleFlagsList(p);
                return true;

            case "info":
            case "view":
            case "ver":
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(color("&cEste comando solo puede ser usado por jugadores."));
                    return true;
                }
                handleInfo(p);
                return true;

            case "bordes":
            case "border":
            case "borde":
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(color("&cEste comando solo puede ser usado por jugadores."));
                    return true;
                }
                handleViewBorders(p);
                return true;

            case "list":
            case "lista":
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(color("&cEste comando solo puede ser usado por jugadores."));
                    return true;
                }
                handleList(p);
                return true;

            default:
                sendHelp(sender, label);
                return true;
        }
    }

    private void handleGive(CommandSender sender, String[] args, String label) {
        if (args.length < 3) {
            sender.sendMessage(color("&cUso: /" + label + " give <jugador> <carbon|hierro|oro|diamante|esmeralda> [permanente|temporal] [cantidad]"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(color("&cJugador no encontrado: " + args[1]));
            return;
        }

        ProtectionBlockType blockType = ProtectionBlockType.fromString(args[2]);
        if (blockType == null) {
            sender.sendMessage(color("&cTipo de mena inválido. Opciones disponibles: carbon, hierro, oro, diamante, esmeralda."));
            return;
        }

        boolean temporary = false;
        int amount = 1;

        for (int i = 3; i < args.length; i++) {
            String a = args[i].toLowerCase();
            if (a.equals("temporal") || a.equals("temp") || a.equals("1semana")) {
                temporary = true;
            } else if (a.equals("permanente") || a.equals("perm")) {
                temporary = false;
            } else if (isInteger(a)) {
                amount = Math.max(1, Integer.parseInt(a));
            }
        }

        ItemStack item = protectionManager.createProtectionBlockItem(blockType, temporary, amount);
        if (target.getInventory().addItem(item).size() > 0) {
            target.getWorld().dropItemNaturally(target.getLocation(), item);
        }

        sender.sendMessage(color("&8[&aProtección&8] &aEntregada(s) &e" + amount + "x &7Mena de &e" + blockType.getSimpleName() + " (" + (temporary ? "&cTemporal 1 Semana" : "&aPermanente") + "&7) a &e" + target.getName() + "&a."));
        target.sendMessage(color("&8[&aProtección&8] &aHas recibido &e" + amount + "x &7Piedra de Protección: &e" + blockType.getSimpleName() + " &7(" + blockType.getRadiusFormatted() + ") - " + (temporary ? "&cTemporal (1 Semana)" : "&aPermanente") + "&a."));
    }

    private void handleTrust(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(color("&cUso: /ps trust <jugador>"));
            return;
        }

        Claim claim = protectionManager.getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage(color("&cDebes estar dentro de tu terreno protegido para añadir miembros."));
            return;
        }

        if (!claim.isOwner(player.getUniqueId()) && !player.hasPermission("alcatraz.admin")) {
            player.sendMessage(color("&cSolo el dueño de esta protección puede añadir miembros."));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(color("&cYa eres el dueño de esta protección."));
            return;
        }

        claim.addTrust(target.getUniqueId());
        protectionManager.saveClaims();
        player.sendMessage(color("&8[&aProtección&8] &aHas añadido a &e" + (target.getName() != null ? target.getName() : args[1]) + " &aa tu terreno protegido."));
    }

    private void handleUntrust(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(color("&cUso: /ps untrust <jugador>"));
            return;
        }

        Claim claim = protectionManager.getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage(color("&cDebes estar dentro de tu terreno protegido para remover miembros."));
            return;
        }

        if (!claim.isOwner(player.getUniqueId()) && !player.hasPermission("alcatraz.admin")) {
            player.sendMessage(color("&cSolo el dueño de esta protección puede remover miembros."));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        claim.removeTrust(target.getUniqueId());
        protectionManager.saveClaims();
        player.sendMessage(color("&8[&aProtección&8] &cHas removido a &e" + (target.getName() != null ? target.getName() : args[1]) + " &cde tu terreno protegido."));
    }

    private void handleTrustAll(Player player, boolean trustAll) {
        Claim claim = protectionManager.getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage(color("&cDebes estar dentro de tu terreno protegido."));
            return;
        }

        if (!claim.isOwner(player.getUniqueId()) && !player.hasPermission("alcatraz.admin")) {
            player.sendMessage(color("&cSolo el dueño puede modificar el acceso público del terreno."));
            return;
        }

        claim.setTrustAll(trustAll);
        protectionManager.saveClaims();

        if (trustAll) {
            player.sendMessage(color("&8[&aProtección&8] &a¡El terreno ahora es &ePÚBLICO&a! Todos los jugadores pueden construir/interactuar."));
        } else {
            player.sendMessage(color("&8[&aProtección&8] &cEl terreno ahora es &ePRIVADO&c. Solo miembros añadidos tienen acceso."));
        }
    }

    private void handleFlag(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(color("&cUso: /ps flag <nombre_flag> <allow|deny|true|false>"));
            player.sendMessage(color("&7Usa &e/ps flags &7para ver todas las flags disponibles."));
            return;
        }

        Claim claim = protectionManager.getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage(color("&cDebes estar dentro de tu terreno protegido para configurar flags."));
            return;
        }

        if (!claim.isOwner(player.getUniqueId()) && !player.hasPermission("alcatraz.admin")) {
            player.sendMessage(color("&cSolo el dueño puede modificar las flags de esta protección."));
            return;
        }

        ClaimFlag flag = ClaimFlag.fromString(args[1]);
        if (flag == null) {
            player.sendMessage(color("&cFlag desconocida: &e" + args[1] + "&c. Usa &e/ps flags &cpara la lista."));
            return;
        }

        boolean allow = args[2].equalsIgnoreCase("allow") || args[2].equalsIgnoreCase("true") || args[2].equalsIgnoreCase("si") || args[2].equalsIgnoreCase("on");
        claim.setFlag(flag, allow);
        protectionManager.saveClaims();

        player.sendMessage(color("&8[&aProtección&8] &7Flag &e" + flag.name() + " &7establecida en: " + (allow ? "&a&lPERMITIDO (ALLOW)" : "&c&lDENEGADO (DENY)")));
    }

    private void handleFlagsList(Player player) {
        Claim claim = protectionManager.getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage(color("&8&m----------------------------------------"));
            player.sendMessage(color("&6&lFlags Disponibles en Protecciones Alcatraz:"));
            for (ClaimFlag f : ClaimFlag.values()) {
                player.sendMessage(color("&8▪ &e" + f.name() + " &8- &7" + f.getDescription()));
            }
            player.sendMessage(color("&8&m----------------------------------------"));
            return;
        }

        player.sendMessage(color("&8&m----------------------------------------"));
        player.sendMessage(color("&6&lEstado de Flags en tu Terreno (&e" + claim.getName() + "&6):"));
        for (ClaimFlag f : ClaimFlag.values()) {
            boolean status = claim.getFlag(f);
            player.sendMessage(color("&8▪ &e" + f.name() + ": " + (status ? "&aALLOW" : "&cDENY") + " &8(&7" + f.getDescription() + "&8)"));
        }
        player.sendMessage(color("&7Usa: &e/ps flag <nombre> <allow|deny>"));
        player.sendMessage(color("&8&m----------------------------------------"));
    }

    private void handleInfo(Player player) {
        Claim claim = protectionManager.getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage(color("&cNo estás dentro de ningún terreno protegido."));
            return;
        }

        player.sendMessage(color("&8&m----------------------------------------"));
        player.sendMessage(color("&6&lInformación del Terreno Protegido"));
        player.sendMessage(color("&7Nombre: &e" + claim.getName() + " &8| &7ID: &8" + claim.getId()));
        player.sendMessage(color("&7Dueño: &e" + claim.getOwnerName()));
        player.sendMessage(color("&7Tipo: &b" + claim.getType().getDisplay() + " &8| &7Mena: &6" + (claim.getBlockType() != null ? claim.getBlockType().getDisplayName() : "Especial")));
        player.sendMessage(color("&7Radio: &e" + claim.getRadius() + " bloques &8| &7Área: &b" + (claim.getRadius() * 2 + 1) + "x" + (claim.getRadius() * 2 + 1) + "b"));
        player.sendMessage(color("&7Ubicación Bloque Central: &f" + claim.getWorldName() + " (" + claim.getBlockX() + ", " + claim.getBlockY() + ", " + claim.getBlockZ() + ")"));
        player.sendMessage(color("&7Duración: " + claim.getRemainingTimeFormatted()));
        player.sendMessage(color("&7Estado de Acceso: " + (claim.isTrustAll() ? "&aPúblico (Todos tienen acceso)" : "&ePrivado")));
        player.sendMessage(color("&7Miembros con confianza: &a" + claim.getTrustedPlayers().size() + " jugador(es)"));
        player.sendMessage(color("&8&m----------------------------------------"));
    }

    private void handleViewBorders(Player player) {
        Claim claim = protectionManager.getClaimAt(player.getLocation());
        if (claim == null) {
            player.sendMessage(color("&cDebes estar dentro de un terreno protegido para ver sus bordes."));
            return;
        }
        protectionManager.showClaimParticles(player, claim);
        player.sendMessage(color("&8[&aProtección&8] &aDibujando los bordes del terreno con partículas durante 8 segundos."));
    }

    private void handleList(Player player) {
        List<Claim> list = protectionManager.getPlayerClaims(player.getUniqueId());
        if (list.isEmpty()) {
            player.sendMessage(color("&cNo tienes ninguna piedra de protección activa."));
            return;
        }

        player.sendMessage(color("&8&m----------------------------------------"));
        player.sendMessage(color("&6&lTus Terrenos Protegidos (&e" + list.size() + "&6/&e" + protectionManager.getMaxClaimsPerPlayer() + "&6):"));
        for (Claim c : list) {
            player.sendMessage(color("&8▪ &e" + c.getName() + " &7- &6" + (c.getBlockType() != null ? c.getBlockType().getSimpleName() : "PS") + " (" + c.getRadius() + "b) &8| &f" + c.getWorldName() + " (" + c.getBlockX() + ", " + c.getBlockY() + ", " + c.getBlockZ() + ") &8| " + c.getRemainingTimeFormatted()));
        }
        player.sendMessage(color("&8&m----------------------------------------"));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(color("&8&m----------------------------------------"));
        sender.sendMessage(color("&6&lSistema de Protecciones y Claims - AlcatrazCore"));
        sender.sendMessage(color("&e/" + label + " trust <jugador> &7- Da permisos en tu terreno."));
        sender.sendMessage(color("&e/" + label + " untrust <jugador> &7- Quita permisos en tu terreno."));
        sender.sendMessage(color("&e/" + label + " trustall / untrustall &7- Hace el terreno público o privado."));
        sender.sendMessage(color("&e/" + label + " flag <regla> <allow|deny> &7- Configura una regla en tu terreno."));
        sender.sendMessage(color("&e/" + label + " flags &7- Muestra el estado de todas las flags del terreno."));
        sender.sendMessage(color("&e/" + label + " info &7- Información detallada del terreno actual."));
        sender.sendMessage(color("&e/" + label + " view / bordes &7- Muestra los límites del terreno con partículas."));
        sender.sendMessage(color("&e/" + label + " list &7- Lista todos tus terrenos protegidos."));
        if (sender.hasPermission("alcatraz.admin") || sender.hasPermission("alcatraz.claim.admin")) {
            sender.sendMessage(color("&c/" + label + " give <jugador> <tipo> [permanente|temporal] [cant] &7- Comando Admin."));
        }
        sender.sendMessage(color("&8&m----------------------------------------"));
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
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("trust", "untrust", "trustall", "untrustall", "flag", "flags", "info", "view", "bordes", "list"));
            if (sender.hasPermission("alcatraz.admin") || sender.hasPermission("alcatraz.claim.admin")) {
                subs.add("give");
            }
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) list.add(s);
            }
            return list;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) list.add(p.getName());
                }
                return list;
            }
            if (args[0].equalsIgnoreCase("flag")) {
                for (ClaimFlag f : ClaimFlag.values()) {
                    if (f.name().toLowerCase().startsWith(args[1].toLowerCase())) list.add(f.name().toLowerCase());
                }
                return list;
            }
            if (args[0].equalsIgnoreCase("give") && (sender.hasPermission("alcatraz.admin") || sender.hasPermission("alcatraz.claim.admin"))) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) list.add(p.getName());
                }
                return list;
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("flag")) {
                list.add("allow");
                list.add("deny");
                return list;
            }
            if (args[0].equalsIgnoreCase("give") && (sender.hasPermission("alcatraz.admin") || sender.hasPermission("alcatraz.claim.admin"))) {
                list.add("carbon");
                list.add("hierro");
                list.add("oro");
                list.add("diamante");
                list.add("esmeralda");
                return list;
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("give") && (sender.hasPermission("alcatraz.admin") || sender.hasPermission("alcatraz.claim.admin"))) {
            list.add("permanente");
            list.add("temporal");
            return list;
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("give") && (sender.hasPermission("alcatraz.admin") || sender.hasPermission("alcatraz.claim.admin"))) {
            list.addAll(Arrays.asList("1", "2", "5", "10", "64"));
            return list;
        }

        return list;
    }
}
