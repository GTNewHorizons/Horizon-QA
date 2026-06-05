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

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.internal.GameTestDefinition;

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
                resultCase("mod:Suite.cleanupError", CaseResult.Status.ERROR, true, "cleanup broke"),
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
        assertTrue(xml.contains("tests=\"8\" failures=\"2\" errors=\"3\" skipped=\"3\""));
        assertTrue(xml.contains("<failure message=\"required failure\""));
        assertTrue(xml.contains("<failure message=\"required timeout\""));
        assertTrue(xml.contains("<skipped message=\"optional failure\""));
        assertTrue(xml.contains("<skipped message=\"optional timeout\""));
        assertTrue(xml.contains("<error message=\"cleanup broke\" type=\"CLEANUP_ERROR\""));
        assertTrue(xml.contains("<skipped message=\"setup blocked\""));
        assertTrue(xml.contains("<error message=\"still running\""));
        assertTrue(xml.contains("<error message=\"missing selector\""));
    }

    @Test
    public void templateErrorCasesAreJUnitErrors() throws Exception {
        CaseResult resultCase = CaseResult.templateError(
            templateDefinition("mod:Suite.badTemplate", "mod:missing"),
            "Structure template resource not found: mod:missing",
            new IOException("missing template"));
        RunResult result = RunResult
            .completedCases("ci", Collections.singletonList(resultCase), Collections.emptyList(), "TEST.xml");

        assertEquals(CaseResult.Status.ERROR, resultCase.status());
        assertEquals(CaseResult.TEMPLATE_ERROR, resultCase.failureType());
        assertEquals(2, result.exitCode());
        assertEquals(1, result.junitErrors());
        assertEquals(0, result.junitFailures());

        File output = temporaryFolder.newFile("template-error.xml");
        JUnitXmlReporter.write(result, output);

        String xml = read(output);
        assertTrue(xml.contains("tests=\"1\" failures=\"0\" errors=\"1\" skipped=\"0\""));
        assertTrue(
            xml.contains(
                "<error message=\"Structure template resource not found: mod:missing\" type=\"TEMPLATE_ERROR\""));
        assertTrue(xml.contains("template=mod:missing"));
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
    public void skippedCasesCanReferenceBlockingInfrastructureIssue() throws Exception {
        CaseResult skipped = new CaseResult(
            "mod:Suite.blocked",
            "mod:Suite",
            "blocked",
            CaseResult.Status.NOT_STARTED,
            true,
            0,
            0.0,
            "before batch failed",
            "BATCH_HOOK_ERROR",
            "",
            Collections.emptyList(),
            "batchHook:before:default:mod.Suite#setup");
        RunResult result = RunResult.completedCases(
            "ci",
            Collections.singletonList(skipped),
            Collections.singletonList(
                new IssueResult(
                    "batchHook:before:default:mod.Suite#setup",
                    "BEFORE_BATCH_ERROR",
                    "horizonqa.infrastructure",
                    "batch-hook:before:default:mod.Suite#setup",
                    "before batch failed",
                    "issue.id=batchHook:before:default:mod.Suite#setup\n",
                    true)),
            "TEST.xml");

        File xmlOutput = temporaryFolder.newFile("blocked.xml");
        JUnitXmlReporter.write(result, xmlOutput);

        String xml = read(xmlOutput);
        assertTrue(xml.contains("tests=\"2\" failures=\"0\" errors=\"1\" skipped=\"1\""));
        assertTrue(xml.contains("<skipped message=\"before batch failed\" type=\"BATCH_HOOK_ERROR\"/>"));
        assertTrue(xml.contains("blockedByIssueId=batchHook:before:default:mod.Suite#setup"));

        File jsonOutput = temporaryFolder.newFile("blocked.json");
        StatusJsonReporter.write(result, jsonOutput);

        String json = read(jsonOutput);
        assertTrue(json.contains("\"blockedByIssueId\": \"batchHook:before:default:mod.Suite#setup\""));
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

    @Test
    public void atomicReportWriterCleansUpPartialTempFileWhenWriterThrows() throws Exception {
        Path target = new File(temporaryFolder.getRoot(), "TEST-writer-fails.xml").toPath();

        IOException error = assertThrows(IOException.class, () -> AtomicReportWriter.write(target, tempFile -> {
            Files.write(tempFile, "partial report".getBytes(StandardCharsets.UTF_8));
            throw new IOException("writer failed");
        },
            (source, destination, options) -> {
                throw new AssertionError("move should not run after writer failure");
            }));

        assertTrue(
            error.getMessage()
                .contains("writer failed"));
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
            failureType(status),
            "trace",
            Collections.emptyList());
    }

    private static String failureType(CaseResult.Status status) {
        if (status == CaseResult.Status.TIMED_OUT) {
            return "GameTestTimeoutError";
        }
        if (status == CaseResult.Status.ERROR) {
            return CaseResult.CLEANUP_ERROR;
        }
        return "java.lang.AssertionError";
    }

    private static GameTestDefinition templateDefinition(String id, String template) throws Exception {
        return new GameTestDefinition(
            id,
            TemplateDefinitions.class.getMethod("test", GameTestHelper.class),
            template,
            100,
            "",
            true,
            0);
    }

    public static final class TemplateDefinitions {

        public static void test(GameTestHelper helper) {}
    }
}
