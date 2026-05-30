package com.gtnewhorizons.horizonqa.report;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.event.TestEvent;
import com.gtnewhorizons.horizonqa.internal.GameTestInstance;
import com.gtnewhorizons.horizonqa.internal.GameTestStatus;

@Desugar
public record CaseResult(String id, String classname, String name, Status status, boolean required, int tickCount,
    double timeSeconds, String failureMessage, String failureType, String failureTrace, List<String> outputLines) {

    private static final double TICKS_PER_SECOND = 20.0;

    public CaseResult {
        outputLines = immutableList(outputLines);
    }

    public static CaseResult from(GameTestInstance inst) {
        String testId = inst.getDefinition()
            .getTestId();
        int sep = Math.max(testId.lastIndexOf('.'), testId.lastIndexOf('#'));
        String classname = sep > 0 ? testId.substring(0, sep) : "horizonqa";
        String name = sep > 0 ? testId.substring(sep + 1) : testId;

        Throwable cause = inst.getFailureCause();
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
            classname,
            name,
            Status.from(inst.getStatus()),
            inst.getDefinition()
                .isRequired(),
            inst.getTickCount(),
            inst.getTickCount() / TICKS_PER_SECOND,
            failureMessage,
            failureType,
            failureTrace,
            output);
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
        return status == Status.RUNNING;
    }

    public enum Status {

        NOT_STARTED,
        RUNNING,
        PASSED,
        FAILED,
        TIMED_OUT;

        private static Status from(GameTestStatus status) {
            switch (status) {
                case PASSED:
                    return PASSED;
                case FAILED:
                    return FAILED;
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

    private static String formatEvent(TestEvent event) {
        return String.format("[t=%5d] [%-11s] %s", event.tick(), event.category(), event.summary());
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
