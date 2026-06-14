package com.gtnewhorizons.horizonqa;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import com.github.bsideup.jabel.Desugar;

public final class HorizonQAProperties {

    public static final String MODE_PROPERTY = "horizonqa.mode";
    public static final String WORLD_PROPERTY = "horizonqa.world";
    public static final String AUTO_RUN_PROPERTY = "horizonqa.autoRun";
    public static final String STOP_SERVER_PROPERTY = "horizonqa.stopServer";
    public static final String GRID_ORIGIN_PROPERTY = "horizonqa.gridOrigin";
    public static final String TESTS_PROPERTY = "horizonqa.tests";
    public static final String ALLOW_NO_TESTS_PROPERTY = "horizonqa.allowNoTests";
    public static final String REPORT_FILE_PROPERTY = "horizonqa.reportFile";
    public static final String REPORT_DIR_PROPERTY = "horizonqa.reportDir";
    public static final String STATUS_FILE_PROPERTY = "horizonqa.statusFile";
    public static final String EVENTS_PROPERTY = "horizonqa.events";

    private static final String DEFAULT_JUNIT_REPORT = "TEST-horizonqa.xml";
    private static final String DEFAULT_STATUS_REPORT = "horizonqa-result.json";
    private static final GridOrigin DEFAULT_GRID_ORIGIN = new GridOrigin(0, 64, 0);

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
        return PARSED.mode() == Mode.INTERACTIVE || PARSED.mode() == Mode.REPORT || PARSED.mode() == Mode.CI;
    }

    public static boolean isInteractive() {
        return PARSED.mode() == Mode.INTERACTIVE;
    }

    public static boolean isReport() {
        return PARSED.mode() == Mode.REPORT;
    }

    public static boolean isCi() {
        return PARSED.mode() == Mode.CI;
    }

    public static boolean usesCiServerBehavior() {
        return usesHeadlessServerBehavior();
    }

    public static boolean usesHeadlessServerBehavior() {
        return PARSED.mode() == Mode.REPORT || PARSED.mode() == Mode.CI;
    }

    public static boolean usesReportedCommandBatches() {
        return isActive() && !interactiveFeaturesEnabled();
    }

    public static boolean interactiveFeaturesEnabled() {
        return PARSED.mode() == Mode.INTERACTIVE;
    }

    public static WorldPolicy worldPolicy() {
        return PARSED.worldPolicy();
    }

    public static String worldPolicyName() {
        return PARSED.worldPolicy()
            .name()
            .toLowerCase();
    }

    public static String rawWorld() {
        return PARSED.rawWorld();
    }

    public static boolean usesVoidWorld() {
        return PARSED.worldPolicy() == WorldPolicy.VOID;
    }

    public static boolean autoRunTests() {
        return PARSED.autoRunTests();
    }

    public static String rawAutoRun() {
        return PARSED.rawAutoRun();
    }

    public static boolean stopServerAfterRun() {
        return PARSED.stopServerAfterRun();
    }

    public static String rawStopServer() {
        return PARSED.rawStopServer();
    }

    public static int gridOriginX() {
        return PARSED.gridOrigin()
            .x();
    }

    public static int gridOriginY() {
        return PARSED.gridOrigin()
            .y();
    }

    public static int gridOriginZ() {
        return PARSED.gridOrigin()
            .z();
    }

    public static String gridOriginName() {
        GridOrigin origin = PARSED.gridOrigin();
        return origin.x() + "," + origin.y() + "," + origin.z();
    }

    public static String rawGridOrigin() {
        return PARSED.rawGridOrigin();
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
        return junitReportFile(PARSED, new File(System.getProperty("user.dir", ".")));
    }

    static File junitReportFile(ParsedProperties parsed, File workingDirectory) {
        if (parsed.reportFile() != null) {
            return resolveServerFile(workingDirectory, parsed.reportFile());
        }
        if (parsed.reportDir() != null) {
            return new File(resolveServerFile(workingDirectory, parsed.reportDir()), DEFAULT_JUNIT_REPORT).toPath()
                .normalize()
                .toFile();
        }
        return resolveServerFile(workingDirectory, DEFAULT_JUNIT_REPORT);
    }

    public static File statusReportFile() {
        return statusReportFile(PARSED, new File(System.getProperty("user.dir", ".")));
    }

    static File statusReportFile(ParsedProperties parsed, File workingDirectory) {
        if (parsed.statusFile() != null) {
            return resolveServerFile(workingDirectory, parsed.statusFile());
        }
        if (parsed.reportDir() != null) {
            return new File(resolveServerFile(workingDirectory, parsed.reportDir()), DEFAULT_STATUS_REPORT).toPath()
                .normalize()
                .toFile();
        }
        return resolveServerFile(workingDirectory, DEFAULT_STATUS_REPORT);
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
        return infrastructureIssues(autoRunTests());
    }

    public static List<PropertyIssue> reportInfrastructureIssues() {
        return infrastructureIssues(usesReportedCommandBatches());
    }

    private static List<PropertyIssue> infrastructureIssues(boolean enabled) {
        List<PropertyIssue> issues = new ArrayList<>();
        if (PARSED.modeIssue() != null) {
            issues.add(PARSED.modeIssue());
            return Collections.unmodifiableList(issues);
        }
        if (!enabled) {
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
        return parse(System::getProperty);
    }

    static ParsedProperties parse(Properties properties) {
        if (properties == null) {
            return parse(property -> null);
        }
        return parse(properties::getProperty);
    }

    private static ParsedProperties parse(PropertySource properties) {
        List<PropertyIssue> issues = new ArrayList<>();

        String rawMode = properties.getProperty(MODE_PROPERTY);
        ModeParseResult mode = parseMode(rawMode);
        if (mode.issue() != null) {
            issues.add(mode.issue());
        }

        String rawWorld = properties.getProperty(WORLD_PROPERTY);
        WorldPolicyParseResult world = parseWorldPolicy(rawWorld, defaultWorldPolicy(mode.mode()));
        if (world.issue() != null) {
            issues.add(world.issue());
        }

        String rawAutoRun = properties.getProperty(AUTO_RUN_PROPERTY);
        BooleanParseResult autoRun = parseStrictBoolean(AUTO_RUN_PROPERTY, rawAutoRun, defaultAutoRun(mode.mode()));
        if (autoRun.issue() != null) {
            issues.add(autoRun.issue());
        }

        String rawStopServer = properties.getProperty(STOP_SERVER_PROPERTY);
        BooleanParseResult stopServer = parseStrictBoolean(
            STOP_SERVER_PROPERTY,
            rawStopServer,
            defaultStopServer(mode.mode(), autoRun.value()));
        if (stopServer.issue() != null) {
            issues.add(stopServer.issue());
        }

        String rawGridOrigin = properties.getProperty(GRID_ORIGIN_PROPERTY);
        GridOriginParseResult gridOrigin = parseGridOrigin(rawGridOrigin);
        if (gridOrigin.issue() != null) {
            issues.add(gridOrigin.issue());
        }

        String rawTests = properties.getProperty(TESTS_PROPERTY);
        SelectorParseResult selectors = parseSelectors(rawTests);
        issues.addAll(selectors.issues());

        String rawAllowNoTests = properties.getProperty(ALLOW_NO_TESTS_PROPERTY);
        BooleanParseResult allowNoTests = parseStrictBoolean(ALLOW_NO_TESTS_PROPERTY, rawAllowNoTests, false);
        if (allowNoTests.issue() != null) {
            issues.add(allowNoTests.issue());
        }

        String reportFile = parsePathProperty(
            REPORT_FILE_PROPERTY,
            properties.getProperty(REPORT_FILE_PROPERTY),
            issues);
        String reportDir = parsePathProperty(REPORT_DIR_PROPERTY, properties.getProperty(REPORT_DIR_PROPERTY), issues);
        String statusFile = parsePathProperty(
            STATUS_FILE_PROPERTY,
            properties.getProperty(STATUS_FILE_PROPERTY),
            issues);

        String rawEvents = properties.getProperty(EVENTS_PROPERTY);
        EventsParseResult events = parseEvents(rawEvents);
        if (events.issue() != null) {
            issues.add(events.issue());
        }

        return new ParsedProperties(
            rawMode,
            mode.mode(),
            mode.issue(),
            rawWorld,
            world.policy(),
            rawAutoRun,
            autoRun.value(),
            rawStopServer,
            stopServer.value(),
            rawGridOrigin,
            gridOrigin.origin(),
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
            case "report" -> {
                return new ModeParseResult(Mode.REPORT, null);
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
                "Invalid -D" + MODE_PROPERTY + "=" + value + " (expected one of: off, interactive, report, ci)",
                true));
    }

    private static WorldPolicy defaultWorldPolicy(Mode mode) {
        return mode == Mode.REPORT || mode == Mode.CI ? WorldPolicy.VOID : WorldPolicy.NORMAL;
    }

    private static boolean defaultAutoRun(Mode mode) {
        return mode == Mode.CI;
    }

    private static boolean defaultStopServer(Mode mode, boolean autoRunTests) {
        return mode == Mode.CI && autoRunTests;
    }

    private static WorldPolicyParseResult parseWorldPolicy(String raw, WorldPolicy defaultPolicy) {
        if (raw == null) {
            return new WorldPolicyParseResult(defaultPolicy, null);
        }
        switch (raw) {
            case "void" -> {
                return new WorldPolicyParseResult(WorldPolicy.VOID, null);
            }
            case "normal" -> {
                return new WorldPolicyParseResult(WorldPolicy.NORMAL, null);
            }
        }
        return new WorldPolicyParseResult(
            defaultPolicy,
            configIssue(
                "config:" + WORLD_PROPERTY,
                WORLD_PROPERTY,
                "Invalid -D" + WORLD_PROPERTY + "=" + renderRawValue(raw) + " (expected void or normal)",
                true));
    }

    private static GridOriginParseResult parseGridOrigin(String raw) {
        if (raw == null) {
            return new GridOriginParseResult(DEFAULT_GRID_ORIGIN, null);
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return invalidGridOrigin(raw);
        }

        String[] parts = value.split(",", -1);
        if (parts.length != 3) {
            return invalidGridOrigin(raw);
        }

        Integer x = parseInteger(parts[0]);
        Integer y = parseInteger(parts[1]);
        Integer z = parseInteger(parts[2]);
        if (x == null || y == null || z == null || y < 0 || y > 255) {
            return invalidGridOrigin(raw);
        }

        return new GridOriginParseResult(new GridOrigin(x, y, z), null);
    }

    private static Integer parseInteger(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static GridOriginParseResult invalidGridOrigin(String raw) {
        return new GridOriginParseResult(
            DEFAULT_GRID_ORIGIN,
            configIssue(
                "config:" + GRID_ORIGIN_PROPERTY,
                GRID_ORIGIN_PROPERTY,
                "Invalid -D" + GRID_ORIGIN_PROPERTY + "=" + renderRawValue(raw)
                    + " (expected x,y,z with y between 0 and 255)",
                true));
    }

    static SelectorParseResult parseSelectors(String raw) {
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
            if (token.indexOf('*') >= 0) {
                issues.add(
                    invalidSelector(
                        "Invalid selector '" + token
                            + "': '*' is not supported; omit -D"
                            + TESTS_PROPERTY
                            + " or set it to an empty value to run all valid tests."));
                continue;
            }

            int colon = token.indexOf(':');
            if (colon < 0) {
                selectors.add(new TestSelector(SelectorType.NAMESPACE, token));
                continue;
            }

            if (colon == 0) {
                issues.add(invalidSelector("Invalid selector '" + token + "' (missing namespace before ':')"));
                continue;
            }
            if (colon == token.length() - 1) {
                issues.add(
                    invalidSelector(
                        "Invalid selector '" + token + "' (use namespace without ':' to select all tests)"));
                continue;
            }
            if (token.indexOf(':', colon + 1) >= 0) {
                issues.add(
                    invalidSelector(
                        "Invalid selector '" + token
                            + "' (expected one ':' in an exact test id, for example namespace:Class.method)"));
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
        String value = raw.trim();
        if (value.isEmpty()) {
            issues.add(
                configIssue(
                    "config:" + property,
                    property,
                    "Invalid -D" + property + "=" + renderRawValue(raw) + " (expected a file system path)",
                    true));
            return null;
        }
        return value;
    }

    static File resolveServerFile(File workingDirectory, String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            File base = workingDirectory == null ? new File(System.getProperty("user.dir", ".")) : workingDirectory;
            file = new File(base, path);
        }
        return file.toPath()
            .toAbsolutePath()
            .normalize()
            .toFile();
    }

    private static File resolveServerFile(String path) {
        return resolveServerFile(new File(System.getProperty("user.dir", ".")), path);
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
        return raw == null ? "<unset>"
            : raw.isEmpty() ? "<empty>"
                : raw.trim()
                    .isEmpty() ? "<blank>" : raw;
    }

    public enum Mode {
        OFF,
        INTERACTIVE,
        REPORT,
        CI
    }

    public enum WorldPolicy {
        VOID,
        NORMAL
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
    record ParsedProperties(String rawMode, Mode mode, PropertyIssue modeIssue, String rawWorld,
        WorldPolicy worldPolicy, String rawAutoRun, boolean autoRunTests, String rawStopServer,
        boolean stopServerAfterRun, String rawGridOrigin, GridOrigin gridOrigin, String rawTests,
        boolean selectsAllTests, List<TestSelector> testSelectors, String rawAllowNoTests, boolean allowNoTests,
        String reportFile, String reportDir, String statusFile, String rawEvents, boolean eventsEnabled,
        List<PropertyIssue> issues) {

    }

    @Desugar
    private record ModeParseResult(Mode mode, PropertyIssue issue) {

    }

    @Desugar
    record GridOrigin(int x, int y, int z) {

    }

    @Desugar
    private record WorldPolicyParseResult(WorldPolicy policy, PropertyIssue issue) {

    }

    @Desugar
    private record GridOriginParseResult(GridOrigin origin, PropertyIssue issue) {

    }

    @Desugar
    record SelectorParseResult(boolean selectsAll, List<TestSelector> selectors, List<PropertyIssue> issues) {

    }

    @Desugar
    private record BooleanParseResult(boolean value, PropertyIssue issue) {

    }

    @Desugar
    private record EventsParseResult(boolean enabled, PropertyIssue issue) {

    }

    private interface PropertySource {

        String getProperty(String property);
    }
}
