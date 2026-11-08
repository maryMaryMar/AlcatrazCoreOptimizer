package com.alcatrazm.plugin.cosmetics;

import org.bukkit.Material;

public enum CosmeticType {
    BLAZE_SWORDS(
            "blaze_swords",
            "&6&lEspadas del Blaze",
            "alcatraz.cosmetics.blaze_swords",
            "blaze_swords_cosmetic",
            Material.BLAZE_ROD,
            "&74 espadas llameantes que orbitan",
            "&7continuamente alrededor de tu cintura.",
            "",
            "&e▶ Clic para equipar / desequipar"
    ),
    TENEBRIS_HALO(
            "tenebris_halo",
            "&5&lHalo de Tenebris",
            "alcatraz.cosmetics.tenebris_halo",
            "tenebris_halo_cosmetic",
            Material.ENDER_EYE,
            "&78 orbes oscuros que rotan en",
            "&7un círculo vertical detrás de tu espalda.",
            "",
            "&e▶ Clic para equipar / desequipar"
    );

    private final String id;
    private final String displayName;
    private final String permission;
    private final String modelId;
    private final Material icon;
    private final String[] description;

    CosmeticType(String id, String displayName, String permission, String modelId, Material icon, String... description) {
        this.id = id;
        this.displayName = displayName;
        this.permission = permission;
        this.modelId = modelId;
        this.icon = icon;
        this.description = description;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getPermission() { return permission; }
    public String getModelId() { return modelId; }
    public Material getIcon() { return icon; }
    public String[] getDescription() { return description; }

    public static CosmeticType fromString(String str) {
        if (str == null) return null;
        for (CosmeticType type : values()) {
            if (type.id.equalsIgnoreCase(str) || type.name().equalsIgnoreCase(str) || type.modelId.equalsIgnoreCase(str)) {
                return type;
            }
        }
        return null;
    }
}
