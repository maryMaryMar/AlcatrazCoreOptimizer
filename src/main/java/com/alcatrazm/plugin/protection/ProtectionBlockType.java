package com.alcatrazm.plugin.protection;

import org.bukkit.Material;

public enum ProtectionBlockType {
    COAL_30("Carbón", Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, 30, "&8Mena de Carbón Cuántica", "&730 bloques"),
    COPPER_35("Cobre", Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, 35, "&6Mena de Cobre Explorador", "&635 bloques"),
    IRON_50("Hierro", Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, 50, "&fMena de Hierro Reforzada", "&f50 bloques"),
    LAPIS_75("Lapislázuli", Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, 75, "&9Mena de Lapislázuli Mística", "&975 bloques"),
    REDSTONE_100("Redstone", Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, 100, "&cMena de Redstone Autómata", "&c100 bloques"),
    GOLD_100("Oro", Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, 100, "&6Mena de Oro Sagrada", "&6100 bloques"),
    DIAMOND_150("Diamante", Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, 150, "&bMena de Diamante Titán", "&b150 bloques"),
    EMERALD_300("Esmeralda", Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, 300, "&aMena de Esmeralda Suprema", "&a300 bloques"),
    NETHERITE_350("Netherite", Material.ANCIENT_DEBRIS, Material.NETHERITE_BLOCK, 350, "&8Mena Ancestral Legendaria", "&8350 bloques");

    private final String simpleName;
    private final Material material;
    private final Material deepslateMaterial;
    private final int radius;
    private final String displayName;
    private final String radiusFormatted;

    ProtectionBlockType(String simpleName, Material material, Material deepslateMaterial, int radius, String displayName, String radiusFormatted) {
        this.simpleName = simpleName;
        this.material = material;
        this.deepslateMaterial = deepslateMaterial;
        this.radius = radius;
        this.displayName = displayName;
        this.radiusFormatted = radiusFormatted;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public Material getMaterial() {
        return material;
    }

    public Material getDeepslateMaterial() {
        return deepslateMaterial;
    }

    public int getRadius() {
        return radius;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRadiusFormatted() {
        return radiusFormatted;
    }

    public static ProtectionBlockType fromString(String str) {
        if (str == null) return null;
        if ("a".equalsIgnoreCase(str)) return DIAMOND_150;
        if ("b".equalsIgnoreCase(str)) return COAL_30;
        if ("c".equalsIgnoreCase(str)) return IRON_50;
        if ("d".equalsIgnoreCase(str)) return GOLD_100;
        if ("e".equalsIgnoreCase(str)) return EMERALD_300;
        if ("copper".equalsIgnoreCase(str) || "cobre".equalsIgnoreCase(str)) return COPPER_35;
        if ("lapis".equalsIgnoreCase(str) || "lapislazuli".equalsIgnoreCase(str)) return LAPIS_75;
        if ("redstone".equalsIgnoreCase(str)) return REDSTONE_100;
        if ("netherite".equalsIgnoreCase(str) || "ancient".equalsIgnoreCase(str)) return NETHERITE_350;
        for (ProtectionBlockType type : values()) {
            if (type.name().equalsIgnoreCase(str) 
                    || type.getSimpleName().equalsIgnoreCase(str)
                    || type.name().toLowerCase().contains(str.toLowerCase())
                    || String.valueOf(type.getRadius()).equalsIgnoreCase(str)) {
                return type;
            }
        }
        return null;
    }
}
