package com.alcatrazm.plugin.anticaps;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntiCapsManager {

    private final AlcatrazPlugin plugin;

    private boolean enabled = true;
    private boolean bypassOps = false;
    private String bypassPermission = "alcatraz.anticaps.bypass";
    private int minLength = 5;
    private int maxUppercaseWords = 1;
    private double maxCapsPercentage = 50.0;

    // Coincide con palabras de letras unicode
    private static final Pattern WORD_PATTERN = Pattern.compile("(?U)\\b\\p{L}+\\b");

    public AntiCapsManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("anti-caps.enabled", true);
        this.bypassOps = plugin.getConfig().getBoolean("anti-caps.bypass-ops", false);
        this.bypassPermission = plugin.getConfig().getString("anti-caps.bypass-permission", "alcatraz.anticaps.bypass");
        this.minLength = plugin.getConfig().getInt("anti-caps.min-message-length", 5);
        this.maxUppercaseWords = plugin.getConfig().getInt("anti-caps.max-uppercase-words", 1);
        this.maxCapsPercentage = plugin.getConfig().getDouble("anti-caps.max-caps-percentage", 50.0);
    }

    public void reload() {
        loadConfig();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasBypass(Player player) {
        if (player == null) return true;
        if (player.hasPermission(bypassPermission)) {
            if (player.isOp()) {
                return bypassOps;
            }
            return true;
        }
        return false;
    }

    /**
     * Procesa el mensaje: si supera el límite de palabras en mayúsculas,
     * conserva como máximo `maxUppercaseWords` completas en mayúsculas y
     * convierte las palabras mayúsculas restantes a minúsculas.
     */
    public String processMessage(Player player, String message) {
        if (!enabled || message == null || message.length() < minLength) {
            return message;
        }

        if (hasBypass(player)) {
            return message;
        }

        int totalLetters = 0;
        int upperLetters = 0;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (Character.isLetter(c)) {
                totalLetters++;
                if (Character.isUpperCase(c)) {
                    upperLetters++;
                }
            }
        }

        if (totalLetters == 0) {
            return message;
        }

        double capsRatio = (upperLetters * 100.0) / totalLetters;

        // Contar cuántas palabras completas en mayúsculas existen (longitud >= 2)
        Matcher countMatcher = WORD_PATTERN.matcher(message);
        int uppercaseWordsCount = 0;
        while (countMatcher.find()) {
            String word = countMatcher.group();
            if (word.length() >= 2 && isAllUppercase(word)) {
                uppercaseWordsCount++;
            }
        }

        // Si no excede el límite de palabras ni el porcentaje general, se deja intacto
        if (uppercaseWordsCount <= maxUppercaseWords && capsRatio <= maxCapsPercentage) {
            return message;
        }

        // Transformación: permitir hasta maxUppercaseWords en mayúsculas, y el resto se minusculean
        Matcher matcher = WORD_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder(message.length());
        int allowedUppercaseWordsLeft = maxUppercaseWords;

        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() >= 2 && isAllUppercase(word)) {
                if (allowedUppercaseWordsLeft > 0) {
                    // Mantener esta palabra completa en mayúsculas
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(word));
                    allowedUppercaseWordsLeft--;
                } else {
                    // Minusculear esta palabra completa
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(word.toLowerCase(Locale.ROOT)));
                }
            } else if (capsRatio > maxCapsPercentage && isMixedUppercase(word)) {
                // Si el porcentaje es muy alto y hay palabras mezcladas raras, normalizar
                matcher.appendReplacement(sb, Matcher.quoteReplacement(word.toLowerCase(Locale.ROOT)));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(word));
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private boolean isAllUppercase(String word) {
        for (int i = 0; i < word.length(); i++) {
            if (!Character.isUpperCase(word.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isMixedUppercase(String word) {
        int uppers = 0;
        for (int i = 0; i < word.length(); i++) {
            if (Character.isUpperCase(word.charAt(i))) {
                uppers++;
            }
        }
        return uppers > 1 && uppers < word.length();
    }
}
