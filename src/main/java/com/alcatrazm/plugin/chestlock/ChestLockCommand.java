package com.alcatrazm.plugin.chestlock;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class ChestLockCommand implements CommandExecutor, TabCompleter {

    private final ChestLockManager lockManager;

    public ChestLockCommand(ChestLockManager lockManager) {
        this.lockManager = lockManager;
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cEste comando solo puede ser ejecutado por jugadores dentro del juego."));
            return true;
        }

        if (args.length == 0) {
            handleLockTarget(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "add":
            case "agregar":
            case "amigo":
                handleAdd(player, args);
                return true;

            case "remove":
            case "quitar":
            case "eliminar":
                handleRemove(player, args);
                return true;

            case "timer":
            case "tiempo":
            case "autoclose":
                handleTimer(player, args);
                return true;

            case "public":
            case "publico":
                handleSetType(player, LockType.PUBLIC);
                return true;

            case "donation":
            case "donacion":
                handleSetType(player, LockType.DONATION);
                return true;

            case "private":
            case "privado":
                handleSetType(player, LockType.PRIVATE);
                return true;

            case "info":
            case "ver":
                handleInfo(player);
                return true;

            case "unlock":
            case "desbloquear":
                handleUnlock(player);
                return true;

            case "help":
            case "ayuda":
            default:
                sendHelp(player, label);
                return true;
        }
    }

    private Block getTargetBlock(Player player) {
        return player.getTargetBlockExact(5);
    }

    private void handleLockTarget(Player player) {
        Block target = getTargetBlock(player);
        if (target == null || !lockManager.isLockable(target)) {
            player.sendMessage(color("&cDebes estar mirando un cofre, barril, horno o puerta para bloquearlo."));
            return;
        }

        LockedBlock existing = lockManager.getLock(target);
        if (existing != null) {
            player.sendMessage(color("&cEste bloque ya está bloqueado y pertenece a &e" + existing.getOwnerName() + "&c."));
            return;
        }

        boolean isDoor = lockManager.isDoor(target);
        lockManager.lockBlock(target, player, LockType.PRIVATE, isDoor ? 3 : 0, isDoor);
        player.playSound(target.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.2f);

        player.sendMessage(color("&8&m----------------------------------------"));
        player.sendMessage(color("&a&l¡BLOQUEO ACTIVADO CON ÉXITO!"));
        player.sendMessage(color("&7Dueño: &e" + player.getName() + " &8| &7Tipo: &bPrivado"));
        if (isDoor) {
            player.sendMessage(color("&7Auto-Cierre: &a3 segundos tras abrirse."));
            player.sendMessage(color("&a✓ Cartel invisible integrado en la puerta."));
        }
        player.sendMessage(color("&7Usa &e/lock add <amigo> &7para dar permisos a compañeros."));
        player.sendMessage(color("&8&m----------------------------------------"));
    }

    private void handleAdd(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(color("&cUso: /lock add <nombre_amigo>"));
            return;
        }

        Block target = getTargetBlock(player);
        if (target == null) {
            player.sendMessage(color("&cDebes estar mirando tu cofre o puerta."));
            return;
        }

        LockedBlock lock = lockManager.getLock(target);
        if (lock == null) {
            player.sendMessage(color("&cEste bloque no está bloqueado. Usa &e/lock &cprimero."));
            return;
        }

        if (!lock.isOwner(player.getUniqueId()) && !player.hasPermission("alcatraz.lock.admin")) {
            player.sendMessage(color("&cSolo el dueño puede añadir amigos a este candado."));
            return;
        }

        String friend = args[1].trim();
        lock.addTrust(friend);
        lockManager.saveLocks();

        player.sendMessage(color("&8[&a🔒 Candado&8] &aHas concedido acceso a &e" + friend + " &aen este bloque."));
    }

    private void handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(color("&cUso: /lock remove <nombre_amigo>"));
            return;
        }

        Block target = getTargetBlock(player);
        if (target == null) {
            player.sendMessage(color("&cDebes estar mirando tu cofre o puerta."));
            return;
        }

        LockedBlock lock = lockManager.getLock(target);
        if (lock == null) {
            player.sendMessage(color("&cEste bloque no está bloqueado."));
            return;
        }

        if (!lock.isOwner(player.getUniqueId()) && !player.hasPermission("alcatraz.lock.admin")) {
            player.sendMessage(color("&cSolo el dueño puede remover amigos de este candado."));
            return;
        }

        String friend = args[1].trim();
        lock.removeTrust(friend);
        lockManager.saveLocks();

        player.sendMessage(color("&8[&a🔒 Candado&8] &cHas revocado el acceso de &e" + friend + " &cen este bloque."));
    }

    private void handleTimer(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(color("&cUso: /lock timer <segundos> (ej: /lock timer 5)"));
            return;
        }

        Block target = getTargetBlock(player);
        if (target == null || !lockManager.isDoor(target)) {
            player.sendMessage(color("&cDebes estar mirando una puerta o trampilla bloqueada."));
            return;
        }

        LockedBlock lock = lockManager.getLock(target);
        if (lock == null) {
            player.sendMessage(color("&cEsta puerta no está bloqueada. Usa &e/lock &cprimero."));
            return;
        }

        if (!lock.isOwner(player.getUniqueId()) && !player.hasPermission("alcatraz.lock.admin")) {
            player.sendMessage(color("&cSolo el dueño puede configurar el temporizador."));
            return;
        }

        try {
            int seconds = Integer.parseInt(args[1]);
            if (seconds < 0 || seconds > 60) {
                player.sendMessage(color("&cEl tiempo debe estar entre 0 (sin auto-cierre) y 60 segundos."));
                return;
            }

            lock.setAutoCloseSeconds(seconds);
            lockManager.saveLocks();

            if (seconds > 0) {
                player.sendMessage(color("&8[&a🔒 Candado&8] &aAuto-cierre establecido en &e" + seconds + " segundos&a."));
            } else {
                player.sendMessage(color("&8[&a🔒 Candado&8] &eAuto-cierre desactivado para esta puerta."));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(color("&cPor favor ingresa un número válido de segundos."));
        }
    }

    private void handleSetType(Player player, LockType type) {
        Block target = getTargetBlock(player);
        if (target == null) {
            player.sendMessage(color("&cDebes estar mirando tu cofre o contenedor."));
            return;
        }

        LockedBlock lock = lockManager.getLock(target);
        if (lock == null) {
            player.sendMessage(color("&cEste bloque no está bloqueado. Usa &e/lock &cprimero."));
            return;
        }

        if (!lock.isOwner(player.getUniqueId()) && !player.hasPermission("alcatraz.lock.admin")) {
            player.sendMessage(color("&cSolo el dueño puede cambiar el modo de este candado."));
            return;
        }

        lock.setLockType(type);
        lockManager.saveLocks();
        player.sendMessage(color("&8[&a🔒 Candado&8] &7Tipo de candado cambiado a: &e" + type.getDisplay()));
    }

    private void handleInfo(Player player) {
        Block target = getTargetBlock(player);
        if (target == null) {
            player.sendMessage(color("&cDebes estar mirando un cofre o puerta."));
            return;
        }

        LockedBlock lock = lockManager.getLock(target);
        if (lock == null) {
            player.sendMessage(color("&7Este bloque &ano tiene candado &7(está libre)."));
            return;
        }

        player.sendMessage(color("&8&m----------------------------------------"));
        player.sendMessage(color("&6&lInformación del Candado Privado"));
        player.sendMessage(color("&7Dueño: &e" + lock.getOwnerName()));
        player.sendMessage(color("&7Tipo: &b" + lock.getLockType().getDisplay()));
        if (lock.isDoor()) {
            player.sendMessage(color("&7Auto-Cierre: &a" + (lock.getAutoCloseSeconds() > 0 ? lock.getAutoCloseSeconds() + "s" : "Desactivado")));
            player.sendMessage(color("&7Cartel en puerta: " + (lock.isHiddenSignOnDoor() ? "&aInvisible / Integrado" : "&eVisible")));
        }
        player.sendMessage(color("&7Miembros con acceso: &e" + (lock.getTrustedNames().isEmpty() ? "&7Ninguno" : String.join("&7, &e", lock.getTrustedNames()))));
        player.sendMessage(color("&8&m----------------------------------------"));
    }

    private void handleUnlock(Player player) {
        Block target = getTargetBlock(player);
        if (target == null) {
            player.sendMessage(color("&cDebes estar mirando tu cofre o puerta."));
            return;
        }

        LockedBlock lock = lockManager.getLock(target);
        if (lock == null) {
            player.sendMessage(color("&cEste bloque no tiene ningún candado activo."));
            return;
        }

        if (!lock.isOwner(player.getUniqueId()) && !player.hasPermission("alcatraz.lock.admin")) {
            player.sendMessage(color("&cSolo el dueño o un administrador pueden retirar este candado."));
            return;
        }

        lockManager.unlockBlock(target);
        player.playSound(target.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE, 1.0f, 1.0f);
        player.sendMessage(color("&8[&a🔒 Candado&8] &aCandado retirado con éxito. El bloque ahora es de acceso libre."));
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(color("&8&m----------------------------------------"));
        player.sendMessage(color("&6&lSistema de Cofres y Puertas Privadas - Alcatraz"));
        player.sendMessage(color("&e/" + label + " &7- Bloquea el cofre o puerta que estás mirando."));
        player.sendMessage(color("&e/" + label + " add <amigo> &7- Da acceso a un amigo."));
        player.sendMessage(color("&e/" + label + " remove <amigo> &7- Revoca el acceso a un amigo."));
        player.sendMessage(color("&e/" + label + " timer <segundos> &7- Configura auto-cierre en puertas."));
        player.sendMessage(color("&e/" + label + " public &7- Convierte en público protegido."));
        player.sendMessage(color("&e/" + label + " donation &7- Convierte en cofre de donaciones."));
        player.sendMessage(color("&e/" + label + " info &7- Muestra información del candado."));
        player.sendMessage(color("&e/" + label + " unlock &7- Retira el candado."));
        player.sendMessage(color("&7💡 &fTip: También puedes agacharte y hacer clic derecho con un cartel en la mano."));
        player.sendMessage(color("&8&m----------------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = Arrays.asList("add", "remove", "timer", "public", "donation", "private", "info", "unlock", "help");
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) list.add(s);
            }
            return list;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            for (Player p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) list.add(p.getName());
            }
            return list;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("timer")) {
            return Arrays.asList("3", "5", "10", "15", "0");
        }

        return list;
    }
}
