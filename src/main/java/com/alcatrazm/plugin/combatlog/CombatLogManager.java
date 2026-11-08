package com.alcatrazm.plugin.combatlog;

import com.alcatrazm.plugin.AlcatrazPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
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
                    "/rtp", "/spawn", "/home", "/tpa", "/tpahere", "/pv", "/vault", "/claim", "/fly", "/efly", "/wild", "/back",
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

    public boolean isDungeonWorld(World world) {
        if (world == null) return false;
        String w = world.getName().toLowerCase(Locale.ROOT);
        return w.equals("dungeon1") || w.equals("dungeon2");
    }

    public boolean isNoFlyZone(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        String w = loc.getWorld().getName().toLowerCase(Locale.ROOT);
        if (w.equals("dungeon1") || w.equals("dungeon2") || w.equals("duel1") || w.equals("duel2")) {
            return true;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        if (w.equals("mundo")) {
            // Regiones PvP arena / arena2 en mundo
            if (x >= -175 && x <= -107 && z >= -425 && z <= -336) {
                return true;
            }
        } else if (w.equals("lobby")) {
            // Región PvP en lobby
            if (x >= -70 && x <= -3 && y >= 97 && y <= 108 && z >= -156 && z <= -88) {
                return true;
            }
        }
        return false;
    }

    public boolean disableFlightNow(Player player) {
        if (player == null) return false;
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            return false;
        }
        boolean wasFlyingOrAllowed = player.isFlying() || player.getAllowFlight() || player.isGliding();
        if (player.isFlying()) {
            player.setFlying(false);
        }
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
        }
        if (player.isGliding()) {
            player.setGliding(false);
        }
        if (wasFlyingOrAllowed) {
            player.setFallDistance(0.0f);
        }
        return wasFlyingOrAllowed;
    }

    public boolean isFlyCommand(String commandLine) {
        if (commandLine == null) return false;
        String lower = commandLine.toLowerCase(Locale.ROOT).trim();
        if (!lower.startsWith("/")) {
            lower = "/" + lower;
        }
        String[] parts = lower.split("\\s+", 2);
        String mainCmd = parts[0];
        String strippedCmd = mainCmd;
        if (mainCmd.contains(":")) {
            int colonIdx = mainCmd.indexOf(':');
            strippedCmd = "/" + mainCmd.substring(colonIdx + 1);
        }
        return strippedCmd.equals("/fly") || strippedCmd.equals("/efly");
    }

    public void tagPlayer(Player player) {
        if (!enabled || player == null) return;
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;

        // Desactivar vuelo de inmediato por completo al entrar en combate PvP (sin importar permisos de bypass)
        if (disableFlightNow(player)) {
            player.sendMessage(color("&8[&c⚔ Combate&8] &cTu modo de vuelo ha sido desactivado por entrar en combate PvP."));
        }

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
        if (isFlyCommand(commandLine)) return true;

        String lower = commandLine.toLowerCase(Locale.ROOT).trim();
        if (!lower.startsWith("/")) {
            lower = "/" + lower;
        }

        String[] parts = lower.split("\\s+", 2);
        String mainCmd = parts[0];
        String strippedCmd = mainCmd;
        if (mainCmd.contains(":")) {
            int colonIdx = mainCmd.indexOf(':');
            strippedCmd = "/" + mainCmd.substring(colonIdx + 1);
        }

        for (String blocked : blockedCommands) {
            String b = blocked.toLowerCase(Locale.ROOT).trim();
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
            if (!enabled) return;

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
                        disableFlightNow(p);
                        int rem = (int) Math.ceil((exp - now) / 1000.0);
                        String msg = color("&c⚔ ¡En Combate! &e" + rem + "s &crestantes &7(No te desconectes)");
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
                    }
                }
            }

            // Barrido continuo en zonas sin vuelo (dungeons, arenas y duelos)
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (isNoFlyZone(p.getLocation())) {
                    disableFlightNow(p);
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
