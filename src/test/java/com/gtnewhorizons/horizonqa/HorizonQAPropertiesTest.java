package com.gtnewhorizons.horizonqa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.gtnewhorizons.horizonqa.HorizonQAProperties.PropertyIssue;
import com.gtnewhorizons.horizonqa.HorizonQAProperties.SelectorParseResult;
import com.gtnewhorizons.horizonqa.HorizonQAProperties.SelectorType;
import com.gtnewhorizons.horizonqa.HorizonQAProperties.TestSelector;

public class HorizonQAPropertiesTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void defaultParsingUsesInteractiveAllTestsAndNoIssues() {
        HorizonQAProperties.ParsedProperties parsed = HorizonQAProperties.parse(new Properties());

        assertEquals(HorizonQAProperties.Mode.INTERACTIVE, parsed.mode());
        assertNull(parsed.rawMode());
        assertEquals(HorizonQAProperties.WorldPolicy.NORMAL, parsed.worldPolicy());
        assertNull(parsed.rawWorld());
        assertFalse(parsed.autoRunTests());
        assertNull(parsed.rawAutoRun());
        assertFalse(parsed.stopServerAfterRun());
        assertNull(parsed.rawStopServer());
        assertEquals(new HorizonQAProperties.GridOrigin(0, 64, 0), parsed.gridOrigin());
        assertNull(parsed.rawGridOrigin());
        assertTrue(parsed.selectsAllTests());
        assertTrue(
            parsed.testSelectors()
                .isEmpty());
        assertFalse(parsed.allowNoTests());
        assertTrue(parsed.eventsEnabled());
        assertNull(parsed.reportFile());
        assertNull(parsed.reportDir());
        assertNull(parsed.statusFile());
        assertTrue(
            parsed.issues()
                .isEmpty());
    }

    @Test
    public void reportModeIsRejected() {
        Properties properties = new Properties();
        properties.setProperty(HorizonQAProperties.MODE_PROPERTY, "report");

        HorizonQAProperties.ParsedProperties parsed = HorizonQAProperties.parse(properties);

        assertEquals(HorizonQAProperties.Mode.OFF, parsed.mode());
        assertEquals("report", parsed.rawMode());
        assertEquals(HorizonQAProperties.WorldPolicy.NORMAL, parsed.worldPolicy());
        assertFalse(parsed.autoRunTests());
        assertFalse(parsed.stopServerAfterRun());
        assertConfigIssue(parsed, HorizonQAProperties.MODE_PROPERTY);
    }

    @Test
    public void ciModeDefaultsToVoidWorldAutorunAndShutdown() {
        Properties properties = new Properties();
        properties.setProperty(HorizonQAProperties.MODE_PROPERTY, "ci");

        HorizonQAProperties.ParsedProperties parsed = HorizonQAProperties.parse(properties);

        assertEquals(HorizonQAProperties.Mode.CI, parsed.mode());
        assertEquals(HorizonQAProperties.WorldPolicy.VOID, parsed.worldPolicy());
        assertTrue(parsed.autoRunTests());
        assertTrue(parsed.stopServerAfterRun());
        assertTrue(
            parsed.issues()
                .isEmpty());
    }

    @Test
    public void ciModeCanBeResolvedAsManualReportedKeepAlivePreset() {
        Properties properties = new Properties();
        properties.setProperty(HorizonQAProperties.MODE_PROPERTY, "ci");
        properties.setProperty(HorizonQAProperties.AUTO_RUN_PROPERTY, "false");

        HorizonQAProperties.ParsedProperties parsed = HorizonQAProperties.parse(properties);

        assertEquals(HorizonQAProperties.Mode.CI, parsed.mode());
        assertEquals(HorizonQAProperties.WorldPolicy.VOID, parsed.worldPolicy());
        assertFalse(parsed.autoRunTests());
        assertFalse(parsed.stopServerAfterRun());
        assertTrue(
            parsed.issues()
                .isEmpty());
    }

    @Test
    public void manualReportedInfrastructureIgnoresAutomaticOnlyProperties() {
        Properties properties = new Properties();
        properties.setProperty(HorizonQAProperties.MODE_PROPERTY, "ci");
        properties.setProperty(HorizonQAProperties.AUTO_RUN_PROPERTY, "false");
        properties.setProperty(HorizonQAProperties.TESTS_PROPERTY, "moda,,too:many:colons");
        properties.setProperty(HorizonQAProperties.ALLOW_NO_TESTS_PROPERTY, "yes");
        HorizonQAProperties.ParsedProperties parsed = HorizonQAProperties.parse(properties);

        List<PropertyIssue> issues = HorizonQAProperties.reportInfrastructureIssues(parsed);

        assertTrue(issues.isEmpty());
    }

    @Test
    public void manualReportedInfrastructureKeepsSharedConfigIssues() {
        Properties properties = new Properties();
        properties.setProperty(HorizonQAProperties.MODE_PROPERTY, "ci");
        properties.setProperty(HorizonQAProperties.AUTO_RUN_PROPERTY, "false");
        properties.setProperty(HorizonQAProperties.TESTS_PROPERTY, "too:many:colons");
        properties.setProperty(HorizonQAProperties.GRID_ORIGIN_PROPERTY, "0,256,0");
        HorizonQAProperties.ParsedProperties parsed = HorizonQAProperties.parse(properties);

        List<PropertyIssue> issues = HorizonQAProperties.reportInfrastructureIssues(parsed);

        assertEquals(1, issues.size());
        assertEquals(HorizonQAProperties.GRID_ORIGIN_PROPERTY, issues.get(0)
            .property());
    }

    @Test
    public void propertyParsingTrimsPathsAndKeepsRawConfigurationValues() {
        Properties properties = new Properties();
        properties.setProperty(HorizonQAProperties.MODE_PROPERTY, "ci");
        properties.setProperty(HorizonQAProperties.WORLD_PROPERTY, "normal");
        properties.setProperty(HorizonQAProperties.AUTO_RUN_PROPERTY, "false");
        properties.setProperty(HorizonQAProperties.STOP_SERVER_PROPERTY, "true");
        properties.setProperty(HorizonQAProperties.GRID_ORIGIN_PROPERTY, " 16,128,-32 ");
        properties.setProperty(HorizonQAProperties.TESTS_PROPERTY, " moda , modb:Suite.test ");
        properties.setProperty(HorizonQAProperties.ALLOW_NO_TESTS_PROPERTY, "true");
        properties.setProperty(HorizonQAProperties.REPORT_FILE_PROPERTY, " reports/TEST-custom.xml ");
        properties.setProperty(HorizonQAProperties.REPORT_DIR_PROPERTY, " reports ");
        properties.setProperty(HorizonQAProperties.STATUS_FILE_PROPERTY, " status/result.json ");
        properties.setProperty(HorizonQAProperties.EVENTS_PROPERTY, "off");

        HorizonQAProperties.ParsedProperties parsed = HorizonQAProperties.parse(properties);

        assertEquals(HorizonQAProperties.Mode.CI, parsed.mode());
        assertEquals("ci", parsed.rawMode());
        assertEquals("normal", parsed.rawWorld());
        assertEquals(HorizonQAProperties.WorldPolicy.NORMAL, parsed.worldPolicy());
        assertEquals("false", parsed.rawAutoRun());
        assertFalse(parsed.autoRunTests());
        assertEquals("true", parsed.rawStopServer());
        assertTrue(parsed.stopServerAfterRun());
        assertEquals(" 16,128,-32 ", parsed.rawGridOrigin());
        assertEquals(new HorizonQAProperties.GridOrigin(16, 128, -32), parsed.gridOrigin());
        assertEquals(" moda , modb:Suite.test ", parsed.rawTests());
        assertFalse(parsed.selectsAllTests());
        assertEquals(
            2,
            parsed.testSelectors()
                .size());
        assertEquals(
            new TestSelector(SelectorType.NAMESPACE, "moda"),
            parsed.testSelectors()
                .get(0));
        assertEquals(
            new TestSelector(SelectorType.EXACT_TEST_ID, "modb:Suite.test"),
            parsed.testSelectors()
                .get(1));
        assertEquals("true", parsed.rawAllowNoTests());
        assertTrue(parsed.allowNoTests());
        assertEquals("off", parsed.rawEvents());
        assertFalse(parsed.eventsEnabled());
        assertEquals("reports/TEST-custom.xml", parsed.reportFile());
        assertEquals("reports", parsed.reportDir());
        assertEquals("status/result.json", parsed.statusFile());
        assertTrue(
            parsed.issues()
                .isEmpty());
    }

    @Test
    public void strictPropertyParsingReportsFatalConfigIssues() {
        Properties properties = new Properties();
        properties.setProperty(HorizonQAProperties.MODE_PROPERTY, "true");
        properties.setProperty(HorizonQAProperties.WORLD_PROPERTY, "flat");
        properties.setProperty(HorizonQAProperties.AUTO_RUN_PROPERTY, "yes");
        properties.setProperty(HorizonQAProperties.STOP_SERVER_PROPERTY, "no");
        properties.setProperty(HorizonQAProperties.GRID_ORIGIN_PROPERTY, "0,256,0");
        properties.setProperty(HorizonQAProperties.ALLOW_NO_TESTS_PROPERTY, "yes");
        properties.setProperty(HorizonQAProperties.REPORT_FILE_PROPERTY, "   ");
        properties.setProperty(HorizonQAProperties.EVENTS_PROPERTY, "false");

        HorizonQAProperties.ParsedProperties parsed = HorizonQAProperties.parse(properties);

        assertEquals(HorizonQAProperties.Mode.OFF, parsed.mode());
        assertEquals(HorizonQAProperties.WorldPolicy.NORMAL, parsed.worldPolicy());
        assertFalse(parsed.autoRunTests());
        assertFalse(parsed.stopServerAfterRun());
        assertEquals(new HorizonQAProperties.GridOrigin(0, 64, 0), parsed.gridOrigin());
        assertFalse(parsed.allowNoTests());
        assertNull(parsed.reportFile());
        assertTrue(parsed.eventsEnabled());
        assertConfigIssue(parsed, HorizonQAProperties.MODE_PROPERTY);
        assertConfigIssue(parsed, HorizonQAProperties.WORLD_PROPERTY);
        assertConfigIssue(parsed, HorizonQAProperties.AUTO_RUN_PROPERTY);
        assertConfigIssue(parsed, HorizonQAProperties.STOP_SERVER_PROPERTY);
        assertConfigIssue(parsed, HorizonQAProperties.GRID_ORIGIN_PROPERTY);
        assertConfigIssue(parsed, HorizonQAProperties.ALLOW_NO_TESTS_PROPERTY);
        assertConfigIssue(parsed, HorizonQAProperties.REPORT_FILE_PROPERTY);
        assertConfigIssue(parsed, HorizonQAProperties.EVENTS_PROPERTY);
        assertEquals(
            8,
            parsed.issues()
                .size());
    }

    @Test
    public void selectorParsingAcceptsNamespacesAndExactTestIds() {
        SelectorParseResult parsed = HorizonQAProperties.parseSelectors(" moda , modb:Suite.test , mod-c_1 ");

        assertFalse(parsed.selectsAll());
        assertTrue(
            parsed.issues()
                .isEmpty());
        assertEquals(
            3,
            parsed.selectors()
                .size());
        assertEquals(
            new TestSelector(SelectorType.NAMESPACE, "moda"),
            parsed.selectors()
                .get(0));
        assertEquals(
            new TestSelector(SelectorType.EXACT_TEST_ID, "modb:Suite.test"),
            parsed.selectors()
                .get(1));
        assertEquals(
            new TestSelector(SelectorType.NAMESPACE, "mod-c_1"),
            parsed.selectors()
                .get(2));
    }

    @Test
    public void selectorParsingReportsEveryInvalidToken() {
        SelectorParseResult parsed = HorizonQAProperties.parseSelectors("moda,,*:bad,:missing,mod:,too:many:colons");

        assertFalse(parsed.selectsAll());
        assertEquals(
            1,
            parsed.selectors()
                .size());
        assertEquals(
            new TestSelector(SelectorType.NAMESPACE, "moda"),
            parsed.selectors()
                .get(0));
        assertEquals(
            5,
            parsed.issues()
                .size());
        assertSelectorIssue(
            parsed.issues()
                .get(0),
            "empty selector token at position 2");
        assertSelectorIssue(
            parsed.issues()
                .get(1),
            "'*' is not supported");
        assertSelectorIssue(
            parsed.issues()
                .get(2),
            "missing namespace before ':'");
        assertSelectorIssue(
            parsed.issues()
                .get(3),
            "use namespace without ':'");
        assertSelectorIssue(
            parsed.issues()
                .get(4),
            "expected one ':'");
    }

    @Test
    public void resolvesRelativePathsAgainstServerWorkingDirectory() {
        File resolved = HorizonQAProperties.resolveServerFile(temporaryFolder.getRoot(), "reports/TEST-horizonqa.xml");

        Path expected = new File(temporaryFolder.getRoot(), "reports/TEST-horizonqa.xml").toPath()
            .toAbsolutePath()
            .normalize();
        assertEquals(expected, resolved.toPath());
    }

    @Test
    public void leavesAbsolutePathsAnchoredAtTheirOriginalRoot() throws Exception {
        File absolute = temporaryFolder.newFile("custom.xml")
            .getAbsoluteFile();

        File resolved = HorizonQAProperties.resolveServerFile(temporaryFolder.newFolder("server"), absolute.getPath());

        assertEquals(
            absolute.toPath()
                .toAbsolutePath()
                .normalize(),
            resolved.toPath());
    }

    @Test
    public void reportDirResolvesDefaultReportFilenames() {
        Properties properties = new Properties();
        properties.setProperty(HorizonQAProperties.REPORT_DIR_PROPERTY, "reports/ci");
        HorizonQAProperties.ParsedProperties parsed = HorizonQAProperties.parse(properties);

        assertEquals(
            expectedPath("reports/ci/TEST-horizonqa.xml"),
            HorizonQAProperties.junitReportFile(parsed, temporaryFolder.getRoot())
                .toPath());
        assertEquals(
            expectedPath("reports/ci/horizonqa-result.json"),
            HorizonQAProperties.statusReportFile(parsed, temporaryFolder.getRoot())
                .toPath());
    }

    @Test
    public void explicitReportFilesTakePrecedenceOverReportDir() {
        Properties properties = new Properties();
        properties.setProperty(HorizonQAProperties.REPORT_FILE_PROPERTY, "custom/TEST.xml");
        properties.setProperty(HorizonQAProperties.REPORT_DIR_PROPERTY, "reports/ci");
        properties.setProperty(HorizonQAProperties.STATUS_FILE_PROPERTY, "custom/status.json");
        HorizonQAProperties.ParsedProperties parsed = HorizonQAProperties.parse(properties);

        assertEquals(
            expectedPath("custom/TEST.xml"),
            HorizonQAProperties.junitReportFile(parsed, temporaryFolder.getRoot())
                .toPath());
        assertEquals(
            expectedPath("custom/status.json"),
            HorizonQAProperties.statusReportFile(parsed, temporaryFolder.getRoot())
                .toPath());
    }

    private void assertConfigIssue(HorizonQAProperties.ParsedProperties parsed, String property) {
        PropertyIssue issue = issueForProperty(parsed, property);
        assertEquals("config:" + property, issue.id());
        assertEquals("CONFIG_ERROR", issue.kind());
        assertTrue(issue.fatalInCi());
    }

    private static void assertSelectorIssue(PropertyIssue issue, String expectedMessagePart) {
        assertEquals("selector:invalid", issue.id());
        assertEquals("INVALID_SELECTOR", issue.kind());
        assertEquals(HorizonQAProperties.TESTS_PROPERTY, issue.property());
        assertTrue(issue.fatalInCi());
        assertTrue(
            issue.message()
                .contains(expectedMessagePart));
    }

    private static PropertyIssue issueForProperty(HorizonQAProperties.ParsedProperties parsed, String property) {
        for (PropertyIssue issue : parsed.issues()) {
            if (property.equals(issue.property())) {
                return issue;
            }
        }
        throw new AssertionError("Missing issue for property " + property);
    }

    private Path expectedPath(String path) {
        return new File(temporaryFolder.getRoot(), path).toPath()
            .toAbsolutePath()
            .normalize();
    }
}
