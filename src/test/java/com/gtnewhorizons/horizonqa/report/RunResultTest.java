package com.gtnewhorizons.horizonqa.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class RunResultTest {

    @Test
    public void snapshotsCasesIssuesAndCaseOutput() {
        List<String> output = new ArrayList<>();
        output.add("first");
        CaseResult resultCase = resultCase("mod:Suite.passes", CaseResult.Status.PASSED, true, output);
        List<CaseResult> cases = new ArrayList<>();
        cases.add(resultCase);
        List<IssueResult> issues = new ArrayList<>();
        issues.add(issue("selection:one", true));

        RunResult result = RunResult.completedCases("ci", cases, issues, "TEST.xml");

        output.add("mutated");
        cases.clear();
        issues.clear();

        assertEquals(
            1,
            result.cases()
                .size());
        assertEquals(
            Collections.singletonList("first"),
            result.cases()
                .get(0)
                .outputLines());
        assertEquals(
            1,
            result.issues()
                .size());
        assertThrows(
            UnsupportedOperationException.class,
            () -> result.cases()
                .add(resultCase));
        assertThrows(
            UnsupportedOperationException.class,
            () -> result.cases()
                .get(0)
                .outputLines()
                .add("blocked"));
    }

    @Test
    public void exitCodeUsesInfrastructureErrorWhenFatalIssueExists() {
        List<CaseResult> cases = Arrays.asList(
            resultCase("mod:Suite.required", CaseResult.Status.FAILED, true),
            resultCase("mod:Suite.optional", CaseResult.Status.FAILED, false));
        List<IssueResult> issues = Arrays.asList(issue("selection:fatal", true), issue("selection:warning", false));

        RunResult result = RunResult.completedCases("ci", cases, issues, "TEST.xml");

        assertEquals(2, result.exitCode());
        assertEquals(1, result.requiredFailures());
        assertEquals(1, result.optionalFailures());
        assertEquals(1, result.diagnosticErrors());
        assertEquals("error", result.status());
    }

    @Test
    public void runtimeOnlyRequiredFailuresUseSingleFailureExitCode() {
        RunResult result = RunResult.completedCases(
            "ci",
            Arrays.asList(
                resultCase("mod:Suite.requiredOne", CaseResult.Status.FAILED, true),
                resultCase("mod:Suite.requiredTwo", CaseResult.Status.TIMED_OUT, true)),
            Collections.emptyList(),
            "TEST.xml");

        assertEquals(1, result.exitCode());
        assertEquals("failed", result.status());
        assertEquals(2, result.requiredFailures());
    }

    @Test
    public void incompleteCasesUseInfrastructureExitCode() {
        RunResult result = RunResult.completedCases(
            "ci",
            Collections.singletonList(resultCase("mod:Suite.required", CaseResult.Status.RUNNING, true)),
            Collections.emptyList(),
            "TEST.xml");

        assertEquals(2, result.exitCode());
        assertEquals("error", result.status());
        assertEquals(0, result.requiredFailures());
    }

    @Test
    public void cleanupErrorsUseInfrastructureExitCodeWithoutRequiredFailure() {
        RunResult result = RunResult.completedCases(
            "ci",
            Collections.singletonList(resultCase("mod:Suite.cleanup", CaseResult.Status.ERROR, true)),
            Collections.emptyList(),
            "TEST.xml");

        assertEquals(2, result.exitCode());
        assertEquals("error", result.status());
        assertEquals(0, result.requiredFailures());
        assertEquals(1, result.infrastructureErrors());
        assertEquals(1, result.junitErrors());
        assertEquals(0, result.junitFailures());
    }

    @Test
    public void junitAggregateCountsUseCiSemantics() {
        RunResult result = RunResult.completedCases(
            "ci",
            Arrays.asList(
                resultCase("mod:Suite.requiredFailure", CaseResult.Status.FAILED, true),
                resultCase("mod:Suite.requiredTimeout", CaseResult.Status.TIMED_OUT, true),
                resultCase("mod:Suite.optionalFailure", CaseResult.Status.FAILED, false),
                resultCase("mod:Suite.optionalTimeout", CaseResult.Status.TIMED_OUT, false),
                resultCase("mod:Suite.cleanupError", CaseResult.Status.ERROR, true),
                resultCase("mod:Suite.setupBlocked", CaseResult.Status.NOT_STARTED, true),
                resultCase("mod:Suite.running", CaseResult.Status.RUNNING, true)),
            Collections.singletonList(issue("selection:fatal", true)),
            "TEST.xml");

        assertEquals(1, result.requiredFailed());
        assertEquals(1, result.requiredTimedOut());
        assertEquals(1, result.optionalFailed());
        assertEquals(1, result.optionalTimedOut());
        assertEquals(1, result.skippedBySetup());
        assertEquals(3, result.infrastructureErrors());
        assertEquals(2, result.junitFailures());
        assertEquals(3, result.junitErrors());
        assertEquals(3, result.junitSkipped());
    }

    private static CaseResult resultCase(String id, CaseResult.Status status, boolean required) {
        return resultCase(id, status, required, Collections.emptyList());
    }

    private static CaseResult resultCase(String id, CaseResult.Status status, boolean required, List<String> output) {
        return new CaseResult(
            id,
            "mod:Suite",
            id.substring(id.lastIndexOf('.') + 1),
            status,
            required,
            20,
            1.0,
            status == CaseResult.Status.PASSED ? "" : "broken",
            status == CaseResult.Status.ERROR ? CaseResult.CLEANUP_ERROR : "java.lang.AssertionError",
            "trace",
            output);
    }

    private static IssueResult issue(String id, boolean fatalInCi) {
        return new IssueResult(id, "SELECTION_ERROR", "horizonqa.selection", id, "message", "details", fatalInCi);
    }
}
