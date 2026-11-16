package com.alcatrazm.plugin.airdrop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Comando /uad (/universairdrop, /airdrop) integrado con el modulo AirDrop de AlcatrazCoreOptimizer.
 */
public final class AirDropCommand implements CommandExecutor, TabCompleter {
    private final AirDropModule module;

    public AirDropCommand(AirDropModule module) {
        this.module = module;
    }

    private boolean hasPerm(CommandSender sender, String suffix) {
        return sender.hasPermission("alcatraz.airdrop." + suffix)
                || sender.hasPermission("universairdrop." + suffix)
                || sender.hasPermission("alcatraz.admin");
    }

    private boolean checkPermission(CommandSender sender, String suffix) {
        if (hasPerm(sender, suffix)) {
            return true;
        }
        String noPerm = this.module.getString("messages.no-permission", "No tienes permiso para esta accion");
        this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().error(MessageFormatter.toSmallCaps(noPerm)));
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!this.module.isEnabled() || !this.module.isActive()) {
            this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().error("El modulo de AirDrops esta desactivado en config.yml"));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player p) {
                if (this.checkPermission(p, "admin")) {
                    this.module.getMenuManager().openMenu(p, MenuManager.Page.MAIN);
                }
            } else {
                this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().detail("/uad | /uad lanzar libre | /uad lista | /uad recargar | /uad info"));
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "panel" -> {
                if (sender instanceof Player p && this.checkPermission(p, "admin")) {
                    this.module.getMenuManager().openMenu(p, MenuManager.Page.MAIN);
                }
            }
            case "cinematica", "cine" -> {
                if (sender instanceof Player p) {
                    boolean nowEnabled = this.module.getCinematicManager().toggleCinematicForPlayer(p);
                    String stateText = nowEnabled ? "ACTIVADA" : "DESACTIVADA";
                    this.module.getMessageFormatter().sendMessage(p, this.module.getMessageFormatter().detail("Cinematica: ")
                            .append(nowEnabled ? this.module.getMessageFormatter().success(stateText) : this.module.getMessageFormatter().error(stateText)));
                }
            }
            case "lanzar" -> {
                if (this.checkPermission(sender, "launch")) {
                    String mode = args.length > 1 ? args[1].toUpperCase(Locale.ROOT) : "LIBRE";
                    this.module.getAirDropManager().launch(mode, sender);
                }
            }
            case "omitir" -> {
                if (sender instanceof Player p) {
                    this.module.getCinematicManager().stopCinematic(p.getUniqueId());
                    this.module.getMessageFormatter().sendMessage(p, this.module.getMessageFormatter().detail(MessageFormatter.toSmallCaps("Cinematica finalizada")));
                }
            }
            case "recargar", "reload" -> {
                if (this.checkPermission(sender, "reload")) {
                    this.module.getPlugin().reloadConfig();
                    this.module.reload();
                    this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().success("CONFIGURACION DE AIRDROPS RECARGADA"));
                }
            }
            case "lista" -> {
                if (this.checkPermission(sender, "manage")) {
                    if (this.module.getAirDropManager().getActiveDrops().isEmpty()) {
                        this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().detail(MessageFormatter.toSmallCaps("No hay drops activos")));
                    } else {
                        for (DropInstance d : this.module.getAirDropManager().getActiveDrops()) {
                            this.module.getMessageFormatter().sendMessage(sender,
                                this.module.getMessageFormatter().gradient("DROP " + d.id + " ")
                                    .append(this.module.getMessageFormatter().detail(d.state.name() + " | " + d.location.getWorld().getName() + " | "))
                                    .append(this.module.getMessageFormatter().coords(d.location))
                            );
                        }
                    }
                }
            }
            case "cancelar" -> {
                if (this.checkPermission(sender, "manage")) {
                    if (args.length < 2) {
                        this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().detail("/uad cancelar <id|todos>"));
                    } else if ("todos".equalsIgnoreCase(args[1])) {
                        this.module.getAirDropManager().cancelAll();
                        this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().success("TODOS LOS DROPS FUERON CANCELADOS"));
                    } else {
                        DropInstance d = this.module.getAirDropManager().getDrop(args[1]);
                        if (d == null) {
                            this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().error("DROP NO ENCONTRADO"));
                        } else {
                            this.module.getAirDropManager().removeDrop(d, "cancelado");
                            this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().success("DROP #" + d.id + " CANCELADO"));
                        }
                    }
                }
            }
            case "tp" -> {
                if (this.checkPermission(sender, "manage") && sender instanceof Player p) {
                    DropInstance d = args.length > 1 ? this.module.getAirDropManager().getDrop(args[1]) : null;
                    if (d == null) {
                        this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().error("/uad tp <id>"));
                    } else {
                        this.module.getCinematicManager().stopCinematic(p.getUniqueId());
                        p.teleport(d.location.clone().add(3.0, 1.0, 0.0));
                        this.module.getMessageFormatter().sendMessage(p, this.module.getMessageFormatter().success("Teletransportado a Drop #" + d.id));
                    }
                }
            }
            case "info", "estado" -> {
                boolean autoOn = this.module.getBoolean("automatic.enabled", false);
                long nextSecs = this.module.getAirDropManager().getSecondsUntilNextAutoDrop(this.module.getTicks());
                this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().gradient("ALCATRAZ AIRDROP ").append(this.module.getMessageFormatter().detail("Integrado (UniversAirDrop)")));
                this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().detail("Automaticos: ").append(autoOn ? this.module.getMessageFormatter().success("ACTIVO (" + MessageFormatter.formatDuration(nextSecs * 1000L) + ")") : this.module.getMessageFormatter().error("DESACTIVADO")));
                this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().detail("Objetos en botin: ").append(this.module.getMessageFormatter().info(String.valueOf(this.module.getLootManager().getItemCount()))));
                this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().detail("Drops activos: ").append(this.module.getMessageFormatter().info(String.valueOf(this.module.getAirDropManager().getActiveAndPendingCount()))));
            }
            default -> this.module.getMessageFormatter().sendMessage(sender, this.module.getMessageFormatter().detail("/uad | /uad lanzar libre | /uad lista | /uad cancelar | /uad info"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            list.addAll(List.of("cine", "omitir", "info"));
            if (hasPerm(sender, "admin")) {
                list.addAll(List.of("panel", "cinematica"));
            }
            if (hasPerm(sender, "launch")) {
                list.add("lanzar");
            }
            if (hasPerm(sender, "reload")) {
                list.add("recargar");
            }
            if (hasPerm(sender, "manage")) {
                list.addAll(List.of("lista", "cancelar", "tp"));
            }
        }
        if (args.length == 2 && "lanzar".equalsIgnoreCase(args[0]) && hasPerm(sender, "launch")) {
            list.add("libre");
        }
        if (args.length == 2 && List.of("cancelar", "tp").contains(args[0].toLowerCase(Locale.ROOT)) && hasPerm(sender, "manage")) {
            if (this.module.getAirDropManager() != null) {
                list.addAll(this.module.getAirDropManager().getActiveDrops().stream().map(d -> d.id).toList());
            }
            if ("cancelar".equalsIgnoreCase(args[0])) {
                list.add("todos");
            }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return list.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
