package com.alcatrazm.plugin.antiflood;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class AntiFloodManager {

    private final AlcatrazPlugin plugin;

    private boolean enabled = true;
    private double chatCooldownSeconds = 0.5;
    private boolean blockDuplicates = true;
    private int duplicateCooldownSeconds = 3;
    private boolean trimRepeatedChars = true;
    private int maxRepeatChars = 3;

    private final Map<UUID, Long> lastChatTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastChatMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();

    private static final Pattern REPEATED_CHARS_PATTERN = Pattern.compile("(?i)(.)\\1{3,}");
    private boolean bypassOps = true;

    public AntiFloodManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("anti-flood.enabled", true);
        this.bypassOps = plugin.getConfig().getBoolean("anti-flood.bypass-ops", true);
        this.chatCooldownSeconds = plugin.getConfig().getDouble("anti-flood.chat-cooldown-seconds", 0.0);
        this.blockDuplicates = plugin.getConfig().getBoolean("anti-flood.block-duplicates", true);
        this.duplicateCooldownSeconds = plugin.getConfig().getInt("anti-flood.duplicate-cooldown-seconds", 3);
        this.trimRepeatedChars = plugin.getConfig().getBoolean("anti-flood.trim-repeated-characters", true);
        this.maxRepeatChars = plugin.getConfig().getInt("anti-flood.max-repeat-characters", 3);
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
        return player.hasPermission("alcatraz.antiflood.bypass") ||
               player.hasPermission("alcatraz.admin") ||
               player.hasPermission("alcatraz.mod") ||
               player.hasPermission("group.mod") ||
               player.hasPermission("alcatraz.antiflood.admin");
    }

    public ValidationResult checkMessage(Player player, String rawMessage) {
        if (!enabled || hasBypass(player)) {
            return new ValidationResult(true, rawMessage, null);
        }

        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        // 1. Verificación de Cooldown de Chat (solo si chatCooldownSeconds > 0)
        long cooldownMs = (long) (chatCooldownSeconds * 1000);
        if (cooldownMs > 0) {
            long lastTime = lastChatTime.getOrDefault(uuid, 0L);
            long elapsed = now - lastTime;
            if (elapsed < cooldownMs) {
                double remaining = (cooldownMs - elapsed) / 1000.0;
                return new ValidationResult(false, rawMessage,
                        String.format("§c[Alcatraz] ¡No escribas tan rápido! Espera %.1fs.", remaining));
            }
        }

        // 2. Recorte de caracteres repetidos si está activo
        String processed = rawMessage;
        if (trimRepeatedChars && processed != null) {
            processed = REPEATED_CHARS_PATTERN.matcher(processed).replaceAll("$1$1");
        }

        // 3. Verificación de mensaje duplicado / repetido
        if (blockDuplicates && processed != null) {
            String cleanCurrent = processed.trim().toLowerCase();
            // No bloquear mensajes cortos comunes (ej: "si", "no", "gg", "xd", "f", "ok", "jaja", etc.)
            if (cleanCurrent.length() > 3 && !isShortGreetingOrReaction(cleanCurrent)) {
                String previous = lastChatMessage.get(uuid);
                long prevTime = lastMessageTime.getOrDefault(uuid, 0L);
                long dupCooldownMs = (long) (duplicateCooldownSeconds * 1000);

                if (previous != null && previous.equals(cleanCurrent) && (now - prevTime < dupCooldownMs)) {
                    return new ValidationResult(false, rawMessage,
                            "§c[Alcatraz] No repitas el mismo mensaje tan seguido.");
                }
            }
        }

        // Actualizar registros del jugador
        lastChatTime.put(uuid, now);
        if (processed != null) {
            lastChatMessage.put(uuid, processed.trim().toLowerCase());
            lastMessageTime.put(uuid, now);
        }

        return new ValidationResult(true, processed, null);
    }

    private boolean isShortGreetingOrReaction(String msg) {
        return msg.equals("hola") || msg.equals("si") || msg.equals("no") || msg.equals("xd") ||
               msg.equals("xdd") || msg.equals("xddd") || msg.equals("gg") || msg.equals("ok") ||
               msg.equals("f") || msg.equals("jaja") || msg.equals("jajaja") || msg.equals("ty");
    }

    public void removePlayer(UUID uuid) {
        lastChatTime.remove(uuid);
        lastChatMessage.remove(uuid);
        lastMessageTime.remove(uuid);
    }

    public static class ValidationResult {
        public final boolean allowed;
        public final String finalMessage;
        public final String errorMessage;

        public ValidationResult(boolean allowed, String finalMessage, String errorMessage) {
            this.allowed = allowed;
            this.finalMessage = finalMessage;
            this.errorMessage = errorMessage;
        }
    }
}
