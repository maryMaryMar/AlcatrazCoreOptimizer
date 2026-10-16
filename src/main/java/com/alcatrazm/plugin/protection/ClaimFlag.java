package com.alcatrazm.plugin.protection;

public enum ClaimFlag {
    BREAK("Romper Bloques", false),
    PLACE("Colocar Bloques", false),
    INTERACT("Puertas, Botones, Palancas y Mecanismos", false),
    CONTAINERS("Cofres, Shulkers, Hornos, Barriles, Tolvas", false),
    PVP("Combate PvP entre Jugadores", false),
    MOB_DAMAGE("Dañar Animales y Mascotas", false),
    VILLAGER_TRADE("Comerciar con Aldeanos", false),
    VILLAGER_DAMAGE("Dañar Aldeanos", false),
    ITEM_FRAMES("Modificar o Romper Marcos y Cuadros", false),
    ARMOR_STANDS("Modificar o Romper Soportes de Armadura", false),
    VEHICLES("Colocar, Romper o Montar Botes y Vagonetas", false),
    HOSTILE_SPAWN("Aparición de Monstruos Hostiles", true),
    PASSIVE_SPAWN("Aparición de Animales Pacíficos", true),
    EXPLOSIONS("Explosiones de TNT, Creepers y Cristales", false),
    FIRE_SPREAD("Propagación de Fuego", false),
    LAVA_FLOW("Flujo de Lava Exterior hacia Adentro", false),
    WATER_FLOW("Flujo de Agua Exterior hacia Adentro", false),
    PISTONS("Mecanismos y Pistones Exteriores", false),
    CROP_TRAMPLE("Pisoteo de Cultivos y Farmland", false),
    ENDERPEARL("Teletransporte con Perlas de Ender", false),
    CHORUS_FRUIT("Teletransporte con Fruta Coral", false),
    LIGHTNING("Daño e Incendio por Rayos", false),
    FLY("Modo Vuelo dentro de la Protección", false);

    private final String description;
    private final boolean defaultNormalAllow;

    ClaimFlag(String description, boolean defaultNormalAllow) {
        this.description = description;
        this.defaultNormalAllow = defaultNormalAllow;
    }

    public String getDescription() {
        return description;
    }

    public boolean getDefaultNormalAllow() {
        return defaultNormalAllow;
    }

    public static ClaimFlag fromString(String name) {
        if (name == null) return null;
        switch (name.toLowerCase()) {
            case "a": return BREAK;
            case "b": return PLACE;
            case "c": return INTERACT;
            case "d": return CONTAINERS;
            case "e": return PVP;
            case "f": return MOB_DAMAGE;
            case "g": return VILLAGER_TRADE;
            case "h": return VILLAGER_DAMAGE;
            case "i": return ITEM_FRAMES;
            case "j": return ARMOR_STANDS;
            case "k": return VEHICLES;
            case "l": return HOSTILE_SPAWN;
            case "m": return PASSIVE_SPAWN;
            case "n": return EXPLOSIONS;
            case "o": return FIRE_SPREAD;
            case "p": return LAVA_FLOW;
            case "q": return WATER_FLOW;
            case "r": return PISTONS;
            case "s": return CROP_TRAMPLE;
            case "t": return ENDERPEARL;
            case "u": return CHORUS_FRUIT;
            case "v": return LIGHTNING;
        }
        for (ClaimFlag f : values()) {
            if (f.name().equalsIgnoreCase(name) 
                    || f.name().replace("_", "").equalsIgnoreCase(name)
                    || f.name().replace("_", "-").equalsIgnoreCase(name)) {
                return f;
            }
        }
        return null;
    }
}
