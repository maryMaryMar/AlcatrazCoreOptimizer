package com.alcatrazm.plugin.chatfilter;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ChatFilterManager {

    private final AlcatrazPlugin plugin;

    private boolean enabled = true;
    private int maxStrikes = 3;
    private int muteDurationMinutes = 25;
    private String bypassPermission = "alcatraz.chatfilter.bypass";
    private boolean bypassOps = false;
    private String adminPermission = "alcatraz.chatfilter.admin";
    private boolean notifyStaff = true;
    private String warningSound = "ENTITY_VILLAGER_NO";
    private String muteSound = "ENTITY_ENDER_DRAGON_GROWL";

    // Mensajes configurables
    private String msgPrefix = "&8[&c&lFILTRO&8] ";
    private String msgStrikeWarn = "&eTu mensaje fue bloqueado por contener vocabulario inapropiado. &cAdvertencia: &e[&c%strike%&e/&c%max%&e]";
    private String msgStrikeFinalWarn = "&c¡Atención! Al próximo intento serás silenciado durante &4%duration% minutos&c.";
    private String msgMutedNotice = "&4&l¡SILENCIADO! &cHas acumulado %max% advertencias de malas palabras. Estás silenciado por &e%duration% minutos&c.";
    private String msgStillMuted = "&cEstás silenciado por acumular advertencias de groserías. &eTiempo restante: &f%time%";
    private String msgUnmutedTarget = "&a¡Un administrador te ha levantado el silencio! Puedes hablar de nuevo en el chat.";
    private String msgUnmutedSender = "&aHas desmuteado exitosamente al jugador &e%player%&a.";
    private String msgNotMuted = "&eEl jugador &f%player% &eno está silenciado.";
    private String msgStaffStrikeAlert = "&8[&cAlerta Staff&8] &e%player% &cintentó usar groserías &7(%strike%/%max%)&c: &f%message%";
    private String msgStaffMuteAlert = "&8[&cAlerta Staff&8] &e%player% &4ha sido silenciado automáticamente por %duration% minutos tras %max% faltas.";

    // Conjuntos de palabras prohibidas
    private final Set<String> exactBadWords = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> phraseBadWords = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> subBadWords = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Sistema de strikes (en memoria por sesión)
    private final Map<UUID, Integer> playerStrikes = new ConcurrentHashMap<>();

    // Sistema de Mutes persistentes (mutes.yml)
    private final Map<UUID, MuteEntry> activeMutes = new ConcurrentHashMap<>();
    private File mutesFile;
    private YamlConfiguration mutesConfig;

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");
    private static final Pattern REPEATED_LETTERS = Pattern.compile("([a-z0-9])\\1+");

    public ChatFilterManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.mutesFile = new File(plugin.getDataFolder(), "mutes.yml");
        loadConfig();
        loadBadWords();
        loadMutes();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("chat-filter.enabled", true);
        this.maxStrikes = plugin.getConfig().getInt("chat-filter.max-strikes", 3);
        this.muteDurationMinutes = plugin.getConfig().getInt("chat-filter.mute-duration-minutes", 25);
        this.bypassPermission = plugin.getConfig().getString("chat-filter.bypass-permission", "alcatraz.chatfilter.bypass");
        this.bypassOps = plugin.getConfig().getBoolean("chat-filter.bypass-ops", true);
        this.adminPermission = plugin.getConfig().getString("chat-filter.admin-permission", "alcatraz.chatfilter.admin");
        this.notifyStaff = plugin.getConfig().getBoolean("chat-filter.notify-staff", true);
        this.warningSound = plugin.getConfig().getString("chat-filter.warning-sound", "ENTITY_VILLAGER_NO");
        this.muteSound = plugin.getConfig().getString("chat-filter.mute-sound", "ENTITY_ENDER_DRAGON_GROWL");

        ConfigurationSection msgSec = plugin.getConfig().getConfigurationSection("chat-filter.messages");
        if (msgSec != null) {
            this.msgPrefix = msgSec.getString("prefix", msgPrefix);
            this.msgStrikeWarn = msgSec.getString("strike-warn", msgStrikeWarn);
            this.msgStrikeFinalWarn = msgSec.getString("strike-final-warn", msgStrikeFinalWarn);
            this.msgMutedNotice = msgSec.getString("muted-notice", msgMutedNotice);
            this.msgStillMuted = msgSec.getString("still-muted", msgStillMuted);
            this.msgUnmutedTarget = msgSec.getString("unmuted-target", msgUnmutedTarget);
            this.msgUnmutedSender = msgSec.getString("unmuted-sender", msgUnmutedSender);
            this.msgNotMuted = msgSec.getString("not-muted", msgNotMuted);
            this.msgStaffStrikeAlert = msgSec.getString("staff-strike-alert", msgStaffStrikeAlert);
            this.msgStaffMuteAlert = msgSec.getString("staff-mute-alert", msgStaffMuteAlert);
        }
    }

    public void reload() {
        loadConfig();
        loadBadWords();
        loadMutes();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasBypass(Player player) {
        if (player == null) return true;
        if (player.isOp() && bypassOps) {
            return true;
        }
        return player.hasPermission(bypassPermission) ||
               player.hasPermission("alcatraz.admin") ||
               player.hasPermission(adminPermission);
    }

    public void loadBadWords() {
        exactBadWords.clear();
        phraseBadWords.clear();
        subBadWords.clear();

        File wordsFile = new File(plugin.getDataFolder(), "badwords.txt");
        if (!wordsFile.exists()) {
            try {
                plugin.saveResource("badwords.txt", false);
            } catch (Exception e) {
                plugin.getLogger().warning("[ChatFilter] No se pudo guardar badwords.txt por defecto: " + e.getMessage());
            }
        }

        if (!wordsFile.exists()) {
            plugin.getLogger().warning("[ChatFilter] badwords.txt no existe en la carpeta del plugin.");
            return;
        }

        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(wordsFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String normalized = normalize(line);
                if (normalized.isEmpty()) continue;

                if (normalized.contains(" ")) {
                    phraseBadWords.add(normalized);
                } else if (normalized.length() <= 3) {
                    exactBadWords.add(normalized);
                } else {
                    subBadWords.add(normalized);
                }
                count++;
            }
            plugin.getLogger().info("[ChatFilter] Cargadas " + count + " palabras prohibidas desde badwords.txt.");
        } catch (Exception e) {
            plugin.getLogger().severe("[ChatFilter] Error al leer badwords.txt: " + e.getMessage());
        }
    }

    private void loadMutes() {
        activeMutes.clear();
        if (!mutesFile.exists()) {
            try {
                mutesFile.createNewFile();
            } catch (IOException ignored) {}
        }

        mutesConfig = YamlConfiguration.loadConfiguration(mutesFile);
        ConfigurationSection sec = mutesConfig.getConfigurationSection("mutes");
        if (sec == null) return;

        long now = System.currentTimeMillis();
        for (String key : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String name = sec.getString(key + ".name", "Desconocido");
                long until = sec.getLong(key + ".muted-until", 0L);
                String reason = sec.getString(key + ".reason", "Groserías");
                if (until > now) {
                    activeMutes.put(uuid, new MuteEntry(name, until, reason));
                }
            } catch (Exception ignored) {}
        }
    }

    public synchronized void saveMutes() {
        if (mutesConfig == null) {
            mutesConfig = new YamlConfiguration();
        }
        mutesConfig.set("mutes", null);
        for (Map.Entry<UUID, MuteEntry> entry : activeMutes.entrySet()) {
            String path = "mutes." + entry.getKey().toString();
            mutesConfig.set(path + ".name", entry.getValue().playerName);
            mutesConfig.set(path + ".muted-until", entry.getValue().untilMillis);
            mutesConfig.set(path + ".reason", entry.getValue().reason);
        }
        try {
            mutesConfig.save(mutesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[ChatFilter] Error al guardar mutes.yml: " + e.getMessage());
        }
    }

    public boolean isMuted(UUID uuid) {
        MuteEntry entry = activeMutes.get(uuid);
        if (entry == null) return false;
        if (System.currentTimeMillis() >= entry.untilMillis) {
            activeMutes.remove(uuid);
            saveMutes();
            return false;
        }
        return true;
    }

    public String getRemainingMuteTimeFormatted(UUID uuid) {
        MuteEntry entry = activeMutes.get(uuid);
        if (entry == null) return "0s";
        long diff = entry.untilMillis - System.currentTimeMillis();
        if (diff <= 0) return "0s";

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long secs = seconds % 60;

        if (minutes > 0) {
            return String.format("%dm %02ds", minutes, secs);
        } else {
            return seconds + "s";
        }
    }

    public boolean unmute(UUID uuid) {
        if (activeMutes.remove(uuid) != null) {
            playerStrikes.remove(uuid);
            saveMutes();
            return true;
        }
        return false;
    }

    public boolean unmuteByName(String name) {
        for (Map.Entry<UUID, MuteEntry> entry : activeMutes.entrySet()) {
            if (entry.getValue().playerName.equalsIgnoreCase(name)) {
                activeMutes.remove(entry.getKey());
                playerStrikes.remove(entry.getKey());
                saveMutes();
                return true;
            }
        }
        return false;
    }

    public Map<UUID, MuteEntry> getActiveMutes() {
        cleanExpiredMutes();
        return Collections.unmodifiableMap(activeMutes);
    }

    private void cleanExpiredMutes() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Map.Entry<UUID, MuteEntry>> it = activeMutes.entrySet().iterator();
        while (it.hasNext()) {
            if (now >= it.next().getValue().untilMillis) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            saveMutes();
        }
    }

    /**
     * Valida el mensaje y aplica advertencias / mute automático si corresponde.
     */
    public FilterResult checkMessage(Player player, String rawMessage) {
        if (!enabled) {
            return new FilterResult(ResultType.ALLOWED, null);
        }

        UUID uuid = player.getUniqueId();

        // 1. Si está silenciado, denegar de inmediato
        if (isMuted(uuid)) {
            String remaining = getRemainingMuteTimeFormatted(uuid);
            String msg = color(msgPrefix + msgStillMuted.replace("%time%", remaining));
            playSound(player, warningSound);
            return new FilterResult(ResultType.MUTED, msg);
        }

        // 2. Si tiene bypass configurado, permitir
        if (hasBypass(player)) {
            return new FilterResult(ResultType.ALLOWED, null);
        }

        // 3. Análisis de malas palabras
        String detectedWord = findBadWord(rawMessage);
        if (detectedWord == null) {
            return new FilterResult(ResultType.ALLOWED, null);
        }

        // 4. Infracción detectada -> Incrementar strike
        int currentStrike = playerStrikes.getOrDefault(uuid, 0) + 1;

        if (currentStrike < maxStrikes) {
            playerStrikes.put(uuid, currentStrike);
            playSound(player, warningSound);

            String warn = color(msgPrefix + msgStrikeWarn
                    .replace("%strike%", String.valueOf(currentStrike))
                    .replace("%max%", String.valueOf(maxStrikes)));
            player.sendMessage(warn);

            if (currentStrike == maxStrikes - 1) {
                String finalWarn = color(msgPrefix + msgStrikeFinalWarn
                        .replace("%duration%", String.valueOf(muteDurationMinutes)));
                player.sendMessage(finalWarn);
            }

            notifyStaffStrike(player, currentStrike, maxStrikes, rawMessage);
            return new FilterResult(ResultType.BLOCKED_WARN, warn);
        } else {
            // Mute automático al alcanzar el tope de strikes
            playerStrikes.remove(uuid);
            long until = System.currentTimeMillis() + ((long) muteDurationMinutes * 60 * 1000);
            activeMutes.put(uuid, new MuteEntry(player.getName(), until, "Acumulación de " + maxStrikes + " groserías"));
            saveMutes();

            playSound(player, muteSound);

            String muteMsg = color(msgPrefix + msgMutedNotice
                    .replace("%max%", String.valueOf(maxStrikes))
                    .replace("%duration%", String.valueOf(muteDurationMinutes)));
            player.sendMessage(muteMsg);

            notifyStaffMute(player, muteDurationMinutes, maxStrikes);
            return new FilterResult(ResultType.BLOCKED_MUTED, muteMsg);
        }
    }

    /**
     * Busca si el texto contiene alguna grosería mediante normalizaciones múltiples.
     */
    public String findBadWord(String rawText) {
        if (rawText == null || rawText.isEmpty()) return null;

        String norm1 = normalize(rawText);
        String norm2 = decodeLeetspeak(norm1);
        String norm3 = NON_ALPHANUMERIC.matcher(norm2).replaceAll("");
        String norm4 = REPEATED_LETTERS.matcher(norm3).replaceAll("$1");

        // 1. Frases completas (ej. "hijo de puta", "2 girls 1 cup")
        for (String phrase : phraseBadWords) {
            if (norm1.contains(phrase) || norm2.contains(phrase)) {
                return phrase;
            }
        }

        // 2. Tokenizar palabras para comprobación exacta por palabra (evita falsos positivos de subcadena como 'computadora' -> 'puta' o 'hermano' -> 'ano')
        String[] tokens = norm2.split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (exactBadWords.contains(token) || subBadWords.contains(token)) {
                return token;
            }
        }

        return null;
    }

    public static String normalize(String input) {
        if (input == null) return "";
        String lower = input.toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(decomposed).replaceAll("");
    }

    public static String decodeLeetspeak(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '@':
                case '4':
                    sb.append('a');
                    break;
                case '3':
                    sb.append('e');
                    break;
                case '1':
                case '!':
                case '|':
                    sb.append('i');
                    break;
                case '0':
                    sb.append('o');
                    break;
                case '5':
                case '$':
                    sb.append('s');
                    break;
                case '7':
                    sb.append('t');
                    break;
                case '8':
                    sb.append('b');
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.trim().isEmpty()) return;
        try {
            Sound sound = Sound.valueOf(soundName.trim().toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    private void notifyStaffStrike(Player violator, int strike, int max, String msg) {
        if (!notifyStaff) return;
        String alert = color(msgStaffStrikeAlert
                .replace("%player%", violator.getName())
                .replace("%strike%", String.valueOf(strike))
                .replace("%max%", String.valueOf(max))
                .replace("%message%", msg));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(adminPermission) || online.isOp()) {
                online.sendMessage(alert);
            }
        }
    }

    private void notifyStaffMute(Player violator, int duration, int max) {
        if (!notifyStaff) return;
        String alert = color(msgStaffMuteAlert
                .replace("%player%", violator.getName())
                .replace("%duration%", String.valueOf(duration))
                .replace("%max%", String.valueOf(max)));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(adminPermission) || online.isOp()) {
                online.sendMessage(alert);
            }
        }
    }

    private String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public String getMsgPrefix() { return msgPrefix; }
    public String getMsgUnmutedTarget() { return msgUnmutedTarget; }
    public String getMsgUnmutedSender() { return msgUnmutedSender; }
    public String getMsgNotMuted() { return msgNotMuted; }

    public static class MuteEntry {
        public final String playerName;
        public final long untilMillis;
        public final String reason;

        public MuteEntry(String playerName, long untilMillis, String reason) {
            this.playerName = playerName;
            this.untilMillis = untilMillis;
            this.reason = reason;
        }
    }

    public enum ResultType {
        ALLOWED,
        MUTED,
        BLOCKED_WARN,
        BLOCKED_MUTED
    }

    public static class FilterResult {
        public final ResultType type;
        public final String message;

        public FilterResult(ResultType type, String message) {
            this.type = type;
            this.message = message;
        }

        public boolean isAllowed() {
            return type == ResultType.ALLOWED;
        }
    }
}
