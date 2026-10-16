package com.alcatrazm.plugin.chestlock;

public enum LockType {
    PRIVATE("Privado", "&1[Privado]"),
    PUBLIC("Público", "&2[Público]"),
    DONATION("Donación", "&5[Donación]");

    private final String display;
    private final String signTag;

    LockType(String display, String signTag) {
        this.display = display;
        this.signTag = signTag;
    }

    public String getDisplay() {
        return display;
    }

    public String getSignTag() {
        return signTag;
    }

    public static LockType fromString(String str) {
        if (str == null) return PRIVATE;
        for (LockType t : values()) {
            if (t.name().equalsIgnoreCase(str) || t.getDisplay().equalsIgnoreCase(str) || t.getSignTag().contains(str)) {
                return t;
            }
        }
        return PRIVATE;
    }
}
