package com.gtnewhorizons.horizonqa.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.LogManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class TimingReportersTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void jsonSeparatesMeasuredWallTimeFromSimulationAndRetainsPendingSteps() {
        String json = StatusJsonReporter.toJson(result(), new File("reports/status.json"));

        assertTrue(json.contains("\"schemaVersion\": 4"));
        assertTrue(json.contains("\"wallTimeSeconds\": 7.5"));
        assertTrue(json.contains("\"timeSeconds\": 50.0"));
        assertTrue(json.contains("\"wallTimeSeconds\": 2.5"));
        assertTrue(json.contains("\"state\": \"partial\""));
        assertTrue(json.contains("\"wallTimeSeconds\": null"));
        assertTrue(json.contains("\"status\": \"PENDING\""));
        assertTrue(json.contains("\"status\": \"INTERRUPTED\""));
        assertTrue(json.contains("\"attempts\": 3"));
        assertTrue(json.contains("\"simulationTicks\": 60"));
        assertTrue(json.contains("\"requestedMultiplier\": 10"));
        assertTrue(json.contains("\"operation\": \"GENERAL\""));
        assertTrue(json.contains("\"executionSide\": \"UNKNOWN\""));
        assertTrue(json.contains("\"executionSide\": \"SERVER\""));
        assertTrue(json.contains("\"execution\": {"));
        assertTrue(json.contains("\"cleanup\": {"));
        assertTrue(json.contains("horizonqa-timing.html"));
    }

    @Test
    public void junitUsesMeasuredSuiteAndCaseIntervalsWithLocaleIndependentNumbers() throws Exception {
        File xml = temporaryFolder.newFile("timing.xml");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("pl-PL"));
            JUnitXmlReporter.write(result(), xml);
        } finally {
            Locale.setDefault(previous);
        }

        Document document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(xml);
        assertEquals(
            "7.500",
            document.getDocumentElement()
                .getAttribute("time"));
        Element first = (Element) document.getElementsByTagName("testcase")
            .item(0);
        assertEquals("2.500", first.getAttribute("time"));
        assertTrue(
            first.getTextContent()
                .contains("wallTimeState=partial"));
        Element second = (Element) document.getElementsByTagName("testcase")
            .item(1);
        assertEquals("0.000", second.getAttribute("time"));
        assertTrue(
            second.getTextContent()
                .contains("wallTimeState=unavailable"));
        assertTrue(
            document.getDocumentElement()
                .getTextContent()
                .contains("wallTimeState=unavailable"));
    }

    @Test
    public void htmlEscapesConsumerTextAndShowsUnmeasuredAndPartialIntervals() {
        String html = HtmlTimingReporter.toHtml(result());

        assertFalse(html.contains("<script>alert"));
        assertTrue(html.contains("&lt;script&gt;alert"));
        assertTrue(html.contains("7.500 s (complete)"));
        assertTrue(html.contains("2.500 s (partial)"));
        assertTrue(html.contains("unmeasured (unavailable)"));
        assertTrue(html.contains("INTERRUPTED"));
        assertTrue(html.contains("PENDING"));
        assertTrue(html.contains("FAILED"));
        assertTrue(html.contains("Execution"));
        assertTrue(html.contains("Cleanup"));
        assertTrue(html.contains("Operation"));
        assertTrue(html.contains("Execution side"));
        assertTrue(html.contains("UNKNOWN"));
        assertTrue(html.contains("not CPU time"));
        assertTrue(html.contains("bootstrap and setup measurements are unavailable"));
    }

    @Test
    public void consoleRanksByWallTimeRatherThanSimulationAndKeepsSuiteIndependent() {
        String lines = String.join("\n", ConsoleReporter.timingLines(result()));

        assertTrue(lines.contains("suite 7.500 s (complete)"));
        assertTrue(lines.contains("2.500 s (partial)"));
        assertTrue(lines.contains("not CPU"));
        assertTrue(lines.contains("[GENERAL, UNKNOWN]"));
        assertFalse(lines.contains("50.000"));
        assertFalse(lines.contains("test mod:Suite.unstarted"));
    }

    @Test
    public void htmlFailurePropagatesToStatusAndJunitWithoutSkippingThem() throws Exception {
        File status = new File(temporaryFolder.getRoot(), "status.json");
        File xml = new File(temporaryFolder.getRoot(), "TEST.xml");
        temporaryFolder.newFolder("horizonqa-timing.html");

        RunResult written = RunReportWriter.write(result(), xml, status, LogManager.getLogger());

        assertEquals(2, written.exitCode());
        assertTrue(
            written.issues()
                .stream()
                .anyMatch(issue -> "reporting:timing".equals(issue.id())));
        assertTrue(read(status).contains("reporting:timing"));
        assertTrue(read(xml).contains("REPORT_WRITE_ERROR"));
    }

    @Test
    public void reportWriterPlacesHtmlBesideStatusFile() throws Exception {
        File directory = temporaryFolder.newFolder("reports");
        File status = new File(directory, "custom-status.json");
        File xml = new File(temporaryFolder.getRoot(), "TEST.xml");

        RunReportWriter.write(result(), xml, status, LogManager.getLogger());

        assertTrue(new File(directory, "horizonqa-timing.html").isFile());
    }

    @Test
    public void timingSinkCannotOverwriteAnExplicitStatusTarget() throws Exception {
        File directory = temporaryFolder.newFolder("collision");
        File status = new File(directory, "horizonqa-timing.html");
        File xml = new File(directory, "TEST.xml");
        RunResult written = RunReportWriter.write(result(), xml, status, LogManager.getLogger());
        assertTrue(
            written.issues()
                .stream()
                .anyMatch(
                    issue -> issue.id()
                        .equals("reporting:timing")));
        assertTrue(read(status).contains("\"schemaVersion\": 4"));
    }

    private static RunResult result() {
        StepResult interrupted = new StepResult(
            1,
            "<script>alert('&')</script>",
            "WAIT_ASYNC",
            "END",
            "INTERRUPTED",
            3,
            60,
            10,
            new ElapsedTime(2, ElapsedTime.State.PARTIAL),
            "Fixture.java:7",
            "GENERAL",
            "UNKNOWN");
        StepResult pending = new StepResult(
            2,
            "not reached",
            "EXECUTE",
            "END",
            "PENDING",
            0,
            0,
            1,
            ElapsedTime.unavailable(),
            "Fixture.java:8",
            "GENERAL",
            "SERVER");
        CaseResult failed = new CaseResult(
            "mod:Suite.measured",
            "mod:Suite",
            "measured",
            CaseResult.Status.FAILED,
            true,
            1000,
            50.0,
            "<script>alert('failure')</script>",
            "java.lang.AssertionError",
            "",
            Collections.emptyList(),
            "",
            new CaseTiming(
                new ElapsedTime(2.5, ElapsedTime.State.PARTIAL),
                new ElapsedTime(2.0, ElapsedTime.State.COMPLETE),
                new ElapsedTime(0.5, ElapsedTime.State.PARTIAL)),
            Arrays.asList(interrupted, pending));
        CaseResult unstarted = new CaseResult(
            "mod:Suite.unstarted",
            "mod:Suite",
            "unstarted",
            CaseResult.Status.NOT_STARTED,
            true,
            0,
            0,
            "not reached",
            "",
            "",
            Collections.emptyList());
        return RunResult.completedCases("ci", Arrays.asList(failed, unstarted), Collections.emptyList(), "TEST.xml")
            .withElapsed(new ElapsedTime(7.5, ElapsedTime.State.COMPLETE));
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
