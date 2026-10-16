package com.alcatrazm.plugin.protection;

public enum ClaimType {
    PERMANENT("Permanente / Admin"),
    NORMAL("Normal / Bloque Fijo"),
    TEMPORARY("Temporal (1 Semana)");

    private final String display;

    ClaimType(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }

    public static ClaimType fromString(String str) {
        if (str == null) return NORMAL;
        if ("a".equalsIgnoreCase(str) || "NORMAL".equalsIgnoreCase(str)) return NORMAL;
        if ("b".equalsIgnoreCase(str) || "TEMPORARY".equalsIgnoreCase(str)) return TEMPORARY;
        if ("c".equalsIgnoreCase(str) || "PERMANENT".equalsIgnoreCase(str)) return PERMANENT;
        try {
            return valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
