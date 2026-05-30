package com.gtnewhorizons.horizonqa;

import com.github.bsideup.jabel.Desugar;

public final class HorizonQAProperties {

    public static final String MODE_PROPERTY = "horizonqa.mode";

    private static final String RAW_MODE = System.getProperty(MODE_PROPERTY);
    private static final ParseResult MODE = parseMode(RAW_MODE);

    private HorizonQAProperties() {}

    public static Mode mode() {
        return MODE.mode;
    }

    public static String rawMode() {
        return RAW_MODE;
    }

    public static boolean hasModeError() {
        return MODE.error != null;
    }

    public static String modeError() {
        return MODE.error;
    }

    public static boolean isOff() {
        return MODE.mode == Mode.OFF;
    }

    public static boolean isActive() {
        return MODE.mode == Mode.INTERACTIVE || MODE.mode == Mode.CI;
    }

    public static boolean isInteractive() {
        return MODE.mode == Mode.INTERACTIVE;
    }

    public static boolean isCi() {
        return MODE.mode == Mode.CI;
    }

    public static String modeName() {
        return MODE.mode.name()
            .toLowerCase();
    }

    private static ParseResult parseMode(String raw) {
        if (raw == null) {
            return new ParseResult(Mode.INTERACTIVE, null);
        }
        switch (raw) {
            case "off" -> {
                return new ParseResult(Mode.OFF, null);
            }
            case "interactive" -> {
                return new ParseResult(Mode.INTERACTIVE, null);
            }
            case "ci" -> {
                return new ParseResult(Mode.CI, null);
            }
        }
        String value = raw.isEmpty() ? "<empty>" : raw;
        return new ParseResult(
            Mode.OFF,
            "Invalid -D" + MODE_PROPERTY + "=" + value + " (expected one of: off, interactive, ci)");
    }

    public enum Mode {
        OFF,
        INTERACTIVE,
        CI
    }

    @Desugar
    private record ParseResult(Mode mode, String error) {

    }
}
