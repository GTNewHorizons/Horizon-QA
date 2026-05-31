package com.gtnewhorizons.horizonqa.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReporterOutputTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void junitXmlUsesRunResultForCasesAndIssues() throws Exception {
        CaseResult failed = new CaseResult(
            "mod:Suite.fails",
            "mod:Suite",
            "fails",
            CaseResult.Status.FAILED,
            true,
            10,
            0.5,
            "bad <message>",
            "java.lang.AssertionError",
            "trace & detail",
            Collections.singletonList("event <line>"));
        IssueResult issue = new IssueResult(
            "selection:missing",
            "UNMATCHED_SELECTOR",
            "horizonqa.selection",
            "selector:missing",
            "Missing & invalid\u0001",
            "issue.id=selection:missing\nselector=missing\n",
            true);
        RunResult result = RunResult
            .completedCases("ci", Collections.singletonList(failed), Collections.singletonList(issue), "TEST.xml");

        File output = temporaryFolder.newFile("TEST-horizonqa.xml");
        JUnitXmlReporter.write(result, output);

        String xml = read(output);
        assertTrue(xml.contains("tests=\"2\" failures=\"1\" errors=\"1\" skipped=\"0\""));
        assertTrue(xml.contains("message=\"bad &lt;message&gt;\""));
        assertTrue(xml.contains("trace &amp; detail"));
        assertTrue(xml.contains("event &lt;line&gt;"));
        assertTrue(xml.contains("classname=\"horizonqa.selection\""));
        assertFalse(xml.contains("\u0001"));
    }

    @Test
    public void junitXmlUsesCiSemanticsForFailuresErrorsAndSkipped() throws Exception {
        RunResult result = RunResult.completedCases(
            "ci",
            Arrays.asList(
                resultCase("mod:Suite.requiredFailure", CaseResult.Status.FAILED, true, "required failure"),
                resultCase("mod:Suite.requiredTimeout", CaseResult.Status.TIMED_OUT, true, "required timeout"),
                resultCase("mod:Suite.optionalFailure", CaseResult.Status.FAILED, false, "optional failure"),
                resultCase("mod:Suite.optionalTimeout", CaseResult.Status.TIMED_OUT, false, "optional timeout"),
                resultCase("mod:Suite.setupBlocked", CaseResult.Status.NOT_STARTED, true, "setup blocked"),
                resultCase("mod:Suite.running", CaseResult.Status.RUNNING, true, "still running")),
            Collections.singletonList(
                new IssueResult(
                    "selection:missing",
                    "UNMATCHED_SELECTOR",
                    "horizonqa.selection",
                    "selector:missing",
                    "missing selector",
                    "",
                    true)),
            "TEST.xml");

        File output = temporaryFolder.newFile("TEST-ci-semantics.xml");
        JUnitXmlReporter.write(result, output);

        String xml = read(output);
        assertTrue(xml.contains("tests=\"7\" failures=\"2\" errors=\"2\" skipped=\"3\""));
        assertTrue(xml.contains("<failure message=\"required failure\""));
        assertTrue(xml.contains("<failure message=\"required timeout\""));
        assertTrue(xml.contains("<skipped message=\"optional failure\""));
        assertTrue(xml.contains("<skipped message=\"optional timeout\""));
        assertTrue(xml.contains("<skipped message=\"setup blocked\""));
        assertTrue(xml.contains("<error message=\"still running\""));
        assertTrue(xml.contains("<error message=\"missing selector\""));
    }

    @Test
    public void statusJsonEscapesStringsAndReportsCounts() throws Exception {
        RunResult result = RunResult.preRun(
            "ci",
            Collections
                .singletonList(IssueResult.reporting("junit", "TEST.xml", new IOException("disk \"full\" \u0001"))),
            "TEST-alpha.xml\n");

        File output = temporaryFolder.newFile("horizonqa-result.json");
        StatusJsonReporter.write(result, output);

        String json = read(output);
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"status\": \"error\""));
        assertTrue(json.contains("\"exitCode\": 2"));
        assertTrue(json.contains("\"configuration\": {"));
        assertTrue(json.contains("\"counts\": {"));
        assertTrue(json.contains("\"selectedTests\": 0"));
        assertTrue(json.contains("\"diagnosticErrors\": 1"));
        assertTrue(json.contains("\"reports\": {"));
        assertTrue(json.contains("\"junit\": \"TEST-alpha.xml\\n\""));
        assertTrue(json.contains("\"status\": \""));
        assertTrue(json.contains("\"issues\": ["));
        assertTrue(json.contains("\"kind\": \"REPORT_WRITE_ERROR\""));
        assertTrue(json.contains("disk \\\"full\\\" \\u0001"));
        assertTrue(json.contains("\"stackTrace\": \"java.io.IOException: disk \\\"full\\\" \\u0001"));
        assertTrue(json.contains("\"tests\": []"));
    }

    @Test
    public void statusJsonIncludesTestsWithoutEventLogs() throws Exception {
        RunResult result = RunResult.completedCases(
            "ci",
            Collections.singletonList(
                new CaseResult(
                    "mod:Suite.fails",
                    "mod:Suite",
                    "fails",
                    CaseResult.Status.FAILED,
                    true,
                    10,
                    0.5,
                    "bad",
                    "java.lang.AssertionError",
                    "trace line",
                    Collections.singletonList("event line that belongs only in JUnit"))),
            Collections.singletonList(
                new IssueResult(
                    "config:bad",
                    "CONFIG_ERROR",
                    "horizonqa.configuration",
                    "config:horizonqa.tests",
                    "bad",
                    "",
                    true)),
            "TEST.xml");

        File output = temporaryFolder.newFile("cases.json");
        StatusJsonReporter.write(result, output);

        String json = read(output);
        assertTrue(json.contains("\"status\": \"error\""));
        assertTrue(json.contains("\"exitCode\": 2"));
        assertTrue(json.contains("\"selectedTests\": 1"));
        assertTrue(json.contains("\"tests\": ["));
        assertTrue(json.contains("\"id\": \"mod:Suite.fails\""));
        assertTrue(json.contains("\"status\": \"failed\""));
        assertTrue(json.contains("\"failure\": {"));
        assertTrue(json.contains("\"stackTrace\": \"trace line\""));
        assertTrue(json.contains("\"diagnosticErrors\": 1"));
        assertFalse(json.contains("event line that belongs only in JUnit"));
    }

    @Test
    public void statusJsonReportsPassedOptionalFailureRunsAsPassed() throws Exception {
        RunResult result = RunResult.completedCases(
            "ci",
            Arrays.asList(
                new CaseResult(
                    "mod:Suite.optional",
                    "mod:Suite",
                    "optional",
                    CaseResult.Status.FAILED,
                    false,
                    1,
                    0.05,
                    "optional failure",
                    "java.lang.AssertionError",
                    "",
                    Collections.emptyList())),
            Collections.emptyList(),
            "TEST.xml");

        File output = temporaryFolder.newFile("optional.json");
        StatusJsonReporter.write(result, output);

        String json = read(output);
        assertTrue(json.contains("\"status\": \"passed\""));
        assertTrue(json.contains("\"exitCode\": 0"));
        assertTrue(json.contains("\"optionalFailures\": 1"));
    }

    @Test
    public void atomicReportWriterFallsBackOnlyWhenAtomicMoveIsUnsupported() throws Exception {
        Path target = new File(temporaryFolder.getRoot(), "TEST-horizonqa.xml").toPath();
        AtomicInteger moveCount = new AtomicInteger();
        AtomicReference<Path> tempPath = new AtomicReference<>();

        AtomicReportWriter.write(target, tempFile -> {
            tempPath.set(tempFile);
            Files.write(tempFile, "report".getBytes(StandardCharsets.UTF_8));
        }, (source, destination, options) -> {
            moveCount.incrementAndGet();
            if (hasOption(options, StandardCopyOption.ATOMIC_MOVE)) {
                throw new AtomicMoveNotSupportedException(source.toString(), destination.toString(), "unsupported");
            }
            Files.move(source, destination, options);
        });

        assertTrue(Files.exists(target));
        assertTrue(read(target.toFile()).contains("report"));
        assertEquals(2, moveCount.get());
        assertEquals(
            target.toAbsolutePath()
                .getParent(),
            tempPath.get()
                .getParent());
        String tempName = tempPath.get()
            .getFileName()
            .toString();
        assertTrue(tempName.startsWith("TEST-horizonqa.xml."));
        assertTrue(tempName.endsWith(".tmp"));
        assertNoTempReports(target);
    }

    @Test
    public void atomicReportWriterPropagatesOtherMoveIoExceptions() throws Exception {
        Path target = new File(temporaryFolder.getRoot(), "horizonqa-result.json").toPath();
        AtomicInteger moveCount = new AtomicInteger();

        IOException error = assertThrows(
            IOException.class,
            () -> AtomicReportWriter.write(
                target,
                tempFile -> Files.write(tempFile, "report".getBytes(StandardCharsets.UTF_8)),
                (source, destination, options) -> {
                    moveCount.incrementAndGet();
                    throw new IOException("move failed");
                }));

        assertTrue(
            error.getMessage()
                .contains("move failed"));
        assertEquals(1, moveCount.get());
        assertFalse(Files.exists(target));
        assertNoTempReports(target);
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static boolean hasOption(CopyOption[] options, CopyOption option) {
        for (CopyOption candidate : options) {
            if (candidate == option) {
                return true;
            }
        }
        return false;
    }

    private static void assertNoTempReports(Path target) throws IOException {
        Path parent = target.toAbsolutePath()
            .getParent();
        String prefix = target.getFileName()
            .toString() + ".";
        try (java.util.stream.Stream<Path> files = Files.list(parent)) {
            assertFalse(files.anyMatch(path -> {
                String name = path.getFileName()
                    .toString();
                return name.startsWith(prefix) && name.endsWith(".tmp");
            }));
        }
    }

    private static CaseResult resultCase(String id, CaseResult.Status status, boolean required, String message) {
        return new CaseResult(
            id,
            "mod:Suite",
            id.substring(id.lastIndexOf('.') + 1),
            status,
            required,
            20,
            1.0,
            message,
            status == CaseResult.Status.TIMED_OUT ? "GameTestTimeoutError" : "java.lang.AssertionError",
            "trace",
            Collections.emptyList());
    }
}
