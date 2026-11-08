package com.alcatrazm.plugin.chatfilter;

import com.alcatrazm.plugin.AlcatrazPlugin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
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
    private int maxStrikes = 4;
    private int muteDurationMinutes = 10;
    private int strikeExpirationSeconds = 300; // 5 minutos de caducidad
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

    // Conjuntos de palabras prohibidas, abreviaciones y lista blanca (whitelist)
    private final Set<String> exactBadWords = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> phraseBadWords = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> offensiveAbbreviations = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> whitelistedWords = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> customBadWords = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> customPhrases = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Registro de strikes con timestamp para caducidad automática
    public static class StrikeRecord {
        public final int count;
        public final long timestamp;

        public StrikeRecord(int count, long timestamp) {
            this.count = count;
            this.timestamp = timestamp;
        }
    }
    private final Map<UUID, StrikeRecord> playerStrikes = new ConcurrentHashMap<>();

    // Sistema de Mutes persistentes (mutes.yml)
    private final Map<UUID, MuteEntry> activeMutes = new ConcurrentHashMap<>();
    private File mutesFile;
    private YamlConfiguration mutesConfig;

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
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
        this.maxStrikes = plugin.getConfig().getInt("chat-filter.max-strikes", 4);
        this.muteDurationMinutes = plugin.getConfig().getInt("chat-filter.mute-duration-minutes", 10);
        this.strikeExpirationSeconds = plugin.getConfig().getInt("chat-filter.strike-expiration-seconds", 300);
        this.bypassPermission = plugin.getConfig().getString("chat-filter.bypass-permission", "alcatraz.chatfilter.bypass");
        this.bypassOps = plugin.getConfig().getBoolean("chat-filter.bypass-ops", true);
        this.adminPermission = plugin.getConfig().getString("chat-filter.admin-permission", "alcatraz.chatfilter.admin");
        this.notifyStaff = plugin.getConfig().getBoolean("chat-filter.notify-staff", true);
        this.warningSound = plugin.getConfig().getString("chat-filter.warning-sound", "ENTITY_VILLAGER_NO");
        this.muteSound = plugin.getConfig().getString("chat-filter.mute-sound", "ENTITY_ENDER_DRAGON_GROWL");

        // 1. Cargar lista blanca (whitelist) de palabras que NUNCA deben mutearse
        whitelistedWords.clear();
        List<String> whitelist = plugin.getConfig().getStringList("chat-filter.palabras-permitidas");
        if (whitelist != null) {
            for (String w : whitelist) {
                if (w != null && !w.trim().isEmpty()) {
                    whitelistedWords.add(normalize(w.trim()));
                }
            }
        }

        // 2. Cargar palabras prohibidas extra desde YAML
        customBadWords.clear();
        List<String> extraWords = plugin.getConfig().getStringList("chat-filter.palabras-prohibidas-extra");
        if (extraWords != null) {
            for (String w : extraWords) {
                if (w != null && !w.trim().isEmpty()) {
                    customBadWords.add(normalize(w.trim()));
                }
            }
        }

        // 3. Cargar lista explícita y controlada de abreviaciones ofensivas
        offensiveAbbreviations.clear();
        List<String> abbrevs = plugin.getConfig().getStringList("chat-filter.abreviaciones-ofensivas");
        if (abbrevs == null || abbrevs.isEmpty()) {
            abbrevs = plugin.getConfig().getStringList("chat-filter.offensive-abbreviations");
        }
        if (abbrevs == null || abbrevs.isEmpty()) {
            offensiveAbbreviations.addAll(Arrays.asList("ctm", "hdp", "ptm", "alv", "cdtm", "hdpm", "kys", "stfu"));
        } else {
            for (String a : abbrevs) {
                if (a != null && !a.trim().isEmpty()) {
                    offensiveAbbreviations.add(normalize(a.trim()));
                }
            }
        }

        // 4. Cargar frases prohibidas extra desde YAML
        customPhrases.clear();
        List<String> extraPhrases = plugin.getConfig().getStringList("chat-filter.frases-prohibidas-extra");
        if (extraPhrases != null) {
            for (String f : extraPhrases) {
                if (f != null && !f.trim().isEmpty()) {
                    customPhrases.add(normalize(f.trim()));
                }
            }
        }

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

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(wordsFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String normalized = normalize(line);
                if (normalized.isEmpty()) continue;

                if (normalized.contains(" ")) {
                    phraseBadWords.add(normalized);
                } else if (normalized.length() >= 4) {
                    // Solo palabras completas de 4 o más letras; abreviaciones cortas van en lista explícita
                    exactBadWords.add(normalized);
                }
            }
            plugin.getLogger().info("[ChatFilter] Cargadas " + exactBadWords.size() + " palabras completas y " + phraseBadWords.size() + " frases prohibidas (" + offensiveAbbreviations.size() + " abreviaciones explícitas).");
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
            plugin.getLogger().warning("[ChatFilter] No se pudo guardar mutes.yml: " + e.getMessage());
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
                plugin.getLogger().info("[ChatFilter] " + name + " fue desmuteado exitosamente.");
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

        // 3. Análisis de malas palabras (sin falsos positivos)
        String detectedWord = findBadWord(rawMessage);
        if (detectedWord == null) {
            return new FilterResult(ResultType.ALLOWED, null);
        }

        // 4. Infracción detectada -> Gestión de strikes con caducidad
        long now = System.currentTimeMillis();
        StrikeRecord record = playerStrikes.get(uuid);
        int currentStrike = 1;

        if (record != null) {
            long elapsedSeconds = (now - record.timestamp) / 1000L;
            if (elapsedSeconds > strikeExpirationSeconds) {
                // El strike anterior ya caducó por buen comportamiento
                currentStrike = 1;
            } else {
                currentStrike = record.count + 1;
            }
        }

        if (currentStrike < maxStrikes) {
            playerStrikes.put(uuid, new StrikeRecord(currentStrike, now));
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

            plugin.getLogger().warning("[ChatFilter] Strike (" + currentStrike + "/" + maxStrikes + ") a " + player.getName() + " por palabra '" + detectedWord + "' en: " + rawMessage);
            notifyStaffStrike(player, currentStrike, maxStrikes, rawMessage);
            return new FilterResult(ResultType.BLOCKED_WARN, warn);
        } else {
            // Mute automático al alcanzar el tope de strikes
            playerStrikes.remove(uuid);
            long until = now + ((long) muteDurationMinutes * 60 * 1000L);
            activeMutes.put(uuid, new MuteEntry(player.getName(), until, "Acumulación de " + maxStrikes + " groserías ('" + detectedWord + "')"));
            saveMutes();

            playSound(player, muteSound);

            String muteMsg = color(msgPrefix + msgMutedNotice
                    .replace("%max%", String.valueOf(maxStrikes))
                    .replace("%duration%", String.valueOf(muteDurationMinutes)));
            player.sendMessage(muteMsg);

            plugin.getLogger().severe("[ChatFilter] ¡MUTE AUTOMÁTICO! " + player.getName() + " silenciado por " + muteDurationMinutes + " min tras " + maxStrikes + " strikes. Palabra final: '" + detectedWord + "'");
            notifyStaffMute(player, muteDurationMinutes, maxStrikes);
            return new FilterResult(ResultType.BLOCKED_MUTED, muteMsg);
        }
    }

    /**
     * Busca si el texto contiene alguna grosería o abreviación ofensiva explícita.
     */
    public String findBadWord(String rawText) {
        if (rawText == null || rawText.isEmpty()) return null;

        String norm = normalize(rawText);

        // 1. Frases completas explícitas (personalizadas de YAML y base de badwords.txt)
        for (String phrase : customPhrases) {
            if (containsPhrase(norm, phrase)) {
                return phrase;
            }
        }
        for (String phrase : phraseBadWords) {
            if (containsPhrase(norm, phrase)) {
                return phrase;
            }
        }

        // 2. Tokenizar palabras individuales para comprobación exacta (cero adivinanzas ciegas)
        String[] tokens = norm.split("[^a-z0-9_]+");
        for (String token : tokens) {
            if (token.isEmpty()) continue;

            // COMPROBACIÓN DE LISTA BLANCA (WHITELIST):
            // Si la palabra está explícitamente permitida, se ignora por completo
            if (whitelistedWords.contains(token)) {
                continue;
            }

            // A. Abreviaciones ofensivas explícitas (ej: ctm, hdp, alv)
            if (offensiveAbbreviations.contains(token)) {
                return token;
            }

            // B. Palabras prohibidas añadidas por YAML
            if (customBadWords.contains(token)) {
                return token;
            }

            // C. Palabras completas prohibidas del diccionario base (longitud >= 4)
            if (token.length() >= 4 && exactBadWords.contains(token)) {
                return token;
            }

            // D. Palabras con letras repetidas intencionales (ej: "puuuutaaaa" -> "puta")
            String collapsed = REPEATED_LETTERS.matcher(token).replaceAll("$1");
            if (!whitelistedWords.contains(collapsed)) {
                if (customBadWords.contains(collapsed)) {
                    return collapsed;
                }
                if (collapsed.length() >= 4 && exactBadWords.contains(collapsed)) {
                    return collapsed;
                }
            }
        }

        return null;
    }

    private boolean containsPhrase(String text, String phrase) {
        int idx = text.indexOf(phrase);
        if (idx == -1) return false;
        boolean startOk = (idx == 0) || !Character.isLetterOrDigit(text.charAt(idx - 1));
        int endIdx = idx + phrase.length();
        boolean endOk = (endIdx == text.length()) || !Character.isLetterOrDigit(text.charAt(endIdx));
        return startOk && endOk;
    }

    public static String normalize(String input) {
        if (input == null) return "";
        String lower = input.toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        String noAccents = DIACRITICS_PATTERN.matcher(decomposed).replaceAll("");
        // Solo normalizar símbolos decorativos sin alterar números
        return noAccents.replace('@', 'a').replace('$', 's');
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

    public boolean addWhitelistedWord(String word) {
        String norm = normalize(word);
        if (norm.isEmpty()) return false;
        whitelistedWords.add(norm);
        customBadWords.remove(norm);

        List<String> list = plugin.getConfig().getStringList("chat-filter.palabras-permitidas");
        if (!list.contains(norm)) {
            list.add(norm);
            plugin.getConfig().set("chat-filter.palabras-permitidas", list);
            plugin.saveConfig();
        }
        return true;
    }

    public boolean addCustomBadWord(String word) {
        String norm = normalize(word);
        if (norm.isEmpty()) return false;
        customBadWords.add(norm);
        whitelistedWords.remove(norm);

        List<String> list = plugin.getConfig().getStringList("chat-filter.palabras-prohibidas-extra");
        if (!list.contains(norm)) {
            list.add(norm);
            plugin.getConfig().set("chat-filter.palabras-prohibidas-extra", list);
            plugin.saveConfig();
        }
        return true;
    }

    public boolean removeWord(String word) {
        String norm = normalize(word);
        if (norm.isEmpty()) return false;
        boolean removed = false;

        if (whitelistedWords.remove(norm)) removed = true;
        if (customBadWords.remove(norm)) removed = true;
        if (offensiveAbbreviations.remove(norm)) removed = true;
        if (customPhrases.remove(norm)) removed = true;

        List<String> wl = plugin.getConfig().getStringList("chat-filter.palabras-permitidas");
        if (wl.remove(norm)) {
            plugin.getConfig().set("chat-filter.palabras-permitidas", wl);
            removed = true;
        }
        List<String> extra = plugin.getConfig().getStringList("chat-filter.palabras-prohibidas-extra");
        if (extra.remove(norm)) {
            plugin.getConfig().set("chat-filter.palabras-prohibidas-extra", extra);
            removed = true;
        }
        List<String> abbrevs = plugin.getConfig().getStringList("chat-filter.abreviaciones-ofensivas");
        if (abbrevs.remove(norm)) {
            plugin.getConfig().set("chat-filter.abreviaciones-ofensivas", abbrevs);
            removed = true;
        }
        if (removed) {
            plugin.saveConfig();
        }
        return removed;
    }

    public void sendFilterList(CommandSender sender, String category, int page) {
        String cat = (category == null || category.trim().isEmpty()) ? "permitidas" : category.trim().toLowerCase(Locale.ROOT);

        // Visualización de cabecera con pestañas interactivas
        sender.sendMessage(color("&8&m--------------------------------------------------"));
        sender.sendMessage(color("&b&lFILTRO DE CHAT &8| &ePestañas de Configuración:"));

        if (sender instanceof Player p) {
            TextComponent tabs = new TextComponent();

            TextComponent t1 = new TextComponent(color((cat.startsWith("perm") || cat.startsWith("white") ? "&a&l[✔ Permitidas (" + whitelistedWords.size() + ")] " : "&7[Permitidas (" + whitelistedWords.size() + ")] ")));
            t1.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/alcatraz filter list permitidas"));
            t1.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(color("&aClic para ver palabras permitidas (whitelist)")).create()));

            TextComponent t2 = new TextComponent(color((cat.startsWith("proh") || cat.startsWith("extra") ? "&c&l[✖ Prohibidas (" + customBadWords.size() + ")] " : "&7[Prohibidas (" + customBadWords.size() + ")] ")));
            t2.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/alcatraz filter list prohibidas"));
            t2.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(color("&cClic para ver palabras prohibidas de YAML")).create()));

            TextComponent t3 = new TextComponent(color((cat.startsWith("abrev") || cat.startsWith("siglas") ? "&6&l[⚡ Abreviaciones (" + offensiveAbbreviations.size() + ")] " : "&7[Abreviaciones (" + offensiveAbbreviations.size() + ")] ")));
            t3.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/alcatraz filter list abreviaciones"));
            t3.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(color("&6Clic para ver abreviaciones ofensivas")).create()));

            TextComponent t4 = new TextComponent(color((cat.startsWith("fras") ? "&d&l[💬 Frases (" + (customPhrases.size() + phraseBadWords.size()) + ")] " : "&7[Frases (" + (customPhrases.size() + phraseBadWords.size()) + ")] ")));
            t4.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/alcatraz filter list frases"));
            t4.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(color("&dClic para ver frases prohibidas")).create()));

            TextComponent t5 = new TextComponent(color((cat.startsWith("base") || cat.startsWith("dic") ? "&e&l[📖 Base (" + exactBadWords.size() + ")]" : "&7[Base (" + exactBadWords.size() + ")]")));
            t5.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/alcatraz filter list base"));
            t5.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(color("&eClic para ver el diccionario base (badwords.txt)")).create()));

            tabs.addExtra(t1);
            tabs.addExtra(t2);
            tabs.addExtra(t3);
            tabs.addExtra(t4);
            tabs.addExtra(t5);

            p.spigot().sendMessage(tabs);
        } else {
            sender.sendMessage(color("&a[1. Permitidas: " + whitelistedWords.size() + "] &c[2. Prohibidas: " + customBadWords.size() + "] &6[3. Abreviaciones: " + offensiveAbbreviations.size() + "] &d[4. Frases: " + (customPhrases.size() + phraseBadWords.size()) + "] &e[5. Base: " + exactBadWords.size() + "]"));
            sender.sendMessage(color("&7Usa: &b/alcatraz filter list <permitidas|prohibidas|abreviaciones|frases|base> [página]"));
        }
        sender.sendMessage(color("&8&m--------------------------------------------------"));

        // Selección de lista según pestaña
        List<String> items;
        String title;
        String colorCode;

        if (cat.startsWith("proh") || cat.startsWith("extra")) {
            items = new ArrayList<>(customBadWords);
            title = "&cPalabras Prohibidas Personalizadas (YAML)";
            colorCode = "&c";
            cat = "prohibidas";
        } else if (cat.startsWith("abrev") || cat.startsWith("siglas")) {
            items = new ArrayList<>(offensiveAbbreviations);
            title = "&6Abreviaciones Ofensivas Controladas";
            colorCode = "&6";
            cat = "abreviaciones";
        } else if (cat.startsWith("fras")) {
            items = new ArrayList<>(customPhrases);
            items.addAll(phraseBadWords);
            title = "&dFrases Prohibidas";
            colorCode = "&d";
            cat = "frases";
        } else if (cat.startsWith("base") || cat.startsWith("dic")) {
            items = new ArrayList<>(exactBadWords);
            title = "&eDiccionario Base (badwords.txt)";
            colorCode = "&e";
            cat = "base";
        } else {
            items = new ArrayList<>(whitelistedWords);
            title = "&aPalabras Permitidas (Whitelist)";
            colorCode = "&a";
            cat = "permitidas";
        }

        Collections.sort(items);

        if (items.isEmpty()) {
            sender.sendMessage(color("&ePestaña: " + title + " &7- &o(No hay elementos registrados)"));
            sender.sendMessage(color("&8&m--------------------------------------------------"));
            return;
        }

        int pageSize = 18;
        int totalPages = (int) Math.ceil((double) items.size() / pageSize);
        if (totalPages <= 0) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, items.size());
        List<String> pageItems = items.subList(fromIndex, toIndex);

        sender.sendMessage(color("&ePestaña actual: " + title + " &8» &7Página &e" + page + "&7/&e" + totalPages + " &7(&f" + items.size() + " &7totales):"));
        sender.sendMessage(color(colorCode + String.join("&7, " + colorCode, pageItems)));

        // Botones de navegación interactivos para jugadores
        if (sender instanceof Player p && totalPages > 1) {
            TextComponent nav = new TextComponent(color("&7Navegación: "));
            if (page > 1) {
                TextComponent prev = new TextComponent(color("&b&l[◀ Anterior] "));
                prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/alcatraz filter list " + cat + " " + (page - 1)));
                prev.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(color("&bPágina " + (page - 1))).create()));
                nav.addExtra(prev);
            }
            if (page < totalPages) {
                TextComponent next = new TextComponent(color("&b&l[Siguiente ▶]"));
                next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/alcatraz filter list " + cat + " " + (page + 1)));
                next.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(color("&bPágina " + (page + 1))).create()));
                nav.addExtra(next);
            }
            p.spigot().sendMessage(nav);
        } else if (totalPages > 1) {
            sender.sendMessage(color("&7Para cambiar de página usa: &b/alcatraz filter list " + cat + " <número>"));
        }
        sender.sendMessage(color("&8&m--------------------------------------------------"));
    }

    public Set<String> getWhitelistedWords() { return Collections.unmodifiableSet(whitelistedWords); }
    public Set<String> getCustomBadWords() { return Collections.unmodifiableSet(customBadWords); }
    public Set<String> getOffensiveAbbreviations() { return Collections.unmodifiableSet(offensiveAbbreviations); }
    public Set<String> getCustomPhrases() { return Collections.unmodifiableSet(customPhrases); }
    public Set<String> getExactBadWords() { return Collections.unmodifiableSet(exactBadWords); }
    public Set<String> getPhraseBadWords() { return Collections.unmodifiableSet(phraseBadWords); }

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
