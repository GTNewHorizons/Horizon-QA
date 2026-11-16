package com.gtnewhorizons.horizonqa.report;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.GameTestAssumptionException;
import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;
import com.gtnewhorizons.horizonqa.api.event.TestEvent;
import com.gtnewhorizons.horizonqa.internal.GameTestDefinition;
import com.gtnewhorizons.horizonqa.internal.GameTestInstance;
import com.gtnewhorizons.horizonqa.internal.GameTestStatus;

@Desugar
public record CaseResult(String id, String classname, String name, Status status, boolean required, int tickCount,
    double timeSeconds, String failureMessage, String failureType, String failureTrace, List<String> outputLines,
    String blockedByIssueId) {

    private static final String PARAMETERS_PREFIX = "parameters=";
    public static final String CLEANUP_ERROR = "CLEANUP_ERROR";
    public static final String TEMPLATE_ERROR = "TEMPLATE_ERROR";
    public static final String ASSUMPTION_FAILED = "ASSUMPTION_FAILED";
    public static final String MISSING_REQUIRED_MOD = "MISSING_REQUIRED_MOD";
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
        GameTestDefinition definition = inst.getDefinition();
        String testId = definition.getTestId();

        Throwable cause = failureCauseForReport(inst);
        String failureMessage = failureMessage(inst, cause);
        String failureType = failureType(inst, cause);
        String failureTrace = inst.getStatus() == GameTestStatus.SKIPPED ? ""
            : cause != null ? failureTrace(inst, cause) : "";

        List<String> output = new ArrayList<>();
        addParameterSummary(definition, output);
        for (TestEvent event : inst.getRecorder()
            .snapshot()) {
            output.add(formatEvent(event));
        }
        for (String warning : inst.getWarnings()) {
            output.add("WARNING: " + warning);
        }

        return new CaseResult(
            testId,
            definition.getReportClassName(),
            definition.getReportName(),
            Status.from(inst.getStatus()),
            definition.isRequired(),
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
            definition.getReportClassName(),
            definition.getReportName(),
            Status.NOT_STARTED,
            definition.isRequired(),
            0,
            0.0,
            failureMessage,
            failureType == null || failureType.isEmpty() ? "INFRASTRUCTURE_ERROR" : failureType,
            "",
            parameterOutput(definition),
            blockedByIssueId);
    }

    public static CaseResult skipped(GameTestDefinition definition, String reason, String skipType) {
        String testId = definition.getTestId();
        return new CaseResult(
            testId,
            definition.getReportClassName(),
            definition.getReportName(),
            Status.SKIPPED,
            definition.isRequired(),
            0,
            0.0,
            reason == null || reason.isEmpty() ? "Test was skipped" : reason,
            skipType == null || skipType.isEmpty() ? ASSUMPTION_FAILED : skipType,
            "",
            parameterOutput(definition),
            "");
    }

    public static CaseResult templateError(GameTestDefinition definition, String message, Throwable cause) {
        String testId = definition.getTestId();
        String failureMessage = message == null || message.isEmpty() ? "Template setup failed" : message;
        List<String> output = new ArrayList<>();
        addParameterSummary(definition, output);
        output.add("template=" + definition.getTemplateName());
        output.add("error=" + failureMessage);
        return new CaseResult(
            testId,
            definition.getReportClassName(),
            definition.getReportName(),
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

    /**
     * Supplied parameter summary when this result represents a parameterized case.
     *
     * <p>
     * The summary is also emitted as JUnit {@code system-out}; this accessor lets status JSON expose
     * the same input without copying all event output into that compact report.
     * </p>
     */
    public String parameterSummary() {
        for (String line : outputLines) {
            if (line.startsWith(PARAMETERS_PREFIX)) {
                return line.substring(PARAMETERS_PREFIX.length());
            }
        }
        return "";
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

    public boolean skipped() {
        return status == Status.SKIPPED;
    }

    public String skipReason() {
        return skipped() ? failureMessage : "";
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
        SKIPPED,
        PASSED,
        FAILED,
        ERROR,
        TIMED_OUT;

        private static Status from(GameTestStatus status) {
            switch (status) {
                case SKIPPED:
                    return SKIPPED;
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
        if (status == GameTestStatus.SKIPPED) {
            return cause != null && cause.getMessage() != null ? cause.getMessage()
                : "Runtime assumption was not satisfied";
        }
        if (status == GameTestStatus.ERROR) {
            return withFailureContext(inst, cause, errorMessage(cause, "Cleanup callback failed"));
        }
        if (status == GameTestStatus.FAILED) {
            String message = cause != null && cause.getMessage() != null ? cause.getMessage() : "Test failed";
            return withFailureContext(inst, cause, message);
        }
        if (status == GameTestStatus.TIMED_OUT) {
            return cause != null && cause.getMessage() != null ? cause.getMessage()
                : "Timed out after " + inst.getTickCount() + " ticks";
        }
        if (status != GameTestStatus.PASSED) {
            return "Test did not complete (status: " + status + ")";
        }
        return "";
    }

    private static String failureType(GameTestInstance inst, Throwable cause) {
        GameTestStatus status = inst.getStatus();
        if (status == GameTestStatus.SKIPPED) {
            return cause instanceof GameTestAssumptionException ? ASSUMPTION_FAILED : "TEST_SKIPPED";
        }
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
            if (inst.isExecutionAborted()) return inst.getFailureCause();
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

    private static String withFailureContext(GameTestInstance inst, Throwable cause, String message) {
        String context = inst.getFailureContext();
        if (context.isEmpty() || cause != inst.getFailureCause()) return message;
        return context + ": " + message;
    }

    private static String failureTrace(GameTestInstance inst, Throwable cause) {
        String trace = stackTrace(cause);
        String context = inst.getFailureContext();
        if (context.isEmpty() || cause != inst.getFailureCause()) return trace;
        return context + System.lineSeparator() + trace;
    }

    private static String formatEvent(TestEvent event) {
        return String.format("[t=%5d] [%-11s] %s", event.tick(), event.category(), event.summary());
    }

    private static List<String> parameterOutput(GameTestDefinition definition) {
        if (!definition.isParameterized()) {
            return Collections.emptyList();
        }
        List<String> output = new ArrayList<>(1);
        addParameterSummary(definition, output);
        return output;
    }

    private static void addParameterSummary(GameTestDefinition definition, List<String> output) {
        if (definition.isParameterized()) {
            output.add(PARAMETERS_PREFIX + definition.getArgumentSummary());
        }
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
