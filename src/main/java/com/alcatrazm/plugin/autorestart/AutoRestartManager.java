package com.alcatrazm.plugin.autorestart;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.module.AlcatrazModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Módulo nativo de reinicios automáticos que absorbe la funcionalidad completa de
 * UltimateAutoRestart y PiponAutoRestart sin dependencias pesadas ni tareas por tick.
 */
public class AutoRestartManager implements AlcatrazModule {

    private static final LegacyComponentSerializer LEGACY_HEX = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final Pattern GRADIENT_PATTERN = Pattern.compile(
            "<GRADIENT:([0-9A-Fa-f]{6})>(.*?)</GRADIENT:([0-9A-Fa-f]{6})>",
            Pattern.DOTALL
    );

    private static final Pattern DAY_PREFIX_PATTERN = Pattern.compile(
            "^\\[(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TIMED_CMD_PATTERN = Pattern.compile(
            "^\\[(time|proxy|proxydelay):(\\d+)]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SIMPLE_PROXY_PATTERN = Pattern.compile(
            "^\\[proxy]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private final AlcatrazPlugin plugin;
    private final long bootNanoTime = System.nanoTime();

    private BukkitTask tickTask;
    private AutoRestartPlaceholderExpansion papiExpansion;
    private boolean startupHooksDone = false;

    // Estado del temporizador
    private boolean running = false;
    private int remainingSeconds = -1;
    private int lastProcessedSecond = Integer.MAX_VALUE;
    private long deadlineNanos = 0L;
    private int lastProcessedAfterReboot = 0;
    private String currentReason;

    // Configuración cargada
    private ZoneId zoneId = ZoneId.of("America/Caracas");
    private final List<ScheduleEntry> scheduleEntries = new ArrayList<>();
    private final Set<Integer> messageIntervals = new HashSet<>();
    private final Set<Integer> soundIntervals = new HashSet<>();

    private final Map<Integer, List<CommandSpec>> timedServerCommands = new HashMap<>();
    private final Map<Integer, List<CommandSpec>> timedProxyCommands = new HashMap<>();
    private final List<CommandSpec> normalServerCommands = new ArrayList<>();
    private final List<CommandSpec> normalProxyCommands = new ArrayList<>();
    private final List<CommandSpec> delayedProxyCommands = new ArrayList<>();
    private final Map<Integer, List<CommandSpec>> afterRebootServerCommands = new HashMap<>();
    private final Map<Integer, List<CommandSpec>> afterRebootProxyCommands = new HashMap<>();

    // Auto-delay
    private boolean autoDelayEnabled;
    private int autoDelayCheckSeconds;
    private int autoDelayMinPlayers;
    private int autoDelayAddSeconds;

    // Proxy & Webhooks
    private String proxyChannel = "bukkit:changethiskey";
    private boolean webhookEnabled;
    private String webhookUrl;
    private DateTimeFormatter webhookTimestampFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy - HH:mm:ss");
    private final Map<String, WebhookTemplate> webhookTemplates = new HashMap<>();

    // Formato y mensajes
    private String prefix;
    private String defaultReason;
    private String unscheduledLabel;
    private String fmtDays, fmtDay, fmtHours, fmtHour, fmtMinutes, fmtMinute, fmtSeconds, fmtSecond, fmtSplitter;

    private boolean intervalChatEnabled;
    private List<String> intervalChatLines;
    private boolean intervalActionbarEnabled;
    private String intervalActionbar;
    private boolean intervalTitleEnabled;
    private String intervalTitle;
    private String intervalSubtitle;

    private boolean restartTitleEnabled;
    private String restartTitle;
    private String restartSubtitle;
    private int restartTitleAtSeconds;

    private String msgInfo;
    private String msgDelayGlobal;
    private String msgDelayStaff;
    private String msgForcedGlobal;
    private String msgStoppedGlobal;

    private String intervalSoundName;
    private float intervalSoundVol;
    private float intervalSoundPitch;
    private boolean progressivePitchLast10s;
    private String restartSoundName;
    private String delaySoundName;
    private String invalidSoundName;

    public AutoRestartManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "autorestart";
    }

    @Override
    public String getDisplayName() {
        return "Reinicios Automáticos (UltimateAutoRestart)";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("autorestart.enabled", true);
    }

    @Override
    public void enable() {
        loadConfig();
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, proxyChannel);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            papiExpansion = new AutoRestartPlaceholderExpansion(plugin, this);
            papiExpansion.register();
        }

        int scheduledSeconds = calculateSecondsUntilNext();
        if (scheduledSeconds >= 0) {
            resetCountdown(scheduledSeconds, defaultReason);
        } else {
            this.running = false;
            this.remainingSeconds = -1;
        }

        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);

        if (!startupHooksDone) {
            startupHooksDone = true;
            sendWebhookAsync("SERVER_BACK_UP", this.defaultReason);
        }
    }

    @Override
    public void disable() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
        }
        tickTask = null;
        running = false;

        if (papiExpansion != null) {
            try {
                papiExpansion.unregister();
            } catch (Throwable ignored) {
            }
            papiExpansion = null;
        }

        if (proxyChannel != null && !proxyChannel.isEmpty()) {
            try {
                Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, proxyChannel);
            } catch (Throwable ignored) {
            }
        }
    }

    public void loadConfig() {
        FileConfiguration cfg = plugin.getConfig();
        String tzStr = cfg.getString("autorestart.timezone", "America/Caracas");
        try {
            this.zoneId = ZoneId.of(tzStr);
        } catch (Exception ex) {
            plugin.getLogger().warning("[AutoRestart] Zona horaria inválida '" + tzStr + "', usando sistema.");
            this.zoneId = ZoneId.systemDefault();
        }

        parseSchedule(cfg.getStringList("autorestart.restarts"));

        messageIntervals.clear();
        for (int sec : cfg.getIntegerList("autorestart.message-at-intervals")) {
            messageIntervals.add(sec);
        }
        if (messageIntervals.isEmpty()) {
            Collections.addAll(messageIntervals, 3600, 1800, 600, 300, 120, 60, 30, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        }

        soundIntervals.clear();
        for (int sec : cfg.getIntegerList("autorestart.sound-at-intervals")) {
            soundIntervals.add(sec);
        }

        parseCommandList(cfg.getStringList("autorestart.restart-commands"),
                timedServerCommands, timedProxyCommands, normalServerCommands, normalProxyCommands, delayedProxyCommands);
        parseCommandList(cfg.getStringList("autorestart.commands-after-reboot"),
                afterRebootServerCommands, afterRebootProxyCommands, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        this.autoDelayEnabled = cfg.getBoolean("autorestart.delay-restart.enabled", false);
        this.autoDelayCheckSeconds = cfg.getInt("autorestart.delay-restart.check-seconds-before-restart", 30);
        this.autoDelayMinPlayers = cfg.getInt("autorestart.delay-restart.minimum-players", 1);
        this.autoDelayAddSeconds = cfg.getInt("autorestart.delay-restart.delay-by-seconds", 600);

        String configuredChannel = cfg.getString("autorestart.proxy-secret", "bukkit:changethiskey");
        this.proxyChannel = (configuredChannel == null || !configuredChannel.contains(":")) ? "bukkit:changethiskey" : configuredChannel;

        this.webhookEnabled = cfg.getBoolean("autorestart.webhook.enabled", false);
        this.webhookUrl = cfg.getString("autorestart.webhook.url", "");
        String tsFmt = cfg.getString("autorestart.webhook.timestamp-format", "dd.MM.yyyy - HH:mm:ss");
        try {
            this.webhookTimestampFmt = DateTimeFormatter.ofPattern(tsFmt);
        } catch (IllegalArgumentException ignored) {
            this.webhookTimestampFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy - HH:mm:ss");
        }
        loadWebhookTemplates(cfg);

        this.unscheduledLabel = cfg.getString("autorestart.format.unscheduled", "&f&lN/A");
        this.fmtDays = cfg.getString("autorestart.format.days", "d ");
        this.fmtDay = cfg.getString("autorestart.format.day", "d ");
        this.fmtHours = cfg.getString("autorestart.format.hours", "h ");
        this.fmtHour = cfg.getString("autorestart.format.hour", "h ");
        this.fmtMinutes = cfg.getString("autorestart.format.minutes", "m ");
        this.fmtMinute = cfg.getString("autorestart.format.minute", "m ");
        this.fmtSeconds = cfg.getString("autorestart.format.seconds", "s");
        this.fmtSecond = cfg.getString("autorestart.format.second", "s");
        this.fmtSplitter = cfg.getString("autorestart.format.splitter", "y ");

        this.prefix = cfg.getString("autorestart.messages.prefix", "<GRADIENT:9483FF>&lAlcatraz</GRADIENT:FB9AF2>&r");
        this.defaultReason = cfg.getString("autorestart.messages.default-reason", "Reinicio programado");
        this.currentReason = this.defaultReason;

        this.intervalChatEnabled = cfg.getBoolean("autorestart.messages.interval-chat-enabled", false);
        this.intervalChatLines = cfg.getStringList("autorestart.messages.interval-chat");
        this.intervalActionbarEnabled = cfg.getBoolean("autorestart.messages.interval-actionbar-enabled", true);
        this.intervalActionbar = cfg.getString("autorestart.messages.interval-actionbar",
                "{PREFIX} &f» &c⚠ Reiniciando en &e{0} &7({1})");
        this.intervalTitleEnabled = cfg.getBoolean("autorestart.messages.interval-title-enabled", false);
        this.intervalTitle = cfg.getString("autorestart.messages.interval-title", "{PREFIX}");
        this.intervalSubtitle = cfg.getString("autorestart.messages.interval-subtitle", "&fReiniciando en &d{0}&f!");

        this.restartTitleEnabled = cfg.getBoolean("autorestart.messages.restart-title-enabled", true);
        this.restartTitle = cfg.getString("autorestart.messages.restart-title", "&c&lREINICIO AUTOMÁTICO");
        this.restartSubtitle = cfg.getString("autorestart.messages.restart-subtitle", "&ePor favor, vuelve en un momento...");
        this.restartTitleAtSeconds = cfg.getInt("autorestart.messages.restart-title-at-seconds", 3);

        this.msgInfo = cfg.getString("autorestart.messages.info",
                "{PREFIX} &f» &ePróximo reinicio automático en: &a{0}&e.");
        this.msgDelayGlobal = cfg.getString("autorestart.messages.delay-global",
                "{PREFIX} &f» &eEl reinicio del servidor se retrasó &a{0}&e.");
        this.msgDelayStaff = cfg.getString("autorestart.messages.delay-staff",
                "{PREFIX} &f» &eReinicio retrasado automáticamente &a{0}&e (jugadores activos).");
        this.msgForcedGlobal = cfg.getString("autorestart.messages.forced-global",
                "{PREFIX} &f» &c¡Reinicio forzado programado en &e{0}&c! Razón: &f{1}");
        this.msgStoppedGlobal = cfg.getString("autorestart.messages.stopped-global",
                "{PREFIX} &f» &eLa cuenta regresiva del reinicio ha sido detenida.");

        this.intervalSoundName = cfg.getString("autorestart.sounds.interval-sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        this.intervalSoundVol = (float) cfg.getDouble("autorestart.sounds.interval-volume", 1.0);
        this.intervalSoundPitch = (float) cfg.getDouble("autorestart.sounds.interval-pitch", 1.0);
        this.progressivePitchLast10s = cfg.getBoolean("autorestart.sounds.progressive-pitch-last-10s", true);
        this.restartSoundName = cfg.getString("autorestart.sounds.restart-sound", "ENTITY_PLAYER_LEVELUP");
        this.delaySoundName = cfg.getString("autorestart.sounds.delay-sound", "ENTITY_PLAYER_LEVELUP");
        this.invalidSoundName = cfg.getString("autorestart.sounds.invalid-sound", "ENTITY_VILLAGER_NO");
    }

    private void loadWebhookTemplates(FileConfiguration cfg) {
        webhookTemplates.clear();
        for (String key : new String[]{"SERVER_RESTARTING", "SERVER_BACK_UP", "SERVER_FORCED_RESTART"}) {
            boolean enabled = cfg.getBoolean("autorestart.webhook.messages." + key + ".enabled", true);
            String content = cfg.getString("autorestart.webhook.messages." + key + ".content", "");
            webhookTemplates.put(key, new WebhookTemplate(enabled, content));
        }
    }

    private void parseSchedule(List<String> rawEntries) {
        scheduleEntries.clear();
        for (String raw : rawEntries) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String entry = raw.trim();
            try {
                if (entry.contains(";")) {
                    String[] parts = entry.split(";", -1);
                    if (parts.length != 3) {
                        throw new IllegalArgumentException("Expected Day;HH;MM");
                    }
                    DayOfWeek day = parts[0].trim().equalsIgnoreCase("daily")
                            ? null
                            : DayOfWeek.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
                    int hour = Integer.parseInt(parts[1].trim());
                    int minute = Integer.parseInt(parts[2].trim());
                    if (hour == 24 && minute == 0) {
                        hour = 0;
                    }
                    scheduleEntries.add(new ScheduleEntry(day, LocalTime.of(hour, minute)));
                } else {
                    LocalTime time = LocalTime.parse(entry);
                    scheduleEntries.add(new ScheduleEntry(null, time));
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("[AutoRestart] Entrada de horario inválida en autorestart.restarts: '" + raw + "'");
            }
        }
    }

    private void parseCommandList(
            List<String> rawCommands,
            Map<Integer, List<CommandSpec>> timedServer,
            Map<Integer, List<CommandSpec>> timedProxy,
            List<CommandSpec> normalServer,
            List<CommandSpec> normalProxy,
            List<CommandSpec> delayedProxy
    ) {
        timedServer.clear();
        timedProxy.clear();
        normalServer.clear();
        normalProxy.clear();
        delayedProxy.clear();

        for (String raw : rawCommands) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String trimmed = raw.trim();
            Matcher timedMatcher = TIMED_CMD_PATTERN.matcher(trimmed);
            if (timedMatcher.matches()) {
                String type = timedMatcher.group(1).toLowerCase(Locale.ROOT);
                int sec = Integer.parseInt(timedMatcher.group(2));
                CommandSpec spec = parseDayAndCommand(timedMatcher.group(3), sec);
                switch (type) {
                    case "time" -> timedServer.computeIfAbsent(sec, k -> new ArrayList<>()).add(spec);
                    case "proxy" -> timedProxy.computeIfAbsent(sec, k -> new ArrayList<>()).add(spec);
                    case "proxydelay" -> delayedProxy.add(spec);
                }
                continue;
            }

            Matcher proxyMatcher = SIMPLE_PROXY_PATTERN.matcher(trimmed);
            if (proxyMatcher.matches()) {
                normalProxy.add(parseDayAndCommand(proxyMatcher.group(1), 0));
                continue;
            }

            normalServer.add(parseDayAndCommand(trimmed, 0));
        }
    }

    private CommandSpec parseDayAndCommand(String text, int seconds) {
        String body = text == null ? "" : text.trim();
        Matcher dayMatcher = DAY_PREFIX_PATTERN.matcher(body);
        if (dayMatcher.matches()) {
            DayOfWeek day = DayOfWeek.valueOf(dayMatcher.group(1).toUpperCase(Locale.ROOT));
            return new CommandSpec(day, dayMatcher.group(2).trim(), seconds);
        }
        return new CommandSpec(null, body, seconds);
    }

    public int calculateSecondsUntilNext() {
        if (scheduleEntries.isEmpty()) {
            return -1;
        }
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        long minSeconds = Long.MAX_VALUE;
        for (ScheduleEntry entry : scheduleEntries) {
            int dayOffset = entry.day == null ? 0 : Math.floorMod(entry.day.getValue() - now.getDayOfWeek().getValue(), 7);
            ZonedDateTime candidate = now.toLocalDate().plusDays(dayOffset).atTime(entry.time).atZone(now.getZone());
            if (!candidate.isAfter(now)) {
                candidate = candidate.toLocalDate().plusDays(entry.day == null ? 1L : 7L).atTime(entry.time).atZone(now.getZone());
            }
            Duration duration = Duration.between(now, candidate);
            long secs = duration.getSeconds() + (duration.getNano() == 0 ? 0L : 1L);
            minSeconds = Math.min(minSeconds, secs);
        }
        return (int) Math.min(Integer.MAX_VALUE, minSeconds);
    }

    private void resetCountdown(int seconds, String reason) {
        this.remainingSeconds = Math.max(0, seconds);
        this.lastProcessedSecond = this.remainingSeconds + 1;
        this.deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(this.remainingSeconds);
        this.currentReason = (reason != null && !reason.isBlank()) ? reason : this.defaultReason;
        this.running = true;
    }

    private int computeSecondsLeftFromClock() {
        long diffNanos = deadlineNanos - System.nanoTime();
        if (diffNanos <= 0L) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, 1L + (diffNanos - 1L) / 1_000_000_000L);
    }

    private void tick() {
        // 1. Tareas post-arranque (commandsAfterReboot)
        int uptimeSeconds = getAfterRebootSeconds();
        if (uptimeSeconds > lastProcessedAfterReboot) {
            for (int sec = lastProcessedAfterReboot + 1; sec <= uptimeSeconds; sec++) {
                dispatchTimedCommands(afterRebootServerCommands.get(sec));
                dispatchProxyCommands(afterRebootProxyCommands.get(sec), "");
            }
            lastProcessedAfterReboot = uptimeSeconds;
        }

        // 2. Cuenta regresiva hacia el reinicio
        if (!running) {
            return;
        }

        int current = computeSecondsLeftFromClock();
        this.remainingSeconds = current;
        if (current >= lastProcessedSecond) {
            return;
        }

        int start = lastProcessedSecond - 1;
        // Evitar tormenta de pasos si hubiera un salto de reloj extremo (> 15s)
        if (start - current > 15) {
            start = current;
        }

        Integer notifyMessageSec = null;
        Integer notifySoundSec = null;

        for (int sec = start; sec >= current; sec--) {
            lastProcessedSecond = sec;

            if (autoDelayEnabled && sec == autoDelayCheckSeconds
                    && Bukkit.getOnlinePlayers().size() >= autoDelayMinPlayers) {
                int newRemaining = Math.addExact(sec, autoDelayAddSeconds);
                resetCountdown(newRemaining, currentReason);
                announceDelay(autoDelayAddSeconds);
                return;
            }

            dispatchTimedCommands(timedServerCommands.get(sec));
            dispatchProxyCommands(timedProxyCommands.get(sec), "");

            if (sec == restartTitleAtSeconds && sec > 0 && restartTitleEnabled) {
                showRestartTitleToAll();
            }

            if (messageIntervals.contains(sec)) {
                notifyMessageSec = sec;
            }
            if (soundIntervals.contains(sec)) {
                notifySoundSec = sec;
            }

            if (sec == 0) {
                executeFinalRestart();
                return;
            }
        }

        if (notifyMessageSec != null) {
            announceInterval(notifyMessageSec);
        }
        if (notifySoundSec != null) {
            playIntervalSound(notifySoundSec);
        }
    }

    private void executeFinalRestart() {
        this.running = false;
        this.remainingSeconds = 0;

        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }

        sendWebhookAsync("SERVER_RESTARTING", currentReason);

        if (restartTitleEnabled) {
            showRestartTitleToAll();
        }
        playNamedSoundToAll(restartSoundName, 1.5f, 1.0f);

        for (CommandSpec spec : delayedProxyCommands) {
            sendSingleProxyCommand(spec, "TIME:" + spec.delaySeconds + ":");
        }
        dispatchProxyCommands(normalProxyCommands, "");

        for (CommandSpec spec : normalServerCommands) {
            String cmd = spec.commandFor(LocalDate.now(zoneId).getDayOfWeek());
            if (cmd != null && !cmd.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }
    }

    private void showRestartTitleToAll() {
        Component titleComp = formatComponent(replaceVars(restartTitle, remainingSeconds, currentReason));
        Component subComp = formatComponent(replaceVars(restartSubtitle, remainingSeconds, currentReason));
        Title title = Title.title(titleComp, subComp,
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
        }
    }

    private void announceInterval(int sec) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (intervalChatEnabled) {
                for (String line : intervalChatLines) {
                    player.sendMessage(formatComponent(replaceVars(line, sec, currentReason)));
                }
            }
            if (intervalActionbarEnabled && intervalActionbar != null && !intervalActionbar.isEmpty()) {
                player.sendActionBar(formatComponent(replaceVars(intervalActionbar, sec, currentReason)));
            }
            if (intervalTitleEnabled) {
                Component t = formatComponent(replaceVars(intervalTitle, sec, currentReason));
                Component s = formatComponent(replaceVars(intervalSubtitle, sec, currentReason));
                player.showTitle(Title.title(t, s, Title.Times.times(
                        Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(200))));
            }
        }
    }

    private void announceDelay(int addedSeconds) {
        playNamedSoundToAll(delaySoundName, 1.0f, 1.0f);
        for (Player player : Bukkit.getOnlinePlayers()) {
            String template = (player.hasPermission("uar.staff.notifydelay") || player.hasPermission("uar.admin")
                    || player.hasPermission("alcatraz.autorestart.admin"))
                    ? msgDelayStaff
                    : msgDelayGlobal;
            player.sendMessage(formatComponent(replaceVars(template, addedSeconds, currentReason)));
        }
    }

    private void playIntervalSound(int sec) {
        float pitch = intervalSoundPitch;
        if (progressivePitchLast10s && sec <= 10 && sec >= 1) {
            pitch = 1.0f + (10 - sec) * 0.1f;
        }
        playNamedSoundToAll(intervalSoundName, intervalSoundVol, pitch);
    }

    public void playSound(CommandSender sender, String soundName, float volume, float pitch) {
        if (!(sender instanceof Player player)) {
            return;
        }
        Sound sound = resolveSound(soundName);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private void playNamedSoundToAll(String soundName, float volume, float pitch) {
        Sound sound = resolveSound(soundName);
        if (sound == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private Sound resolveSound(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String normalized = rawName.trim().toLowerCase(Locale.ROOT).replace('_', '.');
        Sound fromRegistry = Registry.SOUNDS.get(NamespacedKey.minecraft(normalized));
        if (fromRegistry != null) {
            return fromRegistry;
        }
        try {
            return Sound.valueOf(rawName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void dispatchTimedCommands(List<CommandSpec> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        DayOfWeek today = LocalDate.now(zoneId).getDayOfWeek();
        for (CommandSpec spec : list) {
            String cmd = spec.commandFor(today);
            if (cmd != null && !cmd.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }
    }

    private void dispatchProxyCommands(List<CommandSpec> list, String prefixPayload) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (CommandSpec spec : list) {
            sendSingleProxyCommand(spec, prefixPayload);
        }
    }

    private void sendSingleProxyCommand(CommandSpec spec, String prefixPayload) {
        String cmd = spec.commandFor(LocalDate.now(zoneId).getDayOfWeek());
        if (cmd == null || cmd.isEmpty()) {
            return;
        }
        var iterator = Bukkit.getOnlinePlayers().iterator();
        if (!iterator.hasNext()) {
            return;
        }
        Player carrier = iterator.next();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new DataOutputStream(baos).writeUTF(prefixPayload + cmd);
            carrier.sendPluginMessage(plugin, proxyChannel, baos.toByteArray());
        } catch (IOException ex) {
            plugin.getLogger().warning("[AutoRestart] Error enviando comando al proxy: " + ex.getMessage());
        }
    }

    private void sendWebhookAsync(String eventKey, String reason) {
        if (!webhookEnabled || webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        WebhookTemplate tpl = webhookTemplates.get(eventKey);
        if (tpl == null || !tpl.enabled || tpl.content == null || tpl.content.isBlank()) {
            return;
        }
        String ts = ZonedDateTime.now(zoneId).format(webhookTimestampFmt);
        String bodyText = tpl.content
                .replace("{TIMESTAMP}", ts)
                .replace("{SECONDS}", Integer.toString(Math.max(0, remainingSeconds)))
                .replace("{FORMATTED}", formatSeconds(remainingSeconds))
                .replace("{REASON}", reason != null ? reason : defaultReason);

        String json = "{\"content\":\"" + escapeJson(bodyText) + "\"}";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl.trim()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HTTP_CLIENT.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("[AutoRestart] Fallo al enviar webhook: " + ex.getMessage());
                        return null;
                    });
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[AutoRestart] URL de webhook inválida: " + ex.getMessage());
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    // =====================================================
    // API pública y comandos de control
    // =====================================================

    public void forceRestart(int seconds, String reason) {
        String effectiveReason = (reason != null && !reason.isBlank()) ? reason : defaultReason;
        resetCountdown(seconds, effectiveReason);
        if (tickTask == null || tickTask.isCancelled()) {
            this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        }
        sendWebhookAsync("SERVER_FORCED_RESTART", effectiveReason);
        String msg = replaceVars(msgForcedGlobal, Math.max(0, seconds), effectiveReason);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(formatComponent(msg));
            playSound(player, restartSoundName, 1.0f, 1.0f);
        }
    }

    public void delayRestart(int addedSeconds) {
        if (!running || remainingSeconds < 0) {
            return;
        }
        int updated = Math.addExact(Math.max(0, remainingSeconds), addedSeconds);
        resetCountdown(updated, currentReason);
        announceDelay(addedSeconds);
    }

    public boolean stopRestart() {
        if (!running) {
            return false;
        }
        this.running = false;
        this.remainingSeconds = -1;
        String msg = replaceVars(msgStoppedGlobal, 0, currentReason);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(formatComponent(msg));
        }
        return true;
    }

    public boolean isRunning() {
        return running;
    }

    public int getRemainingSeconds() {
        if (!running || remainingSeconds < 0) {
            return -1;
        }
        this.remainingSeconds = computeSecondsLeftFromClock();
        return this.remainingSeconds;
    }

    public int getAfterRebootSeconds() {
        long elapsed = System.nanoTime() - bootNanoTime;
        return (int) Math.max(0L, TimeUnit.NANOSECONDS.toSeconds(elapsed));
    }

    public String getInfoMessage() {
        int sec = getRemainingSeconds();
        if (sec < 0) {
            return replaceVars(msgInfo, -1, currentReason);
        }
        return replaceVars(msgInfo, sec, currentReason);
    }

    public String getInvalidSoundName() {
        return invalidSoundName;
    }

    public String getPrefix() {
        return prefix;
    }

    public String formatSeconds(int totalSeconds) {
        if (totalSeconds < 0) {
            return unscheduledLabel;
        }
        if (totalSeconds == 0) {
            return "0" + fmtSeconds;
        }
        long days = TimeUnit.SECONDS.toDays(totalSeconds);
        long hours = TimeUnit.SECONDS.toHours(totalSeconds) % 24;
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
        long seconds = totalSeconds % 60;

        List<String> parts = new ArrayList<>(4);
        if (days > 0) parts.add(days + (days == 1 ? fmtDay : fmtDays));
        if (hours > 0) parts.add(hours + (hours == 1 ? fmtHour : fmtHours));
        if (minutes > 0) parts.add(minutes + (minutes == 1 ? fmtMinute : fmtMinutes));
        if (seconds > 0) parts.add(seconds + (seconds == 1 ? fmtSecond : fmtSeconds));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            sb.append(parts.get(i));
            if (i < parts.size() - 2) {
                sb.append(" ");
            } else if (i == parts.size() - 2) {
                if (!sb.toString().endsWith(" ")) {
                    sb.append(" ");
                }
                sb.append(fmtSplitter);
            }
        }
        return sb.toString().trim();
    }

    public String formatHHMMSS(int totalSeconds) {
        if (totalSeconds < 0) {
            return "00:00:00";
        }
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    public String replaceVars(String template, int seconds, String reason) {
        if (template == null) {
            return "";
        }
        String formatted = formatSeconds(seconds);
        return template
                .replace("{PREFIX}", prefix != null ? prefix : "")
                .replace("%prefix%", prefix != null ? prefix : "")
                .replace("{0}", formatted)
                .replace("%time%", formatted)
                .replace("%seconds%", Integer.toString(Math.max(0, seconds)))
                .replace("{1}", reason != null ? reason : defaultReason)
                .replace("%reason%", reason != null ? reason : defaultReason);
    }

    public static Component formatComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return LEGACY_HEX.deserialize(applyGradients(text));
    }

    private static String applyGradients(String input) {
        Matcher matcher = GRADIENT_PATTERN.matcher(input);
        if (!matcher.find()) {
            return input;
        }
        matcher.reset();
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String startHex = matcher.group(1);
            String body = matcher.group(2);
            String endHex = matcher.group(3);
            matcher.appendReplacement(out, Matcher.quoteReplacement(interpolateHex(startHex, endHex, body)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String interpolateHex(String startHex, String endHex, String text) {
        int r1 = Integer.parseInt(startHex.substring(0, 2), 16);
        int g1 = Integer.parseInt(startHex.substring(2, 4), 16);
        int b1 = Integer.parseInt(startHex.substring(4, 6), 16);
        int r2 = Integer.parseInt(endHex.substring(0, 2), 16);
        int g2 = Integer.parseInt(endHex.substring(2, 4), 16);
        int b2 = Integer.parseInt(endHex.substring(4, 6), 16);

        // Contar caracteres visibles excluyendo códigos de formato (&l, &o, etc.)
        int visibleLen = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&' && i + 1 < text.length()) {
                i++;
                continue;
            }
            visibleLen++;
        }
        if (visibleLen == 0) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        StringBuilder activeStyles = new StringBuilder();
        int step = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'r') {
                    activeStyles.setLength(0);
                } else if ("klmno".indexOf(code) >= 0) {
                    activeStyles.append('&').append(code);
                }
                i++;
                continue;
            }
            float ratio = visibleLen == 1 ? 0f : (float) step / (visibleLen - 1);
            int r = Math.round(r1 + ratio * (r2 - r1));
            int g = Math.round(g1 + ratio * (g2 - g1));
            int b = Math.round(b1 + ratio * (b2 - b1));
            result.append(String.format(Locale.ROOT, "&#%02X%02X%02X", r, g, b))
                    .append(activeStyles)
                    .append(c);
            step++;
        }
        return result.toString();
    }

    public int parseTimeArgument(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1;
        }
        String val = raw.trim().toLowerCase(Locale.ROOT);
        try {
            if (val.endsWith("h")) {
                return Math.multiplyExact(Integer.parseInt(val.substring(0, val.length() - 1)), 3600);
            }
            if (val.endsWith("m")) {
                return Math.multiplyExact(Integer.parseInt(val.substring(0, val.length() - 1)), 60);
            }
            if (val.endsWith("s")) {
                return Integer.parseInt(val.substring(0, val.length() - 1));
            }
            if (val.contains(":")) {
                String[] parts = val.split(":");
                if (parts.length == 2) {
                    int hours = Integer.parseInt(parts[0]);
                    int minutes = Integer.parseInt(parts[1]);
                    return Math.addExact(Math.multiplyExact(hours, 3600), Math.multiplyExact(minutes, 60));
                }
            }
            return Integer.parseInt(val);
        } catch (NumberFormatException | ArithmeticException ex) {
            return -1;
        }
    }

    private record ScheduleEntry(DayOfWeek day, LocalTime time) {}

    private record WebhookTemplate(boolean enabled, String content) {}

    private record CommandSpec(DayOfWeek dayFilter, String command, int delaySeconds) {
        String commandFor(DayOfWeek today) {
            if (dayFilter != null && dayFilter != today) {
                return null;
            }
            return command;
        }
    }
}
