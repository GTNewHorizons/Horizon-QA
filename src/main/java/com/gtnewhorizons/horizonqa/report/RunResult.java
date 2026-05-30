package com.gtnewhorizons.horizonqa.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.internal.GameTestInstance;

@Desugar
public record RunResult(String mode, List<CaseResult> cases, List<IssueResult> issues, String junitReport,
    int exitCode) {

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
        int exitCode = cappedExitCode(requiredFailures(cases) + fatalIssues(issues));
        return new RunResult(mode, cases, issues, junitReport, exitCode);
    }

    public static RunResult preRun(String mode, List<IssueResult> issues, String junitReport, int exitCode) {
        return new RunResult(mode, Collections.emptyList(), issues, junitReport, exitCode);
    }

    public String status() {
        if (exitCode == 0) {
            return "passed";
        }
        if (diagnosticErrors() > 0 || cases.isEmpty()) {
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
        long count = 0;
        for (CaseResult result : cases) {
            if (result.failedOptionalCase()) count++;
        }
        return count;
    }

    public long requiredFailures() {
        return requiredFailures(cases);
    }

    public boolean passedRun() {
        return exitCode == 0;
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

    private static int cappedExitCode(long failures) {
        return (int) Math.min(failures, 127);
    }

    private static <T> List<T> immutableList(List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
