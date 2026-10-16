package com.alcatrazm.plugin.commands;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.lifemending.managers.LifeMendingManager;
import com.alcatrazm.plugin.mobcatcher.MobCatcherManager;
import com.alcatrazm.plugin.netheriteelytra.managers.ItemManager;
import com.alcatrazm.plugin.newbieprotection.NewbieProtectionManager;
import com.alcatrazm.plugin.optimizer.monitor.PerformanceMonitor;
import com.alcatrazm.plugin.protection.Claim;
import com.alcatrazm.plugin.protection.ClaimType;
import com.alcatrazm.plugin.protection.ProtectionBlockType;
import com.alcatrazm.plugin.protection.ProtectionCommand;
import com.alcatrazm.plugin.protection.ProtectionManager;
import com.alcatrazm.plugin.sonicblade.SonicBladeManager;
import com.alcatrazm.plugin.bossdefense.BossDefenseCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class AlcatrazCommand implements CommandExecutor, TabCompleter {

    private final AlcatrazPlugin plugin;
    private final SonicBladeManager sonicBladeManager;
    private final ItemManager itemManager;
    private final LifeMendingManager lifeMendingManager;
    private final NewbieProtectionManager newbieProtectionManager;
    private final PerformanceMonitor performanceMonitor;
    private final ProtectionManager protectionManager;
    private final MobCatcherManager mobCatcherManager;
    private final ProtectionCommand protectionCommand;

    public AlcatrazCommand(AlcatrazPlugin plugin, SonicBladeManager sonicBladeManager, ItemManager itemManager, 
                           LifeMendingManager lifeMendingManager, 
                           NewbieProtectionManager newbieProtectionManager,
                           PerformanceMonitor performanceMonitor, ProtectionManager protectionManager,
                           MobCatcherManager mobCatcherManager, ProtectionCommand protectionCommand) {
        this.plugin = plugin;
        this.sonicBladeManager = sonicBladeManager;
        this.itemManager = itemManager;
        this.lifeMendingManager = lifeMendingManager;
        this.newbieProtectionManager = newbieProtectionManager;
        this.performanceMonitor = performanceMonitor;
        this.protectionManager = protectionManager;
        this.mobCatcherManager = mobCatcherManager;
        this.protectionCommand = protectionCommand;
    }

    private String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            String firstArg = args[0].toLowerCase();
            if (firstArg.equals("ps") || firstArg.equals("protectionstones") || firstArg.equals("protes") || firstArg.equals("piedras")) {
                if (protectionCommand != null) {
                    String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                    return protectionCommand.onCommand(sender, command, "ps", subArgs);
                }
            }
        }

        if (!sender.hasPermission("alcatraz.admin")) {
            sender.sendMessage(color("&cNo tienes permisos para usar los comandos principales de AlcatrazCoreOptimizer."));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload":
                plugin.reloadConfig();
                plugin.restartTasks();
                if (plugin.getElevatorListener() != null) plugin.getElevatorListener().loadMaterials();
                if (newbieProtectionManager != null) newbieProtectionManager.loadData();
                if (plugin.getTransientChunkUnloader() != null) plugin.getTransientChunkUnloader().reload();
                if (plugin.getFarmOptimizer() != null) plugin.getFarmOptimizer().reload();
                if (plugin.getItemAndXPStacker() != null) plugin.getItemAndXPStacker().reload();
                if (plugin.getVillagerOptimizer() != null) plugin.getVillagerOptimizer().reload();
                if (plugin.getTntOptimizer() != null) plugin.getTntOptimizer().reload();
                if (plugin.getProtectionManager() != null) plugin.getProtectionManager().reload();
                if (plugin.getCombatLogManager() != null) plugin.getCombatLogManager().reload();
                if (plugin.getAutoAnnouncer() != null) plugin.getAutoAnnouncer().reload();
                if (plugin.getSellWandManager() != null) plugin.getSellWandManager().loadConfig();
                if (plugin.getMountManager() != null) plugin.getMountManager().reload();
                if (plugin.getBossDefenseManager() != null) plugin.getBossDefenseManager().reload();
                if (plugin.getChatFilterManager() != null) plugin.getChatFilterManager().reload();
                if (plugin.getAntiFloodManager() != null) plugin.getAntiFloodManager().reload();
                if (plugin.getAntiSpamManager() != null) plugin.getAntiSpamManager().reload();
                if (plugin.getAntiCapsManager() != null) plugin.getAntiCapsManager().reload();
                if (plugin.getResourcePackManager() != null) plugin.getResourcePackManager().reload();
                if (plugin.getAntiXrayManager() != null) plugin.getAntiXrayManager().loadConfig();
                if (plugin.getGrapplingHookManager() != null) plugin.getGrapplingHookManager().loadConfig();
                plugin.reloadBanList();

                if (plugin.getAuraSkillsFixer() != null) plugin.getAuraSkillsFixer().syncAllOnlinePlayers();
                sender.sendMessage(color("&8[&bAlcatraz&cOptimizer&8] &a¡Configuración, optimizadores y módulos recargados con éxito (v" + plugin.getDescription().getVersion() + ")!"));
                return true;

            case "monitor":
            case "lag":
            case "status":
                if (performanceMonitor != null) {
                    performanceMonitor.sendMonitorReport(sender);
                }
                return true;

            case "clean":
            case "gc":
            case "limpiar":
                if (performanceMonitor != null) {
                    performanceMonitor.cleanServer(sender);
                }
                return true;

            case "mobcatcher":
            case "esfera":
            case "cazador":
                handleGiveMobCatcher(sender, args);
                return true;

            case "ps":
            case "protectionstones":
            case "protes":
            case "piedras":
            case "claim":
            case "proteccion":
                if (protectionCommand != null) {
                    String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                    return protectionCommand.onCommand(sender, command, "ps", subArgs);
                }
                handleAdminClaim(sender, args);
                return true;

            case "bossdefense":
            case "defensaboss":
            case "bossdef":
                if (plugin.getBossDefenseManager() != null) {
                    BossDefenseCommand bdc = new BossDefenseCommand(plugin, plugin.getBossDefenseManager());
                    String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                    return bdc.onCommand(sender, command, label + " bossdefense", subArgs);
                }
                return true;

            case "version":
            case "info":
                sender.sendMessage(color("&8&m----------------------------------------"));
                sender.sendMessage(color("&b&lAlcatrazCoreOptimizer &ev" + plugin.getDescription().getVersion()));
                sender.sendMessage(color("&7Autor: &eAlcatrazM"));
                sender.sendMessage(color("&7Módulos Activos: &a[Hoja Sónica, Pechera Élitra, Life Mending, Elevadores, VoidSpawn, Anti-PvP Novatos, Player Vaults Infinitos, Protecciones 80-150b, Mob Catcher, CombatLog, Papelera, AutoAnnouncer, SellWands, Monturas, Defensa Bosses 75%, Optimizadores Chunks/Farm/Villager/TNT]"));
                sender.sendMessage(color("&8&m----------------------------------------"));
                return true;

            case "hojasonica":
            case "sonicblade":
            case "hs":
                handleGiveSonicBlade(sender, args);
                return true;

            case "pechera":
            case "chestplate":
            case "elytra":
                handleGiveChestplate(sender, args);
                return true;

            case "libro":
            case "book":
            case "elytrabook":
                handleGiveElytraBook(sender, args);
                return true;

            case "lifemending":
            case "lm":
            case "vidamending":
                handleGiveLifeMending(sender, args);
                return true;

            case "newbie":
                handleNewbieCommand(sender, args);
                return true;

            case "skills":
            case "auraskills":
            case "fixskills":
                handleSkillsCommand(sender, args);
                return true;

            case "monturas":
            case "montura":
            case "mounts":
            case "mount":
            case "mascotas":
            case "mascota":
            case "pets":
            case "pet":
                if (plugin.getMountManager() != null) {
                    if (args.length > 1 && args[1].equalsIgnoreCase("reload")) {
                        plugin.getMountManager().reload();
                        sender.sendMessage(color("&8[&6Monturas&8] &aConfiguración de monturas recargada."));
                        return true;
                    }
                    if (args.length > 1 && (args[1].equalsIgnoreCase("dismissall") || args[1].equalsIgnoreCase("clearall"))) {
                        plugin.getMountManager().dismissAll();
                        sender.sendMessage(color("&8[&6Monturas&8] &cTodas las monturas activas han sido retiradas."));
                        return true;
                    }
                    if (sender instanceof Player p) {
                        com.alcatrazm.plugin.mounts.MountGUI.open(p, plugin.getMountManager());
                    } else {
                        sender.sendMessage(color("&cUsa /monturas spawn <tipo> [jugador]"));
                    }
                }
                return true;

            case "unmute":
            case "desmutear":
                handleUnmuteCommand(sender, args);
                return true;

            case "mutes":
            case "silenciados":
                handleMutesListCommand(sender, args);
                return true;

            case "xray":
            case "antixray":
                if (plugin.getAntiXrayManager() != null) {
                    com.alcatrazm.plugin.antixray.AntiXrayCommand xc = new com.alcatrazm.plugin.antixray.AntiXrayCommand(plugin, plugin.getAntiXrayManager());
                    String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                    return xc.onCommand(sender, command, "xray", subArgs);
                }
                return true;

            case "gancho":
            case "grapplinghook":
            case "ghook":
                if (plugin.getGrapplingHookManager() != null) {
                    com.alcatrazm.plugin.grapplinghook.GrapplingHookCommand ghc = new com.alcatrazm.plugin.grapplinghook.GrapplingHookCommand(plugin, plugin.getGrapplingHookManager());
                    String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                    return ghc.onCommand(sender, command, "gancho", subArgs);
                }
                return true;

            default:
                sendHelp(sender, label);
                return true;
        }
    }

    private void handleUnmuteCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alcatraz.chatfilter.admin") && !sender.hasPermission("alcatraz.admin")) {
            sender.sendMessage(color("&cNo tienes permisos para gestionar silencios de chat."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(color("&eUso: /alcatraz unmute <jugador>"));
            return;
        }

        String targetName = args[1];
        if (plugin.getChatFilterManager() == null) {
            sender.sendMessage(color("&cEl módulo de filtro de chat no está activo."));
            return;
        }

        boolean success = plugin.getChatFilterManager().unmuteByName(targetName);
        String prefix = plugin.getChatFilterManager().getMsgPrefix();
        if (success) {
            String msgSender = plugin.getChatFilterManager().getMsgUnmutedSender().replace("%player%", targetName);
            sender.sendMessage(color(prefix + msgSender));

            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null && target.isOnline()) {
                String msgTarget = plugin.getChatFilterManager().getMsgUnmutedTarget();
                target.sendMessage(color(prefix + msgTarget));
            }
        } else {
            String msgNot = plugin.getChatFilterManager().getMsgNotMuted().replace("%player%", targetName);
            sender.sendMessage(color(prefix + msgNot));
        }
    }

    private void handleMutesListCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alcatraz.chatfilter.admin") && !sender.hasPermission("alcatraz.admin")) {
            sender.sendMessage(color("&cNo tienes permisos para ver los silencios de chat."));
            return;
        }

        if (plugin.getChatFilterManager() == null) {
            sender.sendMessage(color("&cEl módulo de filtro de chat no está activo."));
            return;
        }

        Map<UUID, com.alcatrazm.plugin.chatfilter.ChatFilterManager.MuteEntry> mutes = plugin.getChatFilterManager().getActiveMutes();
        if (mutes.isEmpty()) {
            sender.sendMessage(color("&8[&c&lFILTRO&8] &aNo hay ningún jugador silenciado actualmente."));
            return;
        }

        sender.sendMessage(color("&8&m----------------------------------------"));
        sender.sendMessage(color("&c&lJugadores Silenciados Actualmente &7(" + mutes.size() + "):"));
        for (Map.Entry<UUID, com.alcatrazm.plugin.chatfilter.ChatFilterManager.MuteEntry> e : mutes.entrySet()) {
            String remaining = plugin.getChatFilterManager().getRemainingMuteTimeFormatted(e.getKey());
            sender.sendMessage(color(" &8» &e" + e.getValue().playerName + " &7- Restante: &c" + remaining + " &7(" + e.getValue().reason + ")"));
        }
        sender.sendMessage(color("&8&m----------------------------------------"));
    }

    private void handleGiveMobCatcher(CommandSender sender, String[] args) {
        if (mobCatcherManager == null) return;
        Player target = getTarget(sender, args, 1);
        if (target == null) return;
        int amount = getAmount(args, 1, target == sender);
        ItemStack catcher = mobCatcherManager.createEmptyCatcher(amount);
        deliverItem(target, catcher, amount);
        sender.sendMessage(color("&aHas entregado &e" + amount + "x &dEsfera(s) de Contención &aa &e" + target.getName() + "&a."));
    }

    private void handleAdminClaim(CommandSender sender, String[] args) {
        if (protectionManager == null) return;
        if (args.length < 2) {
            sender.sendMessage(color("&eUso: /alcatraz claim [give|delete|list]"));
            return;
        }

        String action = args[1].toLowerCase();
        if (action.equals("give") || action.equals("dar")) {
            if (args.length < 4) {
                sender.sendMessage(color("&cUso: /alcatraz claim give <jugador> <carbon|hierro|oro|diamante|esmeralda> [permanente|temporal] [cant]"));
                return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(color("&cJugador no encontrado: " + args[2]));
                return;
            }
            ProtectionBlockType bType = ProtectionBlockType.fromString(args[3]);
            if (bType == null) {
                sender.sendMessage(color("&cTipo de mena inválido (carbon, hierro, oro, diamante, esmeralda)."));
                return;
            }
            boolean temp = args.length >= 5 && (args[4].equalsIgnoreCase("temporal") || args[4].equalsIgnoreCase("temp"));
            int amount = (args.length >= 6 && isInteger(args[5])) ? Integer.parseInt(args[5]) : (args.length >= 5 && isInteger(args[4])) ? Integer.parseInt(args[4]) : 1;

            ItemStack item = protectionManager.createProtectionBlockItem(bType, temp, amount);
            if (target.getInventory().addItem(item).size() > 0) {
                target.getWorld().dropItemNaturally(target.getLocation(), item);
            }
            sender.sendMessage(color("&aHas entregado &e" + amount + "x &7Mena de &e" + bType.getSimpleName() + " (" + (temp ? "&cTemporal 1 Semana" : "&aPermanente") + "&7) a &e" + target.getName() + "&a."));
            target.sendMessage(color("&aHas recibido &e" + amount + "x &7Piedra de Protección: &e" + bType.getSimpleName() + " &7(" + bType.getRadiusFormatted() + ") - " + (temp ? "&cTemporal 1 Semana" : "&aPermanente") + "&a."));
        } else if (action.equals("delete")) {
            if (args.length < 3) {
                sender.sendMessage(color("&cUso: /alcatraz claim delete <nombre_claim>"));
                return;
            }
            Claim claim = protectionManager.getClaimByName(args[2]);
            if (claim == null) {
                sender.sendMessage(color("&cProtección no encontrada: " + args[2]));
                return;
            }
            protectionManager.removeClaim(claim.getId());
            sender.sendMessage(color("&aProtección &e" + claim.getName() + " &aeliminada correctamente."));
        } else if (action.equals("list")) {
            sender.sendMessage(color("&8&m----------------------------------------"));
            sender.sendMessage(color("&6&lTodas las Protecciones del Servidor:"));
            for (Claim c : protectionManager.getAllClaims()) {
                sender.sendMessage(color(" &8▪ &e" + c.getName() + " &7[&b" + c.getType().getDisplay() + "&7] - Dueño: &f" + c.getOwnerName() + " &7(Radio: &e" + c.getRadius() + "b&7, Coords: &fX:" + c.getBlockX() + " Z:" + c.getBlockZ() + "&7)"));
            }
            sender.sendMessage(color("&8&m----------------------------------------"));
        }
    }


    private void handleNewbieCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(color("&cUso: /alcatraz newbie <jugador> <minutos|remove>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(color("&cJugador no encontrado: " + args[1]));
            return;
        }

        if (args[2].equalsIgnoreCase("remove") || args[2].equalsIgnoreCase("off")) {
            newbieProtectionManager.removeProtection(target.getUniqueId());
            sender.sendMessage(color("&aProtección de novato eliminada para &e" + target.getName()));
            target.sendMessage(color("&8[&aProtección Novato&8] &cUn administrador ha desactivado tu protección contra PvP."));
            return;
        }

        if (isInteger(args[2])) {
            int minutes = Integer.parseInt(args[2]);
            newbieProtectionManager.grantProtection(target.getUniqueId(), minutes);
            sender.sendMessage(color("&aProtección de novato otorgada a &e" + target.getName() + " &apor &e" + minutes + " minutos&a."));
            target.sendMessage(color("&8[&aProtección Novato&8] &aUn administrador te ha otorgado &e" + minutes + " minutos &ade protección contra PvP."));
        } else {
            sender.sendMessage(color("&cEspecifica una cantidad válida de minutos."));
        }
    }

    private void handleGiveSonicBlade(CommandSender sender, String[] args) {
        Player target = getTarget(sender, args, 1);
        if (target == null) return;
        int amount = getAmount(args, 1, target == sender);
        ItemStack item = sonicBladeManager.createSonicBlade();
        deliverItem(target, item, amount);
        sender.sendMessage(color("&aHas entregado &e" + amount + "x &b&lHoja Sónica &aa &e" + target.getName() + "&a."));
    }

    private void handleGiveChestplate(CommandSender sender, String[] args) {
        Player target = getTarget(sender, args, 1);
        if (target == null) return;
        int amount = getAmount(args, 1, target == sender);
        ItemStack item = itemManager.createElytraChestplate();
        deliverItem(target, item, amount);
        sender.sendMessage(color("&aHas entregado &e" + amount + "x &5&lPechera con Élitra &aa &e" + target.getName() + "&a."));
    }

    private void handleGiveElytraBook(CommandSender sender, String[] args) {
        Player target = getTarget(sender, args, 1);
        if (target == null) return;
        int amount = getAmount(args, 1, target == sender);
        ItemStack item = itemManager.createElytraBook();
        deliverItem(target, item, amount);
        sender.sendMessage(color("&aHas entregado &e" + amount + "x &6Libro de Alas del Inframundo &aa &e" + target.getName() + "&a."));
    }

    private void handleGiveLifeMending(CommandSender sender, String[] args) {
        Player target = getTarget(sender, args, 1);
        if (target == null) return;
        int amount = getAmount(args, 1, target == sender);
        ItemStack item = lifeMendingManager.createLifeMendingBook();
        deliverItem(target, item, amount);
        sender.sendMessage(color("&aHas entregado &e" + amount + "x &cLibro de Life Mending &aa &e" + target.getName() + "&a."));
    }

    private Player getTarget(CommandSender sender, String[] args, int targetArgIndex) {
        if (args.length <= targetArgIndex) {
            if (sender instanceof Player p) {
                return p;
            }
            sender.sendMessage(color("&cDebes especificar un jugador: /alcatraz " + args[0] + " <jugador> [cantidad]"));
            return null;
        }

        if (isInteger(args[targetArgIndex])) {
            if (sender instanceof Player p) {
                return p;
            }
            sender.sendMessage(color("&cDebes especificar un jugador: /alcatraz " + args[0] + " <jugador> [cantidad]"));
            return null;
        }

        Player target = Bukkit.getPlayer(args[targetArgIndex]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(color("&cEl jugador &e" + args[targetArgIndex] + " &cno fue encontrado o está desconectado."));
            return null;
        }
        return target;
    }

    private int getAmount(String[] args, int startIndex, boolean selfTarget) {
        if (selfTarget && args.length > startIndex && isInteger(args[startIndex])) {
            return parseAmount(args[startIndex], 1);
        }
        if (!selfTarget && args.length > startIndex + 1 && isInteger(args[startIndex + 1])) {
            return parseAmount(args[startIndex + 1], 1);
        }
        return 1;
    }

    private void deliverItem(Player player, ItemStack itemTemplate, int amount) {
        int maxStack = itemTemplate.getMaxStackSize();
        if (maxStack <= 1) {
            for (int i = 0; i < amount; i++) {
                ItemStack single = itemTemplate.clone();
                single.setAmount(1);
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(single);
                if (!leftover.isEmpty()) {
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
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
                remaining -= count;
            }
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

    private void handleSkillsCommand(CommandSender sender, String[] args) {
        if (plugin.getAuraSkillsFixer() == null) {
            sender.sendMessage(color("&cEl módulo de AuraSkills no está disponible."));
            return;
        }

        if (args.length > 1 && (args[1].equalsIgnoreCase("fix") || args[1].equalsIgnoreCase("cargar") || args[1].equalsIgnoreCase("load"))) {
            if (args.length < 3) {
                sender.sendMessage(color("&cUso: /" + args[0] + " " + args[1] + " <jugador>"));
                return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(color("&cEl jugador &e" + args[2] + " &cno está conectado."));
                return;
            }
            boolean success = plugin.getAuraSkillsFixer().forceLoadUser(target);
            if (success) {
                sender.sendMessage(color("&8[&bAlcatraz&cSkills&8] &a¡Perfil de AuraSkills cargado y activado exitosamente para &e" + target.getName() + "&a!"));
                target.sendMessage(color("&8[&bAlcatraz&cSkills&8] &aTu perfil de habilidades ha sido sincronizado. Ya puedes abrir &e/skills&a."));
            } else {
                sender.sendMessage(color("&8[&bAlcatraz&cSkills&8] &cNo se pudo cargar el perfil de &e" + target.getName() + "&c. Revisa la consola del servidor."));
            }
            return;
        }

        sender.sendMessage(color("&8[&bAlcatraz&cSkills&8] &7Iniciando saneamiento de archivos y sincronización de perfiles en memoria..."));
        int sanitized = plugin.getAuraSkillsFixer().sanitizeAllUserDataFiles();
        int synced = plugin.getAuraSkillsFixer().syncAllOnlinePlayers();
        sender.sendMessage(color("&8[&bAlcatraz&cSkills&8] &a¡Sincronización de AuraSkills completada con éxito!"));
        sender.sendMessage(color("&7- Archivos YAML saneados de habilidades obsoletas: &e" + sanitized));
        sender.sendMessage(color("&7- Jugadores online sincronizados en memoria: &e" + synced));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(color("&8&m----------------------------------------"));
        sender.sendMessage(color("&b&lAlcatrazCoreOptimizer &7v" + plugin.getDescription().getVersion() + " &8- &ePanel Principal"));
        sender.sendMessage(color("&e/" + label + " ps [give|trust|flags|info|view|list] &7- Piedras de protección."));
        sender.sendMessage(color("&e/" + label + " skills [sync|fix <jugador>] &7- Corrige y sincroniza perfiles de AuraSkills."));
        sender.sendMessage(color("&e/" + label + " monitor &8| &elag &7- Diagnóstico de TPS, MSPT, RAM y top chunks con lag."));
        sender.sendMessage(color("&e/" + label + " clean &8| &egc &7- Limpieza profunda de memoria y entidades residuales."));
        sender.sendMessage(color("&e/" + label + " mobcatcher [jugador] [cant] &7- Entrega Esferas de Contención de mobs."));
        sender.sendMessage(color("&e/" + label + " claim [create_perm|delete|list] &7- Gestiona protecciones de administrador."));
        sender.sendMessage(color("&e/" + label + " hojasonica [jugador] [cant] &7- Entrega la Hoja Sónica del Warden."));
        sender.sendMessage(color("&e/" + label + " pechera [jugador] [cant] &7- Entrega la Pechera con Élitra."));
        sender.sendMessage(color("&e/" + label + " libro [jugador] [cant] &7- Entrega el libro 'Alas del Inframundo'."));
        sender.sendMessage(color("&e/" + label + " lifemending [jugador] [cant] &7- Entrega el libro 'Life Mending'."));
        sender.sendMessage(color("&e/" + label + " newbie <jugador> <minutos|remove> &7- Gestiona protección de novatos."));
        sender.sendMessage(color("&e/" + label + " monturas &7- Abre el establo de monturas (Zorro, Sniffer, Phantom, Panda)."));
        sender.sendMessage(color("&e/" + label + " bossdefense &7- Defensa 75% proyectiles a bosses (/bossdefense)."));
        sender.sendMessage(color("&e/" + label + " unmute <jugador> &7- Levanta el silencio de chat a un jugador."));
        sender.sendMessage(color("&e/" + label + " mutes &7- Lista los jugadores actualmente silenciados por groserías."));
        sender.sendMessage(color("&e/" + label + " reload &7- Recarga toda la configuración y optimizadores."));
        sender.sendMessage(color("&e/" + label + " version &7- Información y estado del optimizador."));
        sender.sendMessage(color("&8&m----------------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length > 0) {
            String firstArg = args[0].toLowerCase();
            if (firstArg.equals("ps") || firstArg.equals("protectionstones") || firstArg.equals("protes") || firstArg.equals("piedras")) {
                if (protectionCommand != null) {
                    String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                    return protectionCommand.onTabComplete(sender, command, "ps", subArgs);
                }
            }
        }

        if (!sender.hasPermission("alcatraz.admin")) {
            return Collections.emptyList();
        }

        if (args.length >= 2 && (args[0].equalsIgnoreCase("bossdefense") || args[0].equalsIgnoreCase("bossdef") || args[0].equalsIgnoreCase("defensaboss"))) {
            if (plugin.getBossDefenseManager() != null) {
                BossDefenseCommand bdc = new BossDefenseCommand(plugin, plugin.getBossDefenseManager());
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return bdc.onTabComplete(sender, command, alias, subArgs);
            }
        }

        if (args.length == 1) {
            List<String> subs = Arrays.asList("ps", "monturas", "bossdefense", "unmute", "mutes", "skills", "fixskills", "monitor", "clean", "mobcatcher", "claim", "proteccion", "hojasonica", "pechera", "libro", "lifemending", "newbie", "xray", "gancho", "reload", "version", "help");

            List<String> completions = new ArrayList<>();
            for (String sub : subs) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("unmute") || args[0].equalsIgnoreCase("desmutear")) {
                List<String> mutedNames = new ArrayList<>();
                if (plugin.getChatFilterManager() != null) {
                    for (com.alcatrazm.plugin.chatfilter.ChatFilterManager.MuteEntry e : plugin.getChatFilterManager().getActiveMutes().values()) {
                        if (e.playerName.toLowerCase().startsWith(args[1].toLowerCase())) {
                            mutedNames.add(e.playerName);
                        }
                    }
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase()) && !mutedNames.contains(p.getName())) {
                        mutedNames.add(p.getName());
                    }
                }
                return mutedNames;
            }
            if (args[0].equalsIgnoreCase("skills") || args[0].equalsIgnoreCase("fixskills") || args[0].equalsIgnoreCase("auraskills")) {
                return Arrays.asList("sync", "fix");
            }
            if (args[0].equalsIgnoreCase("claim")) {
                return Arrays.asList("create_perm", "delete", "list");
            }
            if (Arrays.asList("mobcatcher", "hojasonica", "pechera", "libro", "lifemending", "newbie").contains(args[0].toLowerCase())) {
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
            }
        }

        if (args.length == 3) {
            if ((args[0].equalsIgnoreCase("skills") || args[0].equalsIgnoreCase("fixskills") || args[0].equalsIgnoreCase("auraskills")) && args[1].equalsIgnoreCase("fix")) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        players.add(p.getName());
                    }
                }
                return players;
            }
            if (args[0].equalsIgnoreCase("newbie")) {
                return Arrays.asList("5", "10", "15", "30", "60", "remove");
            }
            return Arrays.asList("1", "2", "5", "10", "64");
        }

        return Collections.emptyList();
    }
}
