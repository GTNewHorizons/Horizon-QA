package com.gtnewhorizons.horizonqa;

public final class GameTestJvmFlags {

    public static final String PROPERTY = "gtnh.horizonqa";

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "false"));

    private GameTestJvmFlags() {}

    public static boolean isEnabled() {
        return ENABLED;
    }
}
