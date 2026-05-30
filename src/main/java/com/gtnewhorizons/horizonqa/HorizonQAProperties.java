package com.gtnewhorizons.horizonqa;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;

public final class HorizonQAProperties {

    public static final String MODE_PROPERTY = "horizonqa.mode";
    public static final String TESTS_PROPERTY = "horizonqa.tests";
    public static final String ALLOW_NO_TESTS_PROPERTY = "horizonqa.allowNoTests";
    public static final String REPORT_FILE_PROPERTY = "horizonqa.reportFile";
    public static final String REPORT_DIR_PROPERTY = "horizonqa.reportDir";
    public static final String STATUS_FILE_PROPERTY = "horizonqa.statusFile";
    public static final String EVENTS_PROPERTY = "horizonqa.events";

    private static final String DEFAULT_JUNIT_REPORT = "TEST-horizonqa.xml";
    private static final String DEFAULT_STATUS_REPORT = "horizonqa-result.json";

    private static final ParsedProperties PARSED = parse();

    private HorizonQAProperties() {}

    public static Mode mode() {
        return PARSED.mode();
    }

    public static String rawMode() {
        return PARSED.rawMode();
    }

    public static boolean hasModeError() {
        return PARSED.modeIssue() != null;
    }

    public static String modeError() {
        return PARSED.modeIssue() != null ? PARSED.modeIssue()
            .message() : null;
    }

    public static boolean isOff() {
        return PARSED.mode() == Mode.OFF;
    }

    public static boolean isActive() {
        return PARSED.mode() == Mode.INTERACTIVE || PARSED.mode() == Mode.CI;
    }

    public static boolean isInteractive() {
        return PARSED.mode() == Mode.INTERACTIVE;
    }

    public static boolean isCi() {
        return PARSED.mode() == Mode.CI;
    }

    public static String modeName() {
        return PARSED.mode()
            .name()
            .toLowerCase();
    }

    public static String rawTests() {
        return PARSED.rawTests();
    }

    public static boolean selectsAllTests() {
        return PARSED.selectsAllTests();
    }

    public static List<TestSelector> testSelectors() {
        return PARSED.testSelectors();
    }

    public static boolean allowNoTests() {
        return PARSED.allowNoTests();
    }

    public static String rawAllowNoTests() {
        return PARSED.rawAllowNoTests();
    }

    public static String reportFile() {
        return PARSED.reportFile();
    }

    public static String reportDir() {
        return PARSED.reportDir();
    }

    public static String statusFile() {
        return PARSED.statusFile();
    }

    public static File junitReportFile() {
        if (PARSED.reportFile() != null) {
            return new File(PARSED.reportFile());
        }
        if (PARSED.reportDir() != null) {
            return new File(PARSED.reportDir(), DEFAULT_JUNIT_REPORT);
        }
        return new File(DEFAULT_JUNIT_REPORT);
    }

    public static File statusReportFile() {
        if (PARSED.statusFile() != null) {
            return new File(PARSED.statusFile());
        }
        if (PARSED.reportDir() != null) {
            return new File(PARSED.reportDir(), DEFAULT_STATUS_REPORT);
        }
        return new File(DEFAULT_STATUS_REPORT);
    }

    public static boolean eventsEnabled() {
        return PARSED.eventsEnabled();
    }

    public static String rawEvents() {
        return PARSED.rawEvents();
    }

    public static List<PropertyIssue> propertyIssues() {
        return PARSED.issues();
    }

    public static List<PropertyIssue> ciInfrastructureIssues() {
        List<PropertyIssue> issues = new ArrayList<>();
        if (PARSED.modeIssue() != null) {
            issues.add(PARSED.modeIssue());
            return Collections.unmodifiableList(issues);
        }
        if (!isCi()) {
            return Collections.emptyList();
        }
        for (PropertyIssue issue : PARSED.issues()) {
            if (issue.fatalInCi()) {
                issues.add(issue);
            }
        }
        return Collections.unmodifiableList(issues);
    }

    private static ParsedProperties parse() {
        List<PropertyIssue> issues = new ArrayList<>();

        String rawMode = System.getProperty(MODE_PROPERTY);
        ModeParseResult mode = parseMode(rawMode);
        if (mode.issue() != null) {
            issues.add(mode.issue());
        }

        String rawTests = System.getProperty(TESTS_PROPERTY);
        SelectorParseResult selectors = parseSelectors(rawTests);
        issues.addAll(selectors.issues());

        String rawAllowNoTests = System.getProperty(ALLOW_NO_TESTS_PROPERTY);
        BooleanParseResult allowNoTests = parseStrictBoolean(ALLOW_NO_TESTS_PROPERTY, rawAllowNoTests, false);
        if (allowNoTests.issue() != null) {
            issues.add(allowNoTests.issue());
        }

        String reportFile = parsePathProperty(REPORT_FILE_PROPERTY, System.getProperty(REPORT_FILE_PROPERTY), issues);
        String reportDir = parsePathProperty(REPORT_DIR_PROPERTY, System.getProperty(REPORT_DIR_PROPERTY), issues);
        String statusFile = parsePathProperty(STATUS_FILE_PROPERTY, System.getProperty(STATUS_FILE_PROPERTY), issues);

        String rawEvents = System.getProperty(EVENTS_PROPERTY);
        EventsParseResult events = parseEvents(rawEvents);
        if (events.issue() != null) {
            issues.add(events.issue());
        }

        return new ParsedProperties(
            rawMode,
            mode.mode(),
            mode.issue(),
            rawTests,
            selectors.selectsAll(),
            selectors.selectors(),
            rawAllowNoTests,
            allowNoTests.value(),
            reportFile,
            reportDir,
            statusFile,
            rawEvents,
            events.enabled(),
            Collections.unmodifiableList(new ArrayList<>(issues)));
    }

    private static ModeParseResult parseMode(String raw) {
        if (raw == null) {
            return new ModeParseResult(Mode.INTERACTIVE, null);
        }
        switch (raw) {
            case "off" -> {
                return new ModeParseResult(Mode.OFF, null);
            }
            case "interactive" -> {
                return new ModeParseResult(Mode.INTERACTIVE, null);
            }
            case "ci" -> {
                return new ModeParseResult(Mode.CI, null);
            }
        }
        String value = renderRawValue(raw);
        return new ModeParseResult(
            Mode.OFF,
            configIssue(
                "config:" + MODE_PROPERTY,
                MODE_PROPERTY,
                "Invalid -D" + MODE_PROPERTY + "=" + value + " (expected one of: off, interactive, ci)",
                true));
    }

    private static SelectorParseResult parseSelectors(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new SelectorParseResult(true, Collections.emptyList(), Collections.emptyList());
        }

        List<TestSelector> selectors = new ArrayList<>();
        List<PropertyIssue> issues = new ArrayList<>();
        String[] tokens = raw.split(",", -1);
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if (token.isEmpty()) {
                issues.add(
                    new PropertyIssue(
                        "selector:invalid",
                        "INVALID_SELECTOR",
                        TESTS_PROPERTY,
                        "Invalid -D" + TESTS_PROPERTY + ": empty selector token at position " + (i + 1),
                        true));
                continue;
            }

            int colon = token.indexOf(':');
            if (colon < 0) {
                selectors.add(new TestSelector(SelectorType.NAMESPACE, token));
                continue;
            }

            if (colon == token.length() - 1) {
                issues.add(
                    invalidSelector("Invalid selector '" + token + "' (use namespace without ':' to select all tests)"));
                continue;
            }

            String localId = token.substring(colon + 1);
            if (localId.indexOf('.') < 0) {
                issues.add(
                    invalidSelector(
                        "Invalid selector '"
                            + token
                            + "' (expected exact test id in the form namespace:Class.method)"));
                continue;
            }

            selectors.add(new TestSelector(SelectorType.EXACT_TEST_ID, token));
        }

        return new SelectorParseResult(
            false,
            Collections.unmodifiableList(new ArrayList<>(selectors)),
            Collections.unmodifiableList(new ArrayList<>(issues)));
    }

    private static BooleanParseResult parseStrictBoolean(String property, String raw, boolean defaultValue) {
        if (raw == null) {
            return new BooleanParseResult(defaultValue, null);
        }
        switch (raw) {
            case "true" -> {
                return new BooleanParseResult(true, null);
            }
            case "false" -> {
                return new BooleanParseResult(false, null);
            }
        }
        return new BooleanParseResult(
            defaultValue,
            configIssue(
                "config:" + property,
                property,
                "Invalid -D" + property + "=" + renderRawValue(raw) + " (expected true or false)",
                true));
    }

    private static String parsePathProperty(String property, String raw, List<PropertyIssue> issues) {
        if (raw == null) {
            return null;
        }
        if (raw.isEmpty()) {
            issues.add(
                configIssue(
                    "config:" + property,
                    property,
                    "Invalid -D" + property + "=<empty> (expected a file system path)",
                    true));
        }
        return raw;
    }

    private static EventsParseResult parseEvents(String raw) {
        if (raw == null) {
            return new EventsParseResult(true, null);
        }
        switch (raw) {
            case "on" -> {
                return new EventsParseResult(true, null);
            }
            case "off" -> {
                return new EventsParseResult(false, null);
            }
        }
        return new EventsParseResult(
            true,
            configIssue(
                "config:" + EVENTS_PROPERTY,
                EVENTS_PROPERTY,
                "Invalid -D" + EVENTS_PROPERTY + "=" + renderRawValue(raw) + " (expected on or off)",
                true));
    }

    private static PropertyIssue invalidSelector(String message) {
        return new PropertyIssue("selector:invalid", "INVALID_SELECTOR", TESTS_PROPERTY, message, true);
    }

    private static PropertyIssue configIssue(String id, String property, String message, boolean fatalInCi) {
        return new PropertyIssue(id, "CONFIG_ERROR", property, message, fatalInCi);
    }

    private static String renderRawValue(String raw) {
        return raw == null ? "<unset>" : raw.isEmpty() ? "<empty>" : raw;
    }

    public enum Mode {
        OFF,
        INTERACTIVE,
        CI
    }

    public enum SelectorType {
        NAMESPACE,
        EXACT_TEST_ID
    }

    @Desugar
    public record TestSelector(SelectorType type, String value) {

    }

    @Desugar
    public record PropertyIssue(String id, String kind, String property, String message, boolean fatalInCi) {

    }

    @Desugar
    private record ParsedProperties(
        String rawMode,
        Mode mode,
        PropertyIssue modeIssue,
        String rawTests,
        boolean selectsAllTests,
        List<TestSelector> testSelectors,
        String rawAllowNoTests,
        boolean allowNoTests,
        String reportFile,
        String reportDir,
        String statusFile,
        String rawEvents,
        boolean eventsEnabled,
        List<PropertyIssue> issues) {

    }

    @Desugar
    private record ModeParseResult(Mode mode, PropertyIssue issue) {

    }

    @Desugar
    private record SelectorParseResult(boolean selectsAll, List<TestSelector> selectors, List<PropertyIssue> issues) {

    }

    @Desugar
    private record BooleanParseResult(boolean value, PropertyIssue issue) {

    }

    @Desugar
    private record EventsParseResult(boolean enabled, PropertyIssue issue) {

    }
}
