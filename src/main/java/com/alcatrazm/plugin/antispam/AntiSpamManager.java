package com.alcatrazm.plugin.antispam;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntiSpamManager {

    private final AlcatrazPlugin plugin;

    private boolean enabled = true;
    private boolean bypassOps = false;
    private String bypassPermission = "alcatraz.antispam.bypass";
    private String adminPermission = "alcatraz.antispam.admin";
    private boolean notifyStaff = true;
    private String warningSound = "ENTITY_VILLAGER_NO";

    private boolean blockLinks = true;
    private boolean blockIps = true;
    private boolean blockDiscord = true;
    private boolean blockSymbolSpam = true;
    private double maxSymbolsPercentage = 65.0;

    private boolean burstEnabled = true;
    private int burstMaxMessages = 4;
    private double burstWindowSeconds = 3.0;

    private final Set<String> whitelist = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Mensajes
    private String msgPrefix = "&8[&c&lSPAM&8] ";
    private String msgBlockedLink = "&cNo está permitido enviar enlaces ni páginas web en el chat.";
    private String msgBlockedIp = "&cNo está permitido publicar direcciones IP o servidores en el chat.";
    private String msgBlockedDiscord = "&cNo está permitido enviar invitaciones de Discord en el chat.";
    private String msgBlockedBurst = "&cEstás enviando demasiados mensajes seguidos. ¡Por favor espera un momento!";
    private String msgBlockedSymbols = "&cTu mensaje contiene demasiados símbolos o caracteres extraños repetidos.";
    private String msgStaffAlert = "&8[&cAlerta Staff&8] &e%player% &cbloqueado por spam/enlace: &f%message%";

    // Registro de ráfagas (Burst spam)
    private final Map<UUID, List<Long>> playerMessageTimestamps = new ConcurrentHashMap<>();

    // Patrones de detección de IPs, Enlaces y Discord
    private static final Pattern IP_PATTERN = Pattern.compile("(?i)\\b(?:[0-9]{1,3}[.,\\-_ ]){3}[0-9]{1,3}(?::[0-9]{1,5})?\\b");
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)(https?://|ftp://|www\\.)[^\\s]+");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("(?i)\\b[a-zA-Z0-9-]{2,}\\.(?:com|net|org|gg|io|ru|xyz|online|shop|live|fun|club|lat|vip|top)\\b(?::[0-9]{1,5})?(?:/[^\\s]*)?");
    private static final Pattern DISCORD_PATTERN = Pattern.compile("(?i)(?:discord(?:app)?\\s*(?:\\.|\\(dot\\))\s*(?:com/invite|gg)/[a-zA-Z0-9_-]+)");
    private static final Pattern REPEATED_SYMBOLS = Pattern.compile("([^a-zA-Z0-9\\s])\\1{5,}");

    public AntiSpamManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("anti-spam.enabled", true);
        this.bypassOps = plugin.getConfig().getBoolean("anti-spam.bypass-ops", true);
        this.bypassPermission = plugin.getConfig().getString("anti-spam.bypass-permission", "alcatraz.antispam.bypass");
        this.adminPermission = plugin.getConfig().getString("anti-spam.admin-permission", "alcatraz.antispam.admin");
        this.notifyStaff = plugin.getConfig().getBoolean("anti-spam.notify-staff", true);
        this.warningSound = plugin.getConfig().getString("anti-spam.warning-sound", "ENTITY_VILLAGER_NO");

        this.blockLinks = plugin.getConfig().getBoolean("anti-spam.block-links", true);
        this.blockIps = plugin.getConfig().getBoolean("anti-spam.block-ips", true);
        this.blockDiscord = plugin.getConfig().getBoolean("anti-spam.block-discord", true);
        this.blockSymbolSpam = plugin.getConfig().getBoolean("anti-spam.block-symbol-spam", true);
        this.maxSymbolsPercentage = plugin.getConfig().getDouble("anti-spam.max-symbols-percentage", 65.0);

        this.burstEnabled = plugin.getConfig().getBoolean("anti-spam.burst.enabled", true);
        this.burstMaxMessages = plugin.getConfig().getInt("anti-spam.burst.max-messages", 6);
        this.burstWindowSeconds = plugin.getConfig().getDouble("anti-spam.burst.window-seconds", 3.0);

        this.whitelist.clear();
        List<String> list = plugin.getConfig().getStringList("anti-spam.whitelist");
        if (list != null) {
            for (String item : list) {
                if (item != null && !item.trim().isEmpty()) {
                    whitelist.add(item.trim().toLowerCase(Locale.ROOT));
                }
            }
        }

        ConfigurationSection msgSec = plugin.getConfig().getConfigurationSection("anti-spam.messages");
        if (msgSec != null) {
            this.msgPrefix = msgSec.getString("prefix", msgPrefix);
            this.msgBlockedLink = msgSec.getString("blocked-link", msgBlockedLink);
            this.msgBlockedIp = msgSec.getString("blocked-ip", msgBlockedIp);
            this.msgBlockedDiscord = msgSec.getString("blocked-discord", msgBlockedDiscord);
            this.msgBlockedBurst = msgSec.getString("blocked-burst", msgBlockedBurst);
            this.msgBlockedSymbols = msgSec.getString("blocked-symbols", msgBlockedSymbols);
            this.msgStaffAlert = msgSec.getString("staff-alert", msgStaffAlert);
        }
    }

    public void reload() {
        loadConfig();
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

    /**
     * Valida si el mensaje es clasificado como spam.
     */
    public SpamResult checkSpam(Player player, String rawMessage) {
        if (!enabled || rawMessage == null || rawMessage.trim().isEmpty()) {
            return new SpamResult(true, null);
        }

        if (hasBypass(player)) {
            return new SpamResult(true, null);
        }

        String clean = rawMessage.trim();

        // 1. Detección de invitaciones de Discord
        if (blockDiscord && containsDiscord(clean)) {
            return triggerViolation(player, msgBlockedDiscord, rawMessage);
        }

        // 2. Detección de direcciones IP
        if (blockIps && containsIp(clean)) {
            return triggerViolation(player, msgBlockedIp, rawMessage);
        }

        // 3. Detección de URLs o dominios web
        if (blockLinks && containsLink(clean)) {
            return triggerViolation(player, msgBlockedLink, rawMessage);
        }

        // 4. Detección de spam de símbolos / gibberish
        if (blockSymbolSpam && isSymbolSpam(clean)) {
            return triggerViolation(player, msgBlockedSymbols, rawMessage);
        }

        // 5. Detección de ráfagas (Burst spam)
        if (burstEnabled) {
            long now = System.currentTimeMillis();
            UUID uuid = player.getUniqueId();
            List<Long> timestamps = playerMessageTimestamps.computeIfAbsent(uuid, k -> Collections.synchronizedList(new ArrayList<>()));

            long windowMs = (long) (burstWindowSeconds * 1000);
            synchronized (timestamps) {
                timestamps.removeIf(t -> now - t > windowMs);
                timestamps.add(now);
                if (timestamps.size() > burstMaxMessages) {
                    return triggerViolation(player, msgBlockedBurst, rawMessage);
                }
            }
        }

        return new SpamResult(true, null);
    }

    private boolean isWhitelisted(String matched) {
        if (whitelist.isEmpty() || matched == null) return false;
        String lower = matched.toLowerCase(Locale.ROOT);
        for (String allowed : whitelist) {
            if (lower.contains(allowed)) return true;
        }
        return false;
    }

    private boolean containsDiscord(String text) {
        Matcher m = DISCORD_PATTERN.matcher(text);
        while (m.find()) {
            if (!isWhitelisted(m.group())) return true;
        }
        return false;
    }

    private boolean containsIp(String text) {
        Matcher m = IP_PATTERN.matcher(text);
        while (m.find()) {
            String ip = m.group();
            if (!isWhitelisted(ip)) return true;
        }
        return false;
    }

    private boolean containsLink(String text) {
        Matcher m1 = URL_PATTERN.matcher(text);
        while (m1.find()) {
            if (!isWhitelisted(m1.group())) return true;
        }
        Matcher m2 = DOMAIN_PATTERN.matcher(text);
        while (m2.find()) {
            if (!isWhitelisted(m2.group())) return true;
        }
        return false;
    }

    private boolean isSymbolSpam(String text) {
        if (text.length() < 7) return false;

        if (REPEATED_SYMBOLS.matcher(text).find()) {
            return true;
        }

        int symbols = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)) {
                symbols++;
            }
        }

        double ratio = (symbols * 100.0) / text.length();
        return ratio >= maxSymbolsPercentage;
    }

    private SpamResult triggerViolation(Player player, String reason, String rawMessage) {
        playSound(player, warningSound);
        String formattedNotice = color(msgPrefix + reason);
        player.sendMessage(formattedNotice);
        notifyStaff(player, rawMessage);
        return new SpamResult(false, formattedNotice);
    }

    private void notifyStaff(Player violator, String msg) {
        if (!notifyStaff) return;
        String alert = color(msgStaffAlert
                .replace("%player%", violator.getName())
                .replace("%message%", msg));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(adminPermission) || online.isOp()) {
                online.sendMessage(alert);
            }
        }
    }

    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.trim().isEmpty()) return;
        try {
            Sound sound = Sound.valueOf(soundName.trim().toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    public void removePlayer(UUID uuid) {
        playerMessageTimestamps.remove(uuid);
    }

    private String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static class SpamResult {
        public final boolean allowed;
        public final String reason;

        public SpamResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
    }
}
