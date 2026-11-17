package com.alcatrazm.plugin.airdrop;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Configuracion de una categoria/rareza de AirDrop cargada dinamicamente desde config.yml.
 */
public record DropTier(
    String id,
    boolean enabled,
    String displayName,
    String description,
    int probability,
    int expiryMinutes,
    int minItems,
    int maxItems,
    Material blockMaterial,
    String colorStart,
    String colorEnd
) {

    public static String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim();
    }

    public static List<DropTier> defaultTiers() {
        return List.of(
            new DropTier("comun", true, "DROP COMUN", "botin comun", 65, 15, 3, 5, Material.CHEST, "#87DCAA", "#D2FFE4"),
            new DropTier("epico", true, "DROP EPICO", "botin epico", 25, 15, 4, 6, Material.TRAPPED_CHEST, "#B66BFF", "#F2A7FF"),
            new DropTier("legendario", true, "DROP LEGENDARIO", "botin legendario", 10, 20, 5, 8, Material.ENDER_CHEST, "#FFAA00", "#FFF078")
        );
    }

    public static List<DropTier> loadTiers(AirDropModule module) {
        ConfigurationSection sec = module.getPlugin().getConfig().getConfigurationSection("airdrop.tiers");
        if (sec == null || sec.getKeys(false).isEmpty()) {
            return defaultTiers();
        }
        List<DropTier> list = new ArrayList<>();
        for (String key : sec.getKeys(false)) {
            ConfigurationSection t = sec.getConfigurationSection(key);
            if (t == null) {
                continue;
            }
            String normId = normalizeId(key);
            boolean enabled = t.getBoolean("enabled", true);
            String displayName = t.getString("display-name", "DROP " + key.toUpperCase(Locale.ROOT));
            String description = t.getString("description", "botin " + normId);
            int probability = Math.max(0, t.getInt("probability", 10));
            int expiry = Math.clamp(t.getInt("expiry-minutes", module.getInt("drop.expiry-minutes", 15, 1, 1440)), 1, 1440);
            int minItems = Math.clamp(t.getInt("min-items", module.getInt("loot.minimum", 3, 1, 54)), 1, 54);
            int maxItems = Math.clamp(t.getInt("max-items", module.getInt("loot.maximum", 5, 1, 54)), minItems, 54);
            Material mat = Material.matchMaterial(t.getString("block", "CHEST"));
            if (mat == null || !mat.isBlock()) {
                mat = Material.CHEST;
            }
            String colorStart = t.getString("color-start", "#F8CB53");
            String colorEnd = t.getString("color-end", "#FFE6A0");
            list.add(new DropTier(normId, enabled, displayName, description, probability, expiry, minItems, maxItems, mat, colorStart, colorEnd));
        }
        return list.isEmpty() ? defaultTiers() : List.copyOf(list);
    }

    public static DropTier resolveTier(List<DropTier> tiers, String rawId) {
        String norm = normalizeId(rawId);
        for (DropTier tier : tiers) {
            if (tier.id.equals(norm)) {
                return tier;
            }
        }
        return null;
    }

    public static DropTier pickWeighted(List<DropTier> tiers, RandomGenerator random) {
        List<DropTier> candidates = tiers.stream().filter(t -> t.enabled && t.probability > 0).toList();
        if (candidates.isEmpty()) {
            return tiers.stream().filter(DropTier::enabled).findFirst().orElse(tiers.isEmpty() ? null : tiers.getFirst());
        }
        int total = candidates.stream().mapToInt(DropTier::probability).sum();
        int roll = random.nextInt(total);
        int acc = 0;
        for (DropTier tier : candidates) {
            acc += tier.probability;
            if (roll < acc) {
                return tier;
            }
        }
        return candidates.getLast();
    }

    public static double getChancePercent(List<DropTier> tiers, DropTier target) {
        if (!target.enabled || target.probability <= 0) {
            return 0.0;
        }
        int total = tiers.stream().filter(t -> t.enabled && t.probability > 0).mapToInt(DropTier::probability).sum();
        return total <= 0 ? 0.0 : (target.probability * 100.0) / total;
    }
}
