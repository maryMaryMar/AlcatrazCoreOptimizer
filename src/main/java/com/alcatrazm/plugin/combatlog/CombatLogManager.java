package com.alcatrazm.plugin.combatlog;

import com.alcatrazm.plugin.AlcatrazPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CombatLogManager {

    private final AlcatrazPlugin plugin;
    private final Map<UUID, Long> combatUntil = new ConcurrentHashMap<>();
    private BukkitTask actionbarTask;

    private boolean enabled;
    private int combatDurationSeconds;
    private List<String> blockedCommands;

    public CombatLogManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        startActionbarTask();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("combat-log.enabled", true);
        this.combatDurationSeconds = plugin.getConfig().getInt("combat-log.duration-seconds", 15);
        this.blockedCommands = plugin.getConfig().getStringList("combat-log.blocked-commands");
        if (this.blockedCommands.isEmpty()) {
            this.blockedCommands = Arrays.asList(
                    "/rtp", "/spawn", "/home", "/tpa", "/tpahere", "/pv", "/vault", "/claim", "/fly", "/wild", "/back",
                    "/montura", "/monturas", "/mount", "/mounts", "/pet", "/pets", "/mascota", "/mascotas"
            );
        }
    }

    public void reload() {
        loadConfig();
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public void tagPlayer(Player player) {
        if (!enabled || player == null) return;
        if (player.hasPermission("alcatraz.combatlog.bypass")) return;

        // Auto-despedir montura activa para evitar abusos o escapes injustos en PvP
        if (plugin.getMountManager() != null && plugin.getMountManager().isCombatAutoDismiss()) {
            if (plugin.getMountManager().hasActiveMount(player.getUniqueId())) {
                plugin.getMountManager().dismissMount(player.getUniqueId(), false);
                player.sendMessage(color("&8[&c⚔ Combate&8] &cTu montura ha sido retirada debido al combate PvP."));
            }
        }

        boolean alreadyInCombat = isInCombat(player.getUniqueId());
        long expiration = System.currentTimeMillis() + (combatDurationSeconds * 1000L);
        combatUntil.put(player.getUniqueId(), expiration);

        if (!alreadyInCombat) {
            player.sendMessage(color("&8[&c⚔ Combate&8] &c¡Has entrado en combate! &7No te desconectes ni uses comandos durante &e" + combatDurationSeconds + "s&7."));
        }
    }

    public boolean isInCombat(UUID uuid) {
        if (!enabled || uuid == null) return false;
        Long exp = combatUntil.get(uuid);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            combatUntil.remove(uuid);
            return false;
        }
        return true;
    }

    public int getRemainingSeconds(UUID uuid) {
        Long exp = combatUntil.get(uuid);
        if (exp == null) return 0;
        long diff = exp - System.currentTimeMillis();
        return Math.max(0, (int) Math.ceil(diff / 1000.0));
    }

    public void removePlayer(UUID uuid) {
        combatUntil.remove(uuid);
    }

    public boolean isCommandBlocked(String commandLine) {
        if (commandLine == null) return false;
        String lower = commandLine.toLowerCase().trim();
        if (!lower.startsWith("/")) {
            lower = "/" + lower;
        }

        // Extraer el comando principal y sanitizar prefijos de namespace (ej. /essentials:spawn -> /spawn)
        String[] parts = lower.split("\\s+", 2);
        String mainCmd = parts[0];
        String strippedCmd = mainCmd;
        if (mainCmd.contains(":")) {
            int colonIdx = mainCmd.indexOf(':');
            strippedCmd = "/" + mainCmd.substring(colonIdx + 1);
        }

        for (String blocked : blockedCommands) {
            String b = blocked.toLowerCase().trim();
            if (!b.startsWith("/")) {
                b = "/" + b;
            }
            if (mainCmd.equals(b) || strippedCmd.equals(b)) {
                return true;
            }
        }
        return false;
    }

    private void startActionbarTask() {
        actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled || combatUntil.isEmpty()) return;

            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> entry : combatUntil.entrySet()) {
                UUID uuid = entry.getKey();
                long exp = entry.getValue();

                if (now > exp) {
                    combatUntil.remove(uuid);
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        p.sendMessage(color("&8[&a⚔ Combate&8] &a¡Ya no estás en combate! Estás a salvo."));
                    }
                } else {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        int rem = (int) Math.ceil((exp - now) / 1000.0);
                        String msg = color("&c⚔ ¡En Combate! &e" + rem + "s &crestantes &7(No te desconectes)");
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
                    }
                }
            }
        }, 10L, 20L);
    }

    public void stopTask() {
        if (actionbarTask != null && !actionbarTask.isCancelled()) {
            actionbarTask.cancel();
            actionbarTask = null;
        }
        combatUntil.clear();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
