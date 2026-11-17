package com.alcatrazm.plugin.commandguard;

import com.alcatrazm.plugin.AlcatrazPlugin;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Motor central de resolución de visibilidad y permisos reales por rango para comandos (CommandGuard).
 */
public final class CommandGuardManager {

    private record CommandSnapshot(
            Map<String, String> canonicalByLabel,
            Map<String, String> permissionByLabel
    ) {
        static final CommandSnapshot EMPTY = new CommandSnapshot(Collections.emptyMap(), Collections.emptyMap());
    }

    private record GuardConfig(
            boolean enabled,
            String bypassPermission,
            String deniedMessage,
            boolean blockNamespaced,
            boolean unknownCommandDefaultAllow,
            boolean strictAllowlistMode,
            boolean blockDottedCommands,
            Set<String> blockedCommands,
            Set<String> blockedPrefixes,
            Set<String> hideFromTab,
            Set<String> alwaysAllow,
            Set<String> allowedNamespaced,
            Map<String, List<String>> permissionMap,
            Map<String, List<String>> subcommandPermissions
    ) {
        static final GuardConfig EMPTY = new GuardConfig(
                true,
                "alcatraz.commandguard.bypass",
                "&8[&c✖&8] &cComando desconocido o no disponible para tu rango.",
                true,
                false,
                true,
                true,
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
    }

    private final AlcatrazPlugin plugin;
    private volatile GuardConfig config = GuardConfig.EMPTY;
    private volatile CommandSnapshot snapshot = CommandSnapshot.EMPTY;

    public CommandGuardManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("command-guard.enabled", true);
    }

    public void reload() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("command-guard");
        if (sec == null) {
            this.config = GuardConfig.EMPTY;
            refreshCommandMapSnapshot();
            return;
        }

        boolean enabled = sec.getBoolean("enabled", true);
        String bypassPerm = sec.getString("bypass-permission", "alcatraz.commandguard.bypass");
        String deniedMsg = sec.getString("messages.denied", "&8[&c✖&8] &cComando desconocido o no disponible para tu rango.");
        boolean blockNamespaced = sec.getBoolean("block-namespaced", true);
        boolean unknownDefaultAllow = sec.getBoolean("unknown-command-default-allow", false);
        boolean strictAllowlist = sec.getBoolean("strict-allowlist-mode", true);
        boolean blockDotted = sec.getBoolean("block-dotted-commands", true);

        Set<String> blocked = toLowerSet(sec.getStringList("blocked-commands"));
        Set<String> blockedPrefixes = toLowerSet(sec.getStringList("blocked-prefixes"));
        Set<String> hideFromTab = toLowerSet(sec.getStringList("hide-from-tab"));
        Set<String> alwaysAllow = toLowerSet(sec.getStringList("always-allow"));
        Set<String> allowedNamespaced = toLowerSet(sec.getStringList("allowed-namespaced"));

        Map<String, List<String>> permMap = parsePermissionMap(sec.getConfigurationSection("permission-map"), false);
        Map<String, List<String>> subMap = parsePermissionMap(sec.getConfigurationSection("subcommand-permissions"), true);

        this.config = new GuardConfig(
                enabled,
                bypassPerm,
                deniedMsg,
                blockNamespaced,
                unknownDefaultAllow,
                strictAllowlist,
                blockDotted,
                Collections.unmodifiableSet(blocked),
                Collections.unmodifiableSet(blockedPrefixes),
                Collections.unmodifiableSet(hideFromTab),
                Collections.unmodifiableSet(alwaysAllow),
                Collections.unmodifiableSet(allowedNamespaced),
                Collections.unmodifiableMap(permMap),
                Collections.unmodifiableMap(subMap)
        );

        refreshCommandMapSnapshot();
    }

    /**
     * Captura en el hilo principal un snapshot inmutable del CommandMap para consultas thread-safe desde eventos async.
     */
    public void refreshCommandMapSnapshot() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::refreshCommandMapSnapshot);
            return;
        }
        try {
            CommandMap map = Bukkit.getCommandMap();
            Map<String, Command> known = map.getKnownCommands();
            Map<String, String> canonical = new HashMap<>(known.size() * 2);
            Map<String, String> perms = new HashMap<>(known.size() * 2);

            for (Map.Entry<String, Command> entry : known.entrySet()) {
                String rawKey = entry.getKey();
                Command cmd = entry.getValue();
                if (rawKey == null || cmd == null) {
                    continue;
                }
                String label = rawKey.toLowerCase(Locale.ROOT);
                String primary = cmd.getName() != null ? cmd.getName().toLowerCase(Locale.ROOT) : label;
                canonical.put(label, primary);
                String perm = cmd.getPermission();
                if (perm != null && !perm.isBlank()) {
                    perms.put(label, perm.trim());
                }
                for (String alias : cmd.getAliases()) {
                    String lowerAlias = alias.toLowerCase(Locale.ROOT);
                    canonical.putIfAbsent(lowerAlias, primary);
                    if (perm != null && !perm.isBlank()) {
                        perms.putIfAbsent(lowerAlias, perm.trim());
                    }
                }
            }
            this.snapshot = new CommandSnapshot(
                    Collections.unmodifiableMap(canonical),
                    Collections.unmodifiableMap(perms)
            );
        } catch (Throwable ex) {
            plugin.getLogger().warning("[CommandGuard] No se pudo capturar CommandMap: " + ex.getMessage());
        }
    }

    public boolean hasBypass(Player player) {
        if (player == null) {
            return false;
        }
        String bypassPerm = config.bypassPermission();
        return player.isOp() || (bypassPerm != null && !bypassPerm.isBlank() && player.hasPermission(bypassPerm));
    }

    public String getDeniedMessage() {
        return ChatColor.translateAlternateColorCodes('&', config.deniedMessage());
    }

    /**
     * Determina si un comando raíz debe ser visible en el autocompletado '/' del jugador.
     */
    public boolean isCommandVisible(Player player, String rawLabel) {
        GuardConfig cfg = this.config;
        if (!cfg.enabled() || hasBypass(player)) {
            return true;
        }
        String label = normalizeLabel(rawLabel);
        if (label.isEmpty()) {
            return false;
        }
        if (cfg.blockNamespaced() && label.contains(":")) {
            return false;
        }
        if (isHardBlocked(label, cfg)) {
            return false;
        }
        if (cfg.hideFromTab().contains(label)) {
            return false;
        }
        String canonical = snapshot.canonicalByLabel().get(label);
        if (canonical != null && cfg.hideFromTab().contains(canonical) && !cfg.alwaysAllow().contains(label) && !cfg.permissionMap().containsKey(label)) {
            return false;
        }
        return hasCommandPermission(player, label, cfg);
    }

    /**
     * Determina si un comando (y opcionalmente su subcomando arg0) puede ser ejecutado por el jugador.
     */
    public boolean canExecute(Player player, String rawLabel, String firstArg) {
        GuardConfig cfg = this.config;
        if (!cfg.enabled() || hasBypass(player)) {
            return true;
        }
        String label = normalizeLabel(rawLabel);
        if (label.isEmpty()) {
            return true;
        }

        if (label.contains(":")) {
            if (cfg.blockNamespaced() && !cfg.allowedNamespaced().contains(label)) {
                return false;
            }
            String plain = label.substring(label.indexOf(':') + 1);
            if (plain.isEmpty() || isHardBlocked(plain, cfg)) {
                return false;
            }
            if (!checkSubcommandPermission(player, label, firstArg, cfg)
                    || !checkSubcommandPermission(player, plain, firstArg, cfg)) {
                return false;
            }
            return hasCommandPermission(player, plain, cfg);
        }

        if (isHardBlocked(label, cfg)) {
            return false;
        }
        if (!checkSubcommandPermission(player, label, firstArg, cfg)) {
            return false;
        }
        return hasCommandPermission(player, label, cfg);
    }

    /**
     * Comprueba si el primer argumento / subcomando específico está permitido para el jugador en tab-complete o ejecución.
     */
    public boolean isSubcommandAllowed(Player player, String rawLabel, String subArg) {
        GuardConfig cfg = this.config;
        if (!cfg.enabled() || hasBypass(player)) {
            return true;
        }
        String label = normalizeLabel(rawLabel);
        if (label.contains(":")) {
            label = label.substring(label.indexOf(':') + 1);
        }
        return checkSubcommandPermission(player, label, subArg, cfg);
    }

    public boolean hasSubcommandRules(String rawLabel) {
        String label = normalizeLabel(rawLabel);
        if (label.contains(":")) {
            label = label.substring(label.indexOf(':') + 1);
        }
        String prefix = label + " ";
        String canonical = snapshot.canonicalByLabel().get(label);
        String canonicalPrefix = canonical != null ? canonical + " " : null;
        for (String key : config.subcommandPermissions().keySet()) {
            if (key.startsWith(prefix) || (canonicalPrefix != null && key.startsWith(canonicalPrefix))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Devuelve la lista ordenada de comandos visibles en '/' para un jugador conectado (diagnóstico).
     */
    public List<String> getVisibleCommandsFor(Player player) {
        Collection<String> candidates = new TreeSet<>();
        candidates.addAll(snapshot.canonicalByLabel().keySet());
        candidates.addAll(config.alwaysAllow());
        candidates.addAll(config.permissionMap().keySet());

        List<String> visible = new ArrayList<>();
        for (String cmd : candidates) {
            if (isCommandVisible(player, cmd)) {
                visible.add(cmd);
            }
        }
        Collections.sort(visible);
        return visible;
    }

    public static String normalizeLabel(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        int space = s.indexOf(' ');
        if (space >= 0) {
            s = s.substring(0, space);
        }
        return s.trim().toLowerCase(Locale.ROOT);
    }

    public static String stripSingleLeadingSlash(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.startsWith("/") ? raw.substring(1) : raw;
    }

    private boolean isHardBlocked(String label, GuardConfig cfg) {
        if (cfg.blockedCommands().contains(label)) {
            return true;
        }
        if (cfg.blockDottedCommands() && label.contains(".") && !label.equals(".s")) {
            return true;
        }
        for (String prefix : cfg.blockedPrefixes()) {
            if (label.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkSubcommandPermission(Player player, String label, String firstArg, GuardConfig cfg) {
        if (firstArg == null || firstArg.isBlank()) {
            return true;
        }
        String arg = firstArg.trim().toLowerCase(Locale.ROOT);
        List<String> required = cfg.subcommandPermissions().get(label + " " + arg);
        if (required == null) {
            String canonical = snapshot.canonicalByLabel().get(label);
            if (canonical != null) {
                required = cfg.subcommandPermissions().get(canonical + " " + arg);
            }
        }
        if (required == null || required.isEmpty()) {
            return true;
        }
        return hasAnyPermission(player, required);
    }

    private boolean hasCommandPermission(Player player, String label, GuardConfig cfg) {
        List<String> mapped = cfg.permissionMap().get(label);
        if (mapped != null) {
            return mapped.isEmpty() || hasAnyPermission(player, mapped);
        }
        if (cfg.alwaysAllow().contains(label)) {
            return true;
        }

        if (cfg.strictAllowlistMode()) {
            return false;
        }

        String canonical = snapshot.canonicalByLabel().get(label);
        if (canonical != null && !canonical.equals(label)) {
            if (isHardBlocked(canonical, cfg)) {
                return false;
            }
            List<String> canonicalMapped = cfg.permissionMap().get(canonical);
            if (canonicalMapped != null) {
                return canonicalMapped.isEmpty() || hasAnyPermission(player, canonicalMapped);
            }
            if (cfg.alwaysAllow().contains(canonical)) {
                return true;
            }
        }

        String bukkitPerm = snapshot.permissionByLabel().get(label);
        if ((bukkitPerm == null || bukkitPerm.isBlank()) && canonical != null) {
            bukkitPerm = snapshot.permissionByLabel().get(canonical);
        }
        if (bukkitPerm != null && !bukkitPerm.isBlank()) {
            String[] nodes = bukkitPerm.split(";");
            for (String node : nodes) {
                String trimmed = node.trim();
                if (!trimmed.isEmpty() && player.hasPermission(trimmed)) {
                    return true;
                }
            }
            return false;
        }

        return cfg.unknownCommandDefaultAllow();
    }

    private boolean hasAnyPermission(Player player, List<String> perms) {
        for (String perm : perms) {
            if (perm != null && !perm.isBlank() && player.hasPermission(perm.trim())) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> toLowerSet(List<String> items) {
        Set<String> out = new HashSet<>();
        if (items == null) {
            return out;
        }
        for (String item : items) {
            if (item != null && !item.isBlank()) {
                out.add(item.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static Map<String, List<String>> parsePermissionMap(ConfigurationSection sec, boolean normalizeSubKey) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (sec == null) {
            return out;
        }
        for (String rawKey : sec.getKeys(false)) {
            if (rawKey == null || rawKey.isBlank()) {
                continue;
            }
            List<String> perms;
            if (sec.isList(rawKey)) {
                perms = sec.getStringList(rawKey);
            } else {
                String single = sec.getString(rawKey);
                perms = (single != null && !single.isBlank()) ? List.of(single.trim()) : Collections.emptyList();
            }
            List<String> cleanPerms = new ArrayList<>(perms.size());
            for (String p : perms) {
                if (p != null && !p.isBlank()) {
                    cleanPerms.add(p.trim());
                }
            }
            String key = rawKey.trim().toLowerCase(Locale.ROOT);
            if (normalizeSubKey) {
                key = key.replaceAll("\\s+", " ");
            }
            out.put(key, Collections.unmodifiableList(cleanPerms));
        }
        return out;
    }
}
