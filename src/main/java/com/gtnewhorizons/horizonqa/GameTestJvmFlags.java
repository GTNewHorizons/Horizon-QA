package com.gtnewhorizons.horizonqa;

public final class GameTestJvmFlags {

    public static final String PROPERTY = "gtnh.gametest";

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "false"));

    private GameTestJvmFlags() {}

    public static boolean isEnabled() {
        return ENABLED;
    }
}
