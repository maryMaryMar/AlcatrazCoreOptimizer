package com.alcatrazm.plugin.bossdefense;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BossDefenseCommand implements CommandExecutor, TabCompleter {

    private final AlcatrazPlugin plugin;
    private final BossDefenseManager manager;

    public BossDefenseCommand(AlcatrazPlugin plugin, BossDefenseManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("alcatraz.bossdefense") && !sender.hasPermission("alcatraz.admin")) {
            sender.sendMessage(color("&cNo tienes permisos para usar este comando."));
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
                manager.reload();
                sender.sendMessage(color("&8[&6BossDefense&8] &a¡Configuración de defensa de bosses recargada con éxito!"));
                return true;

            case "cartel":
            case "notify":
            case "aviso":
                if (args.length < 2) {
                    boolean current = manager.isNotificationEnabled();
                    sender.sendMessage(color("&8[&6BossDefense&8] &eEl cartel/aviso de daño está actualmente: " + (current ? "&aACTIVADO" : "&cDESACTIVADO")));
                    sender.sendMessage(color("&7Para cambiarlo usa: &e/" + label + " cartel <on|off>"));
                    return true;
                }
                boolean enableCartel = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("activar");
                manager.setNotificationEnabled(enableCartel);
                plugin.getConfig().set("boss-defense.notification.enabled", enableCartel);
                plugin.saveConfig();
                sender.sendMessage(color("&8[&6BossDefense&8] &aEl cartel/aviso de absorción de daño a bosses ahora está: " + (enableCartel ? "&aACTIVADO" : "&cDESACTIVADO")));
                return true;

            case "stats":
            case "info":
                sender.sendMessage(color("&8&m----------------------------------------"));
                sender.sendMessage(color("&6&lSISTEMA DE DEFENSA DE BOSSES (PROYECTILES Y ESPADAS)"));
                sender.sendMessage(color("&eEstado Global: " + (manager.isEnabled() ? "&aACTIVO" : "&cDESACTIVADO")));
                sender.sendMessage(color("&eDefensa Proyectiles/Flechas: &6" + String.format("%.0f", manager.getProjectileReductionPercentage()) + "% &7(Reciben solo " + String.format("%.0f", (100.0 - manager.getProjectileReductionPercentage())) + "% de daño)"));
                sender.sendMessage(color("&eDefensa Espadas/Cuerpo a Cuerpo: &6" + String.format("%.0f", manager.getSwordReductionPercentage()) + "% &7(Reciben solo " + String.format("%.0f", (100.0 - manager.getSwordReductionPercentage())) + "% de daño)"));
                sender.sendMessage(color("&eCartel / Aviso de Absorción: " + (manager.isNotificationEnabled() ? "&aACTIVADO &7(" + manager.getNotificationDisplayType() + ")" : "&cDESACTIVADO")));
                sender.sendMessage(color("&eUmbral de Vida Mínima Boss: &c" + manager.getMinHealthThreshold() + " HP &7(50 corazones)"));
                sender.sendMessage(color("&eScoreboard Tags reconocidos: &f" + String.join(", ", manager.getScoreboardTags())));
                sender.sendMessage(color("&ePalabras Clave en Nombres: &f" + String.join(", ", manager.getNameKeywords())));
                sender.sendMessage(color("&8&m----------------------------------------"));
                return true;

            case "set":
                if (args.length < 2) {
                    sender.sendMessage(color("&cUso: /" + label + " set <porcentaje> [sword|projectile|all] &7(ej: /" + label + " set 75 all)"));
                    return true;
                }
                try {
                    double percent = Double.parseDouble(args[1]);
                    if (percent < 0 || percent > 100) {
                        sender.sendMessage(color("&cEl porcentaje debe estar entre 0 y 100."));
                        return true;
                    }
                    String targetType = args.length >= 3 ? args[2].toLowerCase() : "all";
                    if (targetType.equals("sword") || targetType.equals("espada")) {
                        manager.setSwordReductionPercentage(percent);
                        plugin.getConfig().set("boss-defense.sword.reduction-percentage", percent);
                        sender.sendMessage(color("&8[&6BossDefense&8] &aDefensa contra &eEspadas &aactualizada al &e" + percent + "%&a!"));
                    } else if (targetType.equals("projectile") || targetType.equals("proyectil") || targetType.equals("flecha")) {
                        manager.setProjectileReductionPercentage(percent);
                        plugin.getConfig().set("boss-defense.projectile.reduction-percentage", percent);
                        sender.sendMessage(color("&8[&6BossDefense&8] &aDefensa contra &eProyectiles &aactualizada al &e" + percent + "%&a!"));
                    } else {
                        manager.setSwordReductionPercentage(percent);
                        manager.setProjectileReductionPercentage(percent);
                        plugin.getConfig().set("boss-defense.sword.reduction-percentage", percent);
                        plugin.getConfig().set("boss-defense.projectile.reduction-percentage", percent);
                        sender.sendMessage(color("&8[&6BossDefense&8] &aDefensa contra &eEspadas y Proyectiles &aactualizada al &e" + percent + "%&a!"));
                    }
                    plugin.saveConfig();
                } catch (NumberFormatException e) {
                    sender.sendMessage(color("&cPor favor ingresa un número válido."));
                }
                return true;

            case "check":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(color("&cEste comando solo puede ser usado por un jugador mirando hacia un mob."));
                    return true;
                }
                LivingEntity target = getTargetLivingEntity(player);
                if (target == null) {
                    player.sendMessage(color("&cNo estás mirando a ninguna entidad viva a menos de 15 bloques."));
                    return true;
                }
                boolean isBoss = manager.isBoss(target);
                String reason = manager.getBossReason(target);
                double maxHealth = manager.getMaxHealth(target);
                String name = target.getCustomName() != null ? target.getCustomName() : target.getName();

                player.sendMessage(color("&8&m----------------------------------------"));
                player.sendMessage(color("&6&lDIAGNÓSTICO DE ENTIDAD:"));
                player.sendMessage(color("&eNombre: &r" + name + " &7(" + target.getType().name() + ")"));
                player.sendMessage(color("&eVida: &c" + String.format("%.1f", target.getHealth()) + " / " + String.format("%.1f", maxHealth) + " HP"));
                player.sendMessage(color("&e¿Es Boss?: " + (isBoss ? "&a&lSÍ" : "&c&lNO")));
                player.sendMessage(color("&eCriterio detectado: &f" + reason));
                if (isBoss) {
                    player.sendMessage(color("&eDefensa Proyectiles: &6" + String.format("%.0f", manager.getProjectileReductionPercentage()) + "% " + (manager.isProjectileDefenseEnabled() ? "&a(Activa)" : "&c(Inactiva)")));
                    player.sendMessage(color("&eDefensa Espadas: &6" + String.format("%.0f", manager.getSwordReductionPercentage()) + "% " + (manager.isSwordDefenseEnabled() ? "&a(Activa)" : "&c(Inactiva)")));
                    player.sendMessage(color("&eCartel en Pantalla: " + (manager.isNotificationEnabled() ? "&aACTIVADO (" + manager.getNotificationDisplayType() + ")" : "&cDESACTIVADO")));
                }
                player.sendMessage(color("&8&m----------------------------------------"));
                return true;

            case "tag":
                if (!(sender instanceof Player playerTag)) {
                    sender.sendMessage(color("&cEste comando solo puede ser usado por un jugador mirando hacia un mob."));
                    return true;
                }
                LivingEntity targetTag = getTargetLivingEntity(playerTag);
                if (targetTag == null) {
                    playerTag.sendMessage(color("&cNo estás mirando a ninguna entidad viva a menos de 15 bloques."));
                    return true;
                }
                manager.tagAsBoss(targetTag);
                String mobName = targetTag.getCustomName() != null ? targetTag.getCustomName() : targetTag.getName();
                playerTag.sendMessage(color("&8[&6BossDefense&8] &a¡Entidad &e" + mobName + " &atagged como Boss con éxito! Ahora tiene 75% de defensa contra flechas y espadas."));
                return true;

            case "untag":
                if (!(sender instanceof Player playerUntag)) {
                    sender.sendMessage(color("&cEste comando solo puede ser usado por un jugador mirando hacia un mob."));
                    return true;
                }
                LivingEntity targetUntag = getTargetLivingEntity(playerUntag);
                if (targetUntag == null) {
                    playerUntag.sendMessage(color("&cNo estás mirando a ninguna entidad viva a menos de 15 bloques."));
                    return true;
                }
                manager.untagBoss(targetUntag);
                playerUntag.sendMessage(color("&8[&6BossDefense&8] &eSe han eliminado las etiquetas de Boss de la criatura."));
                return true;

            default:
                sendHelp(sender, label);
                return true;
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(color("&8&m----------------------------------------"));
        sender.sendMessage(color("&6&lCOMANDOS DE DEFENSA DE BOSSES (/bossdefense)"));
        sender.sendMessage(color("&e/" + label + " check &7- Verifica si la criatura es Boss y sus defensas"));
        sender.sendMessage(color("&e/" + label + " cartel <on|off> &7- Activa o desactiva el cartel en pantalla"));
        sender.sendMessage(color("&e/" + label + " tag &7- Convierte la criatura en Boss (75% defensa)"));
        sender.sendMessage(color("&e/" + label + " untag &7- Quita la condición de Boss a la criatura"));
        sender.sendMessage(color("&e/" + label + " set <%> [sword|projectile|all] &7- Cambia el % de reducción"));
        sender.sendMessage(color("&e/" + label + " stats &7- Muestra la configuración activa y tags"));
        sender.sendMessage(color("&e/" + label + " reload &7- Recarga la configuración"));
        sender.sendMessage(color("&8&m----------------------------------------"));
    }

    private LivingEntity getTargetLivingEntity(Player player) {
        try {
            Entity target = player.getTargetEntity(15);
            if (target instanceof LivingEntity living && !target.equals(player)) {
                return living;
            }
        } catch (Throwable ignored) {}

        try {
            RayTraceResult result = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(),
                    player.getEyeLocation().getDirection(),
                    15.0,
                    0.5,
                    e -> !e.equals(player) && (e instanceof LivingEntity)
            );
            if (result != null && result.getHitEntity() instanceof LivingEntity living) {
                return living;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("alcatraz.bossdefense") && !sender.hasPermission("alcatraz.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> list = Arrays.asList("check", "cartel", "tag", "untag", "set", "stats", "reload", "help");
            List<String> matches = new ArrayList<>();
            for (String s : list) {
                if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                    matches.add(s);
                }
            }
            return matches;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("cartel") || args[0].equalsIgnoreCase("notify") || args[0].equalsIgnoreCase("aviso")) {
                return Arrays.asList("on", "off");
            }
            if (args[0].equalsIgnoreCase("set")) {
                return Arrays.asList("50", "60", "70", "75", "80", "90");
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return Arrays.asList("all", "sword", "projectile");
        }

        return Collections.emptyList();
    }
}
