package com.alcatrazm.plugin.sellwand;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class SellWandTier {

    private final String id;
    private final String name;
    private final double multiplier;
    private final int maxUses;
    private final boolean infinite;
    private final Material material;
    private final int customModelData;
    private final List<String> loreTemplate;

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat INT_FORMAT = new DecimalFormat("#,###");

    public SellWandTier(String id, String name, double multiplier, int maxUses, boolean infinite,
                        Material material, int customModelData, List<String> loreTemplate) {
        this.id = id.toUpperCase();
        this.name = name;
        this.multiplier = multiplier;
        this.maxUses = maxUses;
        this.infinite = infinite;
        this.material = material != null ? material : Material.BLAZE_ROD;
        this.customModelData = customModelData;
        this.loreTemplate = loreTemplate != null ? loreTemplate : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public boolean isInfinite() {
        return infinite;
    }

    public Material getMaterial() {
        return material;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public List<String> getLoreTemplate() {
        return loreTemplate;
    }

    public List<String> buildLore(int currentUses, double totalSold, int itemsSold) {
        return buildLore(currentUses, totalSold, itemsSold, this.multiplier);
    }

    public List<String> buildLore(int currentUses, double totalSold, int itemsSold, double effectiveMultiplier) {
        List<String> result = new ArrayList<>();
        String usesStr = infinite ? "§dInfinitos" : String.valueOf(currentUses);
        String maxUsesStr = infinite ? "∞" : String.valueOf(maxUses);
        String progressBar = buildProgressBar(currentUses, maxUses, infinite);
        double mult = effectiveMultiplier > 0 ? effectiveMultiplier : this.multiplier;
        String multiplierStr = String.format(java.util.Locale.US, "%.2f", mult);

        for (String line : loreTemplate) {
            String formatted = line
                    .replace("%usos%", usesStr)
                    .replace("%usos_max%", maxUsesStr)
                    .replace("%barra_progreso%", progressBar)
                    .replace("%multiplicador%", multiplierStr)
                    .replace("%total_ganado%", MONEY_FORMAT.format(totalSold))
                    .replace("%items_vendidos%", INT_FORMAT.format(itemsSold))
                    .replace("%tier%", name);
            result.add(colorize(formatted));
        }
        return result;
    }

    private String buildProgressBar(int current, int max, boolean isInf) {
        if (isInf) {
            return "§a[§b■■■■■■■■■■§a]";
        }
        if (max <= 0) return "§7[□□□□□□□□□□]";

        int totalBars = 10;
        float percent = (float) Math.max(0, Math.min(current, max)) / max;
        int filledBars = Math.round(totalBars * percent);

        StringBuilder sb = new StringBuilder("§8[");
        if (percent > 0.5) {
            sb.append("§a");
        } else if (percent > 0.2) {
            sb.append("§e");
        } else {
            sb.append("§c");
        }

        for (int i = 0; i < totalBars; i++) {
            if (i == filledBars) {
                sb.append("§7");
            }
            sb.append("■");
        }
        sb.append("§8]");
        return sb.toString();
    }

    public static String colorize(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
