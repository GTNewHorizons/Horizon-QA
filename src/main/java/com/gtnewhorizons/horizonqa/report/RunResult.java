package com.gtnewhorizons.horizonqa.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.internal.GameTestInstance;

@Desugar
public record RunResult(String mode, List<CaseResult> cases, List<IssueResult> issues, String junitReport) {

    private static final int EXIT_PASSED = 0;
    private static final int EXIT_REQUIRED_TEST_FAILURE = 1;
    private static final int EXIT_INFRASTRUCTURE_ERROR = 2;

    public RunResult {
        cases = immutableList(cases);
        issues = immutableList(issues);
        mode = mode == null ? "" : mode;
        junitReport = junitReport == null ? "" : junitReport;
    }

    public static RunResult completed(String mode, List<GameTestInstance> instances, List<IssueResult> issues,
        String junitReport) {
        List<CaseResult> cases = new ArrayList<>();
        for (GameTestInstance instance : instances) {
            cases.add(CaseResult.from(instance));
        }
        return completedCases(mode, cases, issues, junitReport);
    }

    public static RunResult completedCases(String mode, List<CaseResult> cases, List<IssueResult> issues,
        String junitReport) {
        return new RunResult(mode, cases, issues, junitReport);
    }

    public static RunResult preRun(String mode, List<IssueResult> issues, String junitReport) {
        return new RunResult(mode, Collections.emptyList(), issues, junitReport);
    }

    public RunResult withAdditionalIssue(IssueResult issue) {
        if (issue == null) {
            return this;
        }
        List<IssueResult> updated = new ArrayList<>(issues);
        updated.add(issue);
        return new RunResult(mode, cases, updated, junitReport);
    }

    public int exitCode() {
        if (hasInfrastructureError(cases, issues)) {
            return EXIT_INFRASTRUCTURE_ERROR;
        }
        if (requiredFailures(cases) > 0) {
            return EXIT_REQUIRED_TEST_FAILURE;
        }
        return EXIT_PASSED;
    }

    public String status() {
        int exitCode = exitCode();
        if (exitCode == EXIT_PASSED) {
            return "passed";
        }
        if (exitCode == EXIT_INFRASTRUCTURE_ERROR) {
            return "error";
        }
        return "failed";
    }

    public int selectedTests() {
        return cases.size();
    }

    public long passed() {
        long count = 0;
        for (CaseResult result : cases) {
            if (result.passed()) count++;
        }
        return count;
    }

    public long failed() {
        long count = 0;
        for (CaseResult result : cases) {
            if (result.failed()) count++;
        }
        return count;
    }

    public long timedOut() {
        long count = 0;
        for (CaseResult result : cases) {
            if (result.timedOut()) count++;
        }
        return count;
    }

    public long incomplete() {
        long count = 0;
        for (CaseResult result : cases) {
            if (result.incomplete()) count++;
        }
        return count;
    }

    public long diagnosticErrors() {
        return fatalIssues(issues);
    }

    public long optionalFailures() {
        return optionalFailed() + optionalTimedOut();
    }

    public long requiredFailures() {
        return requiredFailed() + requiredTimedOut();
    }

    public long requiredFailed() {
        long count = 0;
        for (CaseResult result : cases) {
            if (result.requiredFailed()) count++;
        }
        return count;
    }

    public long requiredTimedOut() {
        long count = 0;
        for (CaseResult result : cases) {
            if (result.requiredTimedOut()) count++;
        }
        return count;
    }

    public long optionalFailed() {
        long count = 0;
        for (CaseResult result : cases) {
            if (result.optionalFailed()) count++;
        }
        return count;
    }

    public long optionalTimedOut() {
        long count = 0;
        for (CaseResult result : cases) {
            if (result.optionalTimedOut()) count++;
        }
        return count;
    }

    public long skippedBySetup() {
        long count = 0;
        for (CaseResult result : cases) {
            if (result.skippedBySetup()) count++;
        }
        return count;
    }

    public long infrastructureErrors() {
        long count = issues.size();
        for (CaseResult result : cases) {
            if (result.infrastructureError()) count++;
        }
        return count;
    }

    public long junitFailures() {
        return requiredFailed() + requiredTimedOut();
    }

    public long junitErrors() {
        return infrastructureErrors();
    }

    public long junitSkipped() {
        return optionalFailed() + optionalTimedOut() + skippedBySetup();
    }

    public boolean passedRun() {
        return exitCode() == EXIT_PASSED;
    }

    public double durationSeconds() {
        double max = 0.0;
        for (CaseResult result : cases) {
            max = Math.max(max, result.timeSeconds());
        }
        return max;
    }

    private static long requiredFailures(List<CaseResult> cases) {
        long count = 0;
        for (CaseResult result : cases) {
            if (result.failedRequiredCase()) count++;
        }
        return count;
    }

    private static boolean hasInfrastructureError(List<CaseResult> cases, List<IssueResult> issues) {
        if (fatalIssues(issues) > 0) {
            return true;
        }
        if (cases == null) {
            return false;
        }
        for (CaseResult result : cases) {
            if (result.infrastructureError() || result.incomplete()) {
                return true;
            }
        }
        return false;
    }

    private static long fatalIssues(List<IssueResult> issues) {
        long count = 0;
        if (issues == null) {
            return count;
        }
        for (IssueResult issue : issues) {
            if (issue.fatalInCi()) count++;
        }
        return count;
    }

    private static <T> List<T> immutableList(List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
