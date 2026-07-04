package com.gtnewhorizons.horizonqa.report;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;
import com.gtnewhorizons.horizonqa.api.event.TestEvent;
import com.gtnewhorizons.horizonqa.internal.GameTestDefinition;
import com.gtnewhorizons.horizonqa.internal.GameTestInstance;
import com.gtnewhorizons.horizonqa.internal.GameTestStatus;

@Desugar
public record CaseResult(String id, String classname, String name, Status status, boolean required, int tickCount,
    double timeSeconds, String failureMessage, String failureType, String failureTrace, List<String> outputLines,
    String blockedByIssueId) {

    public static final String CLEANUP_ERROR = "CLEANUP_ERROR";
    public static final String TEMPLATE_ERROR = "TEMPLATE_ERROR";
    private static final double TICKS_PER_SECOND = 20.0;

    public CaseResult {
        outputLines = immutableList(outputLines);
        blockedByIssueId = blockedByIssueId == null ? "" : blockedByIssueId;
    }

    public CaseResult(String id, String classname, String name, Status status, boolean required, int tickCount,
        double timeSeconds, String failureMessage, String failureType, String failureTrace, List<String> outputLines) {
        this(
            id,
            classname,
            name,
            status,
            required,
            tickCount,
            timeSeconds,
            failureMessage,
            failureType,
            failureTrace,
            outputLines,
            "");
    }

    public static CaseResult from(GameTestInstance inst) {
        String testId = inst.getDefinition()
            .getTestId();

        Throwable cause = failureCauseForReport(inst);
        String failureMessage = failureMessage(inst, cause);
        String failureType = failureType(inst, cause);
        String failureTrace = cause != null ? stackTrace(cause) : "";

        List<String> output = new ArrayList<>();
        for (TestEvent event : inst.getRecorder()
            .snapshot()) {
            output.add(formatEvent(event));
        }
        for (String warning : inst.getWarnings()) {
            output.add("WARNING: " + warning);
        }

        return new CaseResult(
            testId,
            classname(testId),
            name(testId),
            Status.from(inst.getStatus()),
            inst.getDefinition()
                .isRequired(),
            inst.getTickCount(),
            inst.getTickCount() / TICKS_PER_SECOND,
            failureMessage,
            failureType,
            failureTrace,
            output,
            "");
    }

    public static CaseResult skippedByIssue(GameTestDefinition definition, String blockedByIssueId, String message) {
        return skippedByIssue(definition, blockedByIssueId, message, "BATCH_HOOK_ERROR");
    }

    public static CaseResult skippedByIssue(GameTestDefinition definition, String blockedByIssueId, String message,
        String failureType) {
        String testId = definition.getTestId();
        String failureMessage = message == null || message.isEmpty() ? "Blocked by infrastructure issue" : message;
        return new CaseResult(
            testId,
            classname(testId),
            name(testId),
            Status.NOT_STARTED,
            definition.isRequired(),
            0,
            0.0,
            failureMessage,
            failureType == null || failureType.isEmpty() ? "INFRASTRUCTURE_ERROR" : failureType,
            "",
            Collections.emptyList(),
            blockedByIssueId);
    }

    public static CaseResult templateError(GameTestDefinition definition, String message, Throwable cause) {
        String testId = definition.getTestId();
        String failureMessage = message == null || message.isEmpty() ? "Template setup failed" : message;
        List<String> output = new ArrayList<>();
        output.add("template=" + definition.getTemplateName());
        output.add("error=" + failureMessage);
        return new CaseResult(
            testId,
            classname(testId),
            name(testId),
            Status.ERROR,
            definition.isRequired(),
            0,
            0.0,
            failureMessage,
            TEMPLATE_ERROR,
            cause != null ? stackTrace(cause) : "",
            output,
            "");
    }

    public boolean passed() {
        return status == Status.PASSED;
    }

    public boolean failed() {
        return status == Status.FAILED;
    }

    public boolean timedOut() {
        return status == Status.TIMED_OUT;
    }

    public boolean error() {
        return status == Status.ERROR;
    }

    public boolean incomplete() {
        return status == Status.NOT_STARTED || status == Status.RUNNING;
    }

    public boolean failedRequiredCase() {
        return required && (failed() || timedOut());
    }

    public boolean requiredFailed() {
        return required && failed();
    }

    public boolean requiredTimedOut() {
        return required && timedOut();
    }

    public boolean optionalFailed() {
        return !required && failed();
    }

    public boolean optionalTimedOut() {
        return !required && timedOut();
    }

    public boolean failedOptionalCase() {
        return optionalFailed() || optionalTimedOut();
    }

    public boolean skippedBySetup() {
        return status == Status.NOT_STARTED;
    }

    public boolean infrastructureError() {
        return status == Status.ERROR || status == Status.RUNNING;
    }

    public enum Status {

        NOT_STARTED,
        RUNNING,
        PASSED,
        FAILED,
        ERROR,
        TIMED_OUT;

        private static Status from(GameTestStatus status) {
            switch (status) {
                case PASSED:
                    return PASSED;
                case FAILED:
                    return FAILED;
                case ERROR:
                    return ERROR;
                case TIMED_OUT:
                    return TIMED_OUT;
                case RUNNING:
                    return RUNNING;
                case NOT_STARTED:
                default:
                    return NOT_STARTED;
            }
        }
    }

    private static String failureMessage(GameTestInstance inst, Throwable cause) {
        GameTestStatus status = inst.getStatus();
        if (status == GameTestStatus.ERROR) {
            return errorMessage(cause, "Cleanup callback failed");
        }
        if (status == GameTestStatus.FAILED) {
            return cause != null && cause.getMessage() != null ? cause.getMessage() : "Test failed";
        }
        if (status == GameTestStatus.TIMED_OUT) {
            return "Timed out after " + inst.getTickCount() + " ticks";
        }
        if (status != GameTestStatus.PASSED) {
            return "Test did not complete (status: " + status + ")";
        }
        return "";
    }

    private static String failureType(GameTestInstance inst, Throwable cause) {
        GameTestStatus status = inst.getStatus();
        if (status == GameTestStatus.ERROR) {
            if (cause instanceof GameTestInfrastructureException infrastructure) {
                return infrastructure.kind();
            }
            return CLEANUP_ERROR;
        }
        if (status == GameTestStatus.FAILED) {
            return cause != null ? cause.getClass()
                .getName() : "GameTestError";
        }
        if (status == GameTestStatus.TIMED_OUT) {
            return "GameTestTimeoutError";
        }
        if (status != GameTestStatus.PASSED) {
            return "GameTestError";
        }
        return "";
    }

    private static Throwable failureCauseForReport(GameTestInstance inst) {
        if (inst.getStatus() == GameTestStatus.ERROR) {
            return inst.getCleanupFailureCause() != null ? inst.getCleanupFailureCause() : inst.getFailureCause();
        }
        return inst.getFailureCause();
    }

    private static String errorMessage(Throwable cause, String fallback) {
        if (cause == null) {
            return fallback;
        }
        String message = cause.getMessage();
        if (message == null || message.isEmpty()) {
            return cause.getClass()
                .getName();
        }
        return message;
    }

    private static String formatEvent(TestEvent event) {
        return String.format("[t=%5d] [%-11s] %s", event.tick(), event.category(), event.summary());
    }

    private static String classname(String testId) {
        int sep = splitIndex(testId);
        return sep > 0 ? testId.substring(0, sep) : "horizonqa";
    }

    private static String name(String testId) {
        int sep = splitIndex(testId);
        return sep > 0 ? testId.substring(sep + 1) : testId;
    }

    private static int splitIndex(String testId) {
        if (testId == null) {
            return -1;
        }
        return Math.max(testId.lastIndexOf('.'), testId.lastIndexOf('#'));
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static <T> List<T> immutableList(List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
