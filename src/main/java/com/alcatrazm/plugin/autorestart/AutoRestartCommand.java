package com.alcatrazm.plugin.autorestart;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Comando maestro de reinicios automáticos compatible con UltimateAutoRestart y PiponAutoRestart.
 */
public class AutoRestartCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_SUBS = List.of("now", "delay", "stop", "time", "reload", "version", "help");
    private static final List<String> TIME_SUGGESTIONS = List.of("10", "30", "60", "300", "600", "1800");

    private final AlcatrazPlugin plugin;
    private final AutoRestartManager manager;

    public AutoRestartCommand(AlcatrazPlugin plugin, AutoRestartManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("uar.admin")
                || sender.hasPermission("alcatraz.autorestart.admin")
                || sender.hasPermission("alcatraz.admin")
                || sender.hasPermission("autorestart.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!manager.isEnabled()) {
            sender.sendMessage(AutoRestartManager.formatComponent(
                    "&8[&bAlcatraz&8] &cEl módulo de reinicios automáticos está desactivado en config.yml."));
            return true;
        }

        if (!isAdmin(sender)) {
            sendRemainingTime(sender);
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            sendRemainingTime(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "time", "info" -> {
                sendRemainingTime(sender);
                return true;
            }
            case "reload" -> {
                plugin.reloadConfig();
                manager.reload();
                sender.sendMessage(AutoRestartManager.formatComponent(
                        manager.getPrefix() + " &f» &aConfiguración y horarios de reinicio recargados."));
                return true;
            }
            case "version" -> {
                sender.sendMessage(AutoRestartManager.formatComponent(
                        manager.getPrefix() + " &f» &7Módulo nativo integrado en &bAlcatrazCoreOptimizer v"
                                + plugin.getDescription().getVersion()));
                return true;
            }
            case "now", "force" -> {
                handleNow(sender, args, label);
                return true;
            }
            case "delay" -> {
                handleDelay(sender, args, label);
                return true;
            }
            case "stop", "cancel" -> {
                if (manager.stopRestart()) {
                    sender.sendMessage(AutoRestartManager.formatComponent(
                            manager.getPrefix() + " &f» &eReinicio programado detenido."));
                } else {
                    sender.sendMessage(AutoRestartManager.formatComponent(
                            manager.getPrefix() + " &f» &cNo había ningún temporizador activo."));
                    manager.playSound(sender, manager.getInvalidSoundName(), 1.0f, 1.0f);
                }
                return true;
            }
            case "help", "?" -> {
                sendHelp(sender, label);
                return true;
            }
            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    private void handleNow(CommandSender sender, String[] args, String label) {
        int seconds = 60;
        int reasonStart = 1;
        if (args.length >= 2) {
            int parsed = manager.parseTimeArgument(args[1]);
            if (parsed > 0) {
                seconds = parsed;
                reasonStart = 2;
            } else if (args[1].matches("^\\d+.*")) {
                sender.sendMessage(AutoRestartManager.formatComponent(
                        manager.getPrefix() + " &f» &cTiempo inválido. Uso: /" + label + " now [segundos|5m|1:30] [razón]"));
                manager.playSound(sender, manager.getInvalidSoundName(), 1.0f, 1.0f);
                return;
            }
        }
        String reason = null;
        if (args.length > reasonStart) {
            reason = String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length));
        }
        manager.forceRestart(seconds, reason);
    }

    private void handleDelay(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            sender.sendMessage(AutoRestartManager.formatComponent(
                    manager.getPrefix() + " &f» &cUso: /" + label + " delay <segundos|10m|0:30>"));
            manager.playSound(sender, manager.getInvalidSoundName(), 1.0f, 1.0f);
            return;
        }
        int added = manager.parseTimeArgument(args[1]);
        if (added <= 0) {
            sender.sendMessage(AutoRestartManager.formatComponent(
                    manager.getPrefix() + " &f» &cDebes indicar un tiempo positivo válido (ej. 300 o 5m)."));
            manager.playSound(sender, manager.getInvalidSoundName(), 1.0f, 1.0f);
            return;
        }
        if (!manager.isRunning()) {
            sender.sendMessage(AutoRestartManager.formatComponent(
                    manager.getPrefix() + " &f» &cNo hay un reinicio programado activo para retrasar."));
            manager.playSound(sender, manager.getInvalidSoundName(), 1.0f, 1.0f);
            return;
        }
        manager.delayRestart(added);
    }

    private void sendRemainingTime(CommandSender sender) {
        sender.sendMessage(AutoRestartManager.formatComponent(manager.getInfoMessage()));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(AutoRestartManager.formatComponent("&8&m----------------------------------------"));
        sender.sendMessage(AutoRestartManager.formatComponent(manager.getPrefix() + " &f» &eComandos de Reinicio"));
        sender.sendMessage(AutoRestartManager.formatComponent(" &7/" + label + " time &8- &fMuestra el tiempo hasta el próximo reinicio"));
        sender.sendMessage(AutoRestartManager.formatComponent(" &7/" + label + " now [tiempo] [razón] &8- &fFuerza un reinicio"));
        sender.sendMessage(AutoRestartManager.formatComponent(" &7/" + label + " delay <tiempo> &8- &fRetrasa el reinicio actual"));
        sender.sendMessage(AutoRestartManager.formatComponent(" &7/" + label + " stop &8- &fDetiene el temporizador actual"));
        sender.sendMessage(AutoRestartManager.formatComponent(" &7/" + label + " reload &8- &fRecarga la configuración y horarios"));
        sender.sendMessage(AutoRestartManager.formatComponent("&8&m----------------------------------------"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!isAdmin(sender)) {
            return args.length == 1 && "time".startsWith(args[0].toLowerCase(Locale.ROOT))
                    ? List.of("time")
                    : Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> matches = new ArrayList<>();
            String prefixArg = args[0].toLowerCase(Locale.ROOT);
            for (String sub : ADMIN_SUBS) {
                if (sub.startsWith(prefixArg)) {
                    matches.add(sub);
                }
            }
            return matches;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("now") || args[0].equalsIgnoreCase("delay"))) {
            List<String> matches = new ArrayList<>();
            String prefixArg = args[1].toLowerCase(Locale.ROOT);
            for (String t : TIME_SUGGESTIONS) {
                if (t.startsWith(prefixArg)) {
                    matches.add(t);
                }
            }
            return matches;
        }

        return Collections.emptyList();
    }
}
