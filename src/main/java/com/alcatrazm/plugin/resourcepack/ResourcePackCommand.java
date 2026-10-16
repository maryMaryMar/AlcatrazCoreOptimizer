package com.alcatrazm.plugin.resourcepack;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ResourcePackCommand implements CommandExecutor, TabCompleter {

    private final AlcatrazPlugin plugin;
    private final ResourcePackManager manager;

    public ResourcePackCommand(AlcatrazPlugin plugin, ResourcePackManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private boolean canUseForceBD(CommandSender sender) {
        return sender.hasPermission("alcatraz.pack.forcebd")
                || sender.hasPermission("alcatraz.pack.staff")
                || sender.hasPermission("alcatraz.admin")
                || sender.hasPermission("staff");
    }

    private boolean canUseForceJava(CommandSender sender) {
        return sender.hasPermission("alcatraz.pack.force")
                || sender.hasPermission("alcatraz.pack.staff")
                || sender.hasPermission("alcatraz.admin")
                || sender.hasPermission("staff");
    }

    private boolean canUseReset(CommandSender sender) {
        return sender.hasPermission("alcatraz.pack.reset")
                || sender.hasPermission("alcatraz.pack.staff")
                || sender.hasPermission("alcatraz.admin")
                || sender.hasPermission("staff");
    }

    private boolean canUseStaff(CommandSender sender) {
        return canUseForceBD(sender) || canUseForceJava(sender) || canUseReset(sender)
                || sender.hasPermission("alcatraz.pack.staff")
                || sender.hasPermission("alcatraz.admin")
                || sender.hasPermission("staff");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName().toLowerCase();
        String labelName = label.toLowerCase();

        // 1. Si se invoca directamente el comando /forcebd o /forcebedrock
        if (cmdName.equals("forcebd") || labelName.equals("forcebd") || labelName.equals("forcebedrock")) {
            if (!canUseForceBD(sender)) {
                sender.sendMessage(color("&cNo tienes permisos del Staff para forzar texturas a jugadores de Bedrock."));
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage(color("&cUso: /" + label + " <jugador|all>"));
                return true;
            }
            handleForceBedrockCommand(sender, args[0]);
            return true;
        }

        // 2. Ejecución desde CONSOLA para comandos de /pack
        if (!(sender instanceof Player)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                manager.reload();
                sender.sendMessage(color("&a[Alcatraz] Configuración de ResourcePack recargada exitosamente."));
                return true;
            }
            if (args.length > 1 && (args[0].equalsIgnoreCase("forcebd") || args[0].equalsIgnoreCase("forzarbd"))) {
                handleForceBedrockCommand(sender, args[1]);
                return true;
            }
            if (args.length > 1 && (args[0].equalsIgnoreCase("force") || args[0].equalsIgnoreCase("send") || args[0].equalsIgnoreCase("forzar"))) {
                handleForceJavaCommand(sender, args[1]);
                return true;
            }
            if (args.length > 1 && args[0].equalsIgnoreCase("reset")) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    manager.resetPlayer(target.getUniqueId());
                    sender.sendMessage(color("&a[Alcatraz] Se ha reseteado el registro de texturas para &e" + target.getName()));
                } else {
                    sender.sendMessage(color("&cJugador no encontrado."));
                }
                return true;
            }
            sender.sendMessage(color("&eComandos de consola:"));
            sender.sendMessage(color("&b/pack forceBD <jugador|all> &7- Fuerza la sincronización exclusiva para Bedrock."));
            sender.sendMessage(color("&b/pack force <jugador|all>   &7- Fuerza la descarga para jugadores de Java."));
            sender.sendMessage(color("&b/pack reset <jugador>       &7- Resetea el registro de un jugador."));
            sender.sendMessage(color("&b/pack reload               &7- Recarga la configuración."));
            return true;
        }

        // 3. Ejecución por JUGADOR dentro del juego
        Player player = (Player) sender;
        boolean isBedrock = BedrockDetector.isBedrockPlayer(player);

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(player, isBedrock);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "forcebd":
            case "forcebedrock":
            case "forzarbd":
                if (!canUseForceBD(player)) {
                    player.sendMessage(color("&cNo tienes permisos del Staff para forzar la carga de texturas a jugadores de Bedrock."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(color("&cUso: /pack forceBD <jugador|all>"));
                    return true;
                }
                handleForceBedrockCommand(player, args[1]);
                break;

            case "force":
            case "send":
            case "forzar":
            case "enviar":
                if (!canUseForceJava(player)) {
                    player.sendMessage(color("&cNo tienes permisos del Staff para forzar la carga de texturas a otros jugadores."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(color("&cUso: /pack force <jugador|all>"));
                    return true;
                }
                handleForceJavaCommand(player, args[1]);
                break;

            case "download":
            case "descargar":
            case "instalar":
                if (isBedrock) {
                    player.sendMessage(color("&8[&bAlcatraz&8] &eEstás jugando desde &bBedrock Edition&e. Tus texturas están completamente sincronizadas mediante Geyser."));
                    return true;
                }
                player.sendMessage(color("&8[&bAlcatraz&8] &aEnviando solicitud de paquete de recursos..."));
                manager.sendResourcePack(player, true);
                break;

            case "link":
            case "enlace":
            case "url":
                if (manager.getPackUrl().isEmpty()) {
                    player.sendMessage(color("&8[&bAlcatraz&8] &cNo hay un enlace de paquete configurado en este momento."));
                    return true;
                }
                player.sendMessage(color("&8[&bAlcatraz&8] &7Enlace directo del paquete de texturas oficial (Java):"));
                player.sendMessage(color("&b" + manager.getPackUrl()));
                player.sendMessage(color("&7Instálalo en tu carpeta &e%appdata%\\.minecraft\\resourcepacks &7para tenerlo siempre activo sin tiempos de espera."));
                break;

            case "status":
            case "estado":
            case "info":
                player.sendMessage(color("&8&m----------------------------------------"));
                player.sendMessage(color("&b&lESTADO DE PAQUETE DE TEXTURAS - ALCATRAZ"));
                player.sendMessage(color("&7Plataforma: " + (isBedrock ? "&aBedrock Edition (Geyser)" : "&eJava Edition")));
                if (isBedrock) {
                    player.sendMessage(color("&7Protección Bedrock: &aActiva &7(Sin congelamientos de paquetes Java)"));
                } else {
                    boolean loaded = manager.hasLoadedPack(player.getUniqueId());
                    player.sendMessage(color("&7Registrado como cargado: " + (loaded ? "&aSí &7(Modo sin recarga activo)" : "&cNo")));
                    player.sendMessage(color("&7Omitir envíos automáticos: " + (manager.isOptedOut(player.getUniqueId()) ? "&cSí" : "&aNo")));
                    player.sendMessage(color("&7Hash actual: &8" + (manager.getPackHash().isEmpty() ? "Ninguno" : manager.getPackHash())));
                }
                player.sendMessage(color("&8&m----------------------------------------"));
                break;

            case "toggle":
                if (!player.hasPermission("alcatraz.pack.receive") && !player.isOp()) {
                    player.sendMessage(color("&cNo tienes permiso para modificar tus preferencias de texturas."));
                    return true;
                }
                boolean optedOut = manager.toggleOptOut(player.getUniqueId());
                if (optedOut) {
                    player.sendMessage(color("&8[&bAlcatraz&8] &cHas desactivado la carga automática de texturas al ingresar."));
                } else {
                    player.sendMessage(color("&8[&bAlcatraz&8] &aHas reactivado la carga automática de texturas al ingresar."));
                }
                break;

            case "reset":
                if (!canUseReset(player)) {
                    player.sendMessage(color("&cNo tienes permisos del Staff para resetear registros de texturas."));
                    return true;
                }
                if (args.length < 2) {
                    manager.resetPlayer(player.getUniqueId());
                    player.sendMessage(color("&aSe ha reseteado tu propio registro de texturas. Se te volverá a pedir al ingresar."));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    manager.resetPlayer(target.getUniqueId());
                    player.sendMessage(color("&aSe ha reseteado el registro de texturas de &e" + target.getName()));
                } else {
                    player.sendMessage(color("&cJugador no encontrado."));
                }
                break;

            case "reload":
                if (!player.hasPermission("alcatraz.admin")) {
                    player.sendMessage(color("&cNo tienes permisos para recargar este módulo."));
                    return true;
                }
                manager.reload();
                player.sendMessage(color("&a[Alcatraz] Módulo de ResourcePack recargado correctamente."));
                break;

            default:
                sendHelp(player, isBedrock);
                break;
        }

        return true;
    }

    private void handleForceJavaCommand(CommandSender sender, String targetArg) {
        if (targetArg.equalsIgnoreCase("all") || targetArg.equalsIgnoreCase("*")) {
            int sent = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (BedrockDetector.isBedrockPlayer(p)) continue;
                manager.sendResourcePack(p, true);
                p.sendMessage(color("&8[&bAlcatraz&8] &eUn miembro del Staff ha forzado la carga del paquete de texturas oficial."));
                sent++;
            }
            sender.sendMessage(color("&a[Alcatraz] Paquete de texturas Java enviado forzosamente a &e" + sent + " &ajugadores online (Bedrock omitido)."));
            return;
        }

        Player target = Bukkit.getPlayer(targetArg);
        if (target == null) {
            sender.sendMessage(color("&cJugador '" + targetArg + "' no encontrado o no está en línea."));
            return;
        }

        if (BedrockDetector.isBedrockPlayer(target)) {
            sender.sendMessage(color("&e[Alcatraz] El jugador &6" + target.getName() + " &eestá en &bBedrock Edition&e."));
            sender.sendMessage(color("&7Para forzar texturas a jugadores de Bedrock usa: &b/pack forceBD " + target.getName()));
            return;
        }

        manager.sendResourcePack(target, true);
        sender.sendMessage(color("&a[Alcatraz] Paquete de texturas Java enviado forzosamente a &e" + target.getName()));
        target.sendMessage(color("&8[&bAlcatraz&8] &eUn miembro del Staff ha forzado la carga de tu paquete de texturas."));
    }

    private void handleForceBedrockCommand(CommandSender sender, String targetArg) {
        if (targetArg.equalsIgnoreCase("all") || targetArg.equalsIgnoreCase("*")) {
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (BedrockDetector.isBedrockPlayer(p)) {
                    applyBedrockForce(p);
                    count++;
                }
            }
            if (count == 0) {
                sender.sendMessage(color("&e[Alcatraz] No hay jugadores de Bedrock conectados actualmente en el servidor."));
            } else {
                sender.sendMessage(color("&a[Alcatraz] Sincronización y recarga de texturas Bedrock forzada con éxito a &e" + count + " &ajugadores de Bedrock."));
            }
            return;
        }

        Player target = Bukkit.getPlayer(targetArg);
        if (target == null) {
            sender.sendMessage(color("&cJugador '" + targetArg + "' no encontrado o no está en línea."));
            return;
        }

        if (!BedrockDetector.isBedrockPlayer(target)) {
            sender.sendMessage(color("&cEl jugador &e" + target.getName() + " &cNO es de Bedrock (está conectado desde Java Edition)."));
            sender.sendMessage(color("&7Para forzar texturas a jugadores de Java usa: &b/pack force " + target.getName()));
            return;
        }

        applyBedrockForce(target);
        sender.sendMessage(color("&a[Alcatraz] Recarga y sincronización de texturas Bedrock forzada con éxito para &e" + target.getName()));
    }

    private void applyBedrockForce(Player target) {
        // 1. Si hay URL dedicada configurada en bedrock.custom-pack-url
        if (manager.getBedrockCustomUrl() != null && !manager.getBedrockCustomUrl().trim().isEmpty()) {
            manager.sendBedrockPack(target);
        }

        // 2. Refrescar inventario y paquetes visuales
        try {
            target.updateInventory();
        } catch (Throwable ignored) {}

        target.sendMessage(color("&8[&bAlcatraz&8] &eUn miembro del Staff ha forzado la sincronización de texturas de Bedrock."));
        target.sendMessage(color("&8[&bAlcatraz&8] &7Si continúas viendo texturas bugeadas, ve a &bAjustes > Almacenamiento > Paquetes de recursos &7y borra la caché del servidor en tu dispositivo."));
    }

    private void sendHelp(Player player, boolean isBedrock) {
        player.sendMessage(color("&8&m----------------------------------------"));
        player.sendMessage(color("&b&lALCATRAZ TEXTURES &8- &7Comandos"));
        if (isBedrock) {
            player.sendMessage(color("&e/pack status &8- &7Verifica el estado de compatibilidad de Bedrock."));
        } else {
            player.sendMessage(color("&e/pack download &8- &7Fuerza la descarga del paquete oficial en tu cliente."));
            player.sendMessage(color("&e/pack link &8- &7Obtén el enlace directo para instalación manual."));
            player.sendMessage(color("&e/pack toggle &8- &7Alterna la descarga automática al entrar."));
            player.sendMessage(color("&e/pack status &8- &7Comprueba si tu cliente lo tiene instalado."));
        }
        if (canUseStaff(player)) {
            player.sendMessage(color("&b/pack forceBD <jugador|all> &8- &6[STAFF BEDROCK] &7Fuerza recarga para Bedrock."));
            player.sendMessage(color("&c/pack force <jugador|all>   &8- &e[STAFF JAVA] &7Fuerza descarga para usuarios de Java."));
            player.sendMessage(color("&c/pack reset <jugador>       &8- &7Borra registro para volver a pedir al entrar."));
        }
        if (player.hasPermission("alcatraz.admin")) {
            player.sendMessage(color("&4/pack reload               &8- &7Recarga la configuración de paquetes."));
        }
        player.sendMessage(color("&8&m----------------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("forcebd")) {
            if (args.length == 1 && canUseForceBD(sender)) {
                list.add("all");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (BedrockDetector.isBedrockPlayer(p)) {
                        list.add(p.getName());
                    }
                }
            }
            return list;
        }

        if (args.length == 1) {
            list.addAll(Arrays.asList("download", "link", "status", "toggle"));
            if (canUseStaff(sender)) {
                if (canUseForceBD(sender)) list.add("forceBD");
                if (canUseForceJava(sender)) list.add("force");
                if (canUseReset(sender)) list.add("reset");
            }
            if (sender.hasPermission("alcatraz.admin")) {
                list.add("reload");
            }
        } else if (args.length == 2 && canUseStaff(sender)) {
            String sub = args[0].toLowerCase();
            if (sub.equals("forcebd") || sub.equals("forzarbd")) {
                list.add("all");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (BedrockDetector.isBedrockPlayer(p)) {
                        list.add(p.getName());
                    }
                }
            } else if (sub.equals("force") || sub.equals("send") || sub.equals("reset")) {
                list.add("all");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    list.add(p.getName());
                }
            }
        }
        return list;
    }
}
