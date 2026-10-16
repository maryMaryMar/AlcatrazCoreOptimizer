package com.alcatrazm.plugin.mounts;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public enum MountType {
    // 1. Terrestres
    FOX("zorro", "Zorro Ancestral", EntityType.FOX, Material.FOX_SPAWN_EGG, 2.0, 0.42, 0.55, false, false, true, false, false),
    RABBIT("conejo", "Conejo Colosal", EntityType.RABBIT, Material.RABBIT_FOOT, 2.7, 0.38, 0.85, false, false, true, false, false),
    ARMADILLO("armadillo", "Armadillo Veloz", EntityType.ARMADILLO, Material.ARMADILLO_SCUTE, 2.2, 0.48, 0.50, false, false, true, false, false),
    CAT("gato", "Gato Pantera", EntityType.CAT, Material.STRING, 2.4, 0.45, 0.60, false, false, true, false, false),
    POLAR_BEAR("osopolar", "Oso Polar Guerrero", EntityType.POLAR_BEAR, Material.SNOWBALL, 1.3, 0.38, 0.50, false, false, true, false, false),
    PANDA("panda", "Oso Panda Guardián", EntityType.PANDA, Material.BAMBOO, 1.4, 0.34, 0.50, false, false, true, false, false),
    SNIFFER("sniffer", "Sniffer Arcaico", EntityType.SNIFFER, Material.SNIFFER_EGG, 1.0, 0.32, 0.50, false, false, true, false, false),

    // 2. Voladoras y Especiales
    PHANTOM("phantom", "Phantom del Abismo", EntityType.PHANTOM, Material.PHANTOM_MEMBRANE, 1.25, 1.55, 0.75, true, false, true, true, false),
    MINIDRAGON("minidragon", "MiniDragón del End", EntityType.ENDER_DRAGON, Material.DRAGON_HEAD, 0.20, 2.25, 0.85, true, false, true, false, true),
    BAT("murcielago", "Murciélago Espectral", EntityType.BAT, Material.FEATHER, 2.5, 0.75, 0.55, true, false, true, false, false),
    BEE("abeja", "Abeja Reina Melífera", EntityType.BEE, Material.HONEYCOMB, 2.0, 0.52, 0.65, true, false, true, false, false),

    // 3. Acuáticas y Anfibias
    SALMON("salmon", "Salmón Titán", EntityType.SALMON, Material.SALMON, 3.5, 0.45, 0.40, false, true, true, false, false),
    DOLPHIN("delfin", "Delfín de las Profundidades", EntityType.DOLPHIN, Material.PRISMARINE_CRYSTALS, 1.5, 0.55, 0.65, false, true, true, false, false),
    AXOLOTL("ajolote", "Ajolote Místico", EntityType.AXOLOTL, Material.AXOLOTL_BUCKET, 3.0, 0.46, 0.55, false, true, true, false, false),

    // 4. Mascota Compañera (No Montura)
    ALLAY("allay", "Hada Allay Protectora", EntityType.ALLAY, Material.AMETHYST_SHARD, 1.0, 0.35, 0.0, true, false, false, false, false);

    private final String id;
    private final String displayName;
    private final EntityType entityType;
    private final Material iconMaterial;
    private final double defaultScale;
    private final double defaultSpeed;
    private final double defaultJump;
    private final boolean flying;
    private final boolean aquatic;
    private final boolean rideable;
    private final boolean premium;
    private final boolean retaliateOnly;

    MountType(String id, String displayName, EntityType entityType, Material iconMaterial, 
              double defaultScale, double defaultSpeed, double defaultJump, 
              boolean flying, boolean aquatic, boolean rideable, boolean premium, boolean retaliateOnly) {
        this.id = id;
        this.displayName = displayName;
        this.entityType = entityType;
        this.iconMaterial = iconMaterial;
        this.defaultScale = defaultScale;
        this.defaultSpeed = defaultSpeed;
        this.defaultJump = defaultJump;
        this.flying = flying;
        this.aquatic = aquatic;
        this.rideable = rideable;
        this.premium = premium;
        this.retaliateOnly = retaliateOnly;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public Material getIconMaterial() {
        return iconMaterial;
    }

    public double getDefaultScale() {
        return defaultScale;
    }

    public double getDefaultSpeed() {
        return defaultSpeed;
    }

    public double getDefaultJump() {
        return defaultJump;
    }

    public boolean isFlying() {
        return flying;
    }

    public boolean isAquatic() {
        return aquatic;
    }

    public boolean isRideable() {
        return rideable;
    }

    public boolean isPremium() {
        return premium;
    }

    public boolean isRetaliateOnly() {
        return retaliateOnly;
    }

    public static MountType fromString(String text) {
        if (text == null) return null;
        for (MountType type : values()) {
            if (type.name().equalsIgnoreCase(text) || type.id.equalsIgnoreCase(text)) {
                return type;
            }
        }
        return null;
    }
}
