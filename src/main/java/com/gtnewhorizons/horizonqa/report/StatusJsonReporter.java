package com.gtnewhorizons.horizonqa.report;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

import org.apache.commons.lang3.text.translate.JavaUnicodeEscaper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;

public final class StatusJsonReporter {

    private static final int SCHEMA_VERSION = 5;
    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();
    private static final JavaUnicodeEscaper ASCII = JavaUnicodeEscaper.outsideOf(0, 0x7e);

    private StatusJsonReporter() {}

    public static void write(RunResult result, File outputFile) throws IOException {
        AtomicReportWriter.write(
            outputFile,
            tempFile -> Files.write(tempFile, toJson(result, outputFile).getBytes(StandardCharsets.UTF_8)));
    }

    static String toJson(RunResult result, File outputFile) {
        JsonObject out = new JsonObject();
        out.addProperty("schemaVersion", SCHEMA_VERSION);
        out.addProperty("status", result.status());
        out.addProperty("exitCode", result.exitCode());
        out.add("configuration", configuration());
        out.add("counts", counts(result));
        out.addProperty("wallTimeSeconds", wallTimeSeconds(result.elapsed()));
        out.add("timing", elapsed(result.elapsed()));
        out.add("reports", reports(result, outputFile));
        JsonArray issues = new JsonArray();
        for (IssueResult issue : result.issues()) issues.add(issue(issue));
        out.add("issues", issues);
        JsonArray tests = new JsonArray();
        for (CaseResult resultCase : result.cases()) tests.add(test(resultCase));
        out.add("tests", tests);
        return ASCII.translate(GSON.toJson(out)) + "\n";
    }

    private static JsonObject configuration() {
        JsonObject out = new JsonObject();
        out.addProperty("mode", HorizonQAProperties.modeName());
        out.addProperty("rawMode", HorizonQAProperties.rawMode());
        out.addProperty("world", HorizonQAProperties.worldPolicyName());
        out.addProperty("rawWorld", HorizonQAProperties.rawWorld());
        out.addProperty("autoRun", HorizonQAProperties.autoRunTests());
        out.addProperty("rawAutoRun", HorizonQAProperties.rawAutoRun());
        out.addProperty("stopServer", HorizonQAProperties.stopServerAfterRun());
        out.addProperty("rawStopServer", HorizonQAProperties.rawStopServer());
        out.addProperty("turbo", HorizonQAProperties.turboMultiplier());
        out.addProperty("rawTurbo", HorizonQAProperties.rawTurbo());
        out.addProperty("gridOrigin", HorizonQAProperties.gridOriginName());
        out.addProperty("rawGridOrigin", HorizonQAProperties.rawGridOrigin());
        out.addProperty("tests", HorizonQAProperties.rawTests());
        out.addProperty("selectsAllTests", HorizonQAProperties.selectsAllTests());
        out.addProperty("allowNoTests", HorizonQAProperties.allowNoTests());
        out.addProperty("eventsEnabled", HorizonQAProperties.eventsEnabled());
        out.addProperty("reportFile", HorizonQAProperties.reportFile());
        out.addProperty("reportDir", HorizonQAProperties.reportDir());
        out.addProperty("statusFile", HorizonQAProperties.statusFile());
        return out;
    }

    private static JsonObject counts(RunResult result) {
        JsonObject out = new JsonObject();
        out.addProperty("selectedTests", result.selectedTests());
        out.addProperty("passed", result.passed());
        out.addProperty("failed", result.failed());
        out.addProperty("timedOut", result.timedOut());
        out.addProperty("skipped", result.skipped());
        out.addProperty("incomplete", result.incomplete());
        out.addProperty("requiredFailures", result.requiredFailures());
        out.addProperty("optionalFailures", result.optionalFailures());
        out.addProperty(
            "issues",
            result.issues()
                .size());
        out.addProperty("diagnosticErrors", result.diagnosticErrors());
        out.addProperty("junitFailures", result.junitFailures());
        out.addProperty("junitErrors", result.junitErrors());
        out.addProperty("junitSkipped", result.junitSkipped());
        return out;
    }

    private static JsonObject reports(RunResult result, File outputFile) {
        JsonObject out = new JsonObject();
        out.addProperty("junit", result.junitReport());
        out.addProperty("status", outputFile == null ? null : outputFile.getPath());
        return out;
    }

    private static JsonObject issue(IssueResult issue) {
        JsonObject out = new JsonObject();
        out.addProperty("id", issue.id());
        out.addProperty("kind", issue.kind());
        out.addProperty("source", issue.classname());
        out.addProperty("name", issue.name());
        out.addProperty("message", issue.message());
        out.addProperty("fatalInCi", issue.fatalInCi());
        addText(out, "details", issue.details());
        addText(out, "stackTrace", issue.stackTrace());
        return out;
    }

    private static JsonObject test(CaseResult resultCase) {
        JsonObject out = new JsonObject();
        out.addProperty("id", resultCase.id());
        out.addProperty("classname", resultCase.classname());
        out.addProperty("name", resultCase.name());
        out.addProperty("status", statusName(resultCase.status()));
        out.addProperty("required", resultCase.required());
        out.addProperty("ticks", resultCase.tickCount());
        out.addProperty("timeSeconds", resultCase.timeSeconds());
        out.addProperty(
            "wallTimeSeconds",
            wallTimeSeconds(
                resultCase.timing()
                    .total()));
        out.add("timing", caseTiming(resultCase.timing()));
        JsonArray steps = new JsonArray();
        for (StepResult step : resultCase.steps()) steps.add(step(step));
        out.add("steps", steps);
        addText(out, "parameters", resultCase.parameterSummary());
        if (!resultCase.outputLines()
            .isEmpty()) {
            JsonArray lines = new JsonArray();
            for (String line : resultCase.outputLines()) lines.add(new JsonPrimitive(line == null ? "" : line));
            out.add("output", lines);
        }
        addText(out, "blockedByIssueId", resultCase.blockedByIssueId());
        if (resultCase.skipped()) {
            out.addProperty("skipReason", resultCase.skipReason());
            out.addProperty("skipType", resultCase.failureType());
        } else if (!resultCase.passed()) {
            out.add("failure", failure(resultCase));
        }
        return out;
    }

    private static Double wallTimeSeconds(ElapsedTime elapsed) {
        return elapsed.state() == ElapsedTime.State.UNAVAILABLE ? null : elapsed.seconds();
    }

    private static JsonObject elapsed(ElapsedTime elapsed) {
        JsonObject out = new JsonObject();
        out.addProperty("wallTimeSeconds", wallTimeSeconds(elapsed));
        out.addProperty(
            "state",
            elapsed.state()
                .name()
                .toLowerCase(Locale.ROOT));
        return out;
    }

    private static JsonObject caseTiming(CaseTiming timing) {
        JsonObject out = new JsonObject();
        out.add("total", elapsed(timing.total()));
        out.add("execution", elapsed(timing.execution()));
        out.add("cleanup", elapsed(timing.cleanup()));
        return out;
    }

    private static JsonObject step(StepResult step) {
        JsonObject out = new JsonObject();
        out.addProperty("index", step.index());
        out.addProperty("label", step.label());
        out.addProperty("kind", step.kind());
        out.addProperty("phase", step.phase());
        out.addProperty("operation", step.operation());
        out.addProperty("executionSide", step.executionSide());
        out.addProperty("status", step.status());
        out.addProperty("attempts", step.attempts());
        out.addProperty("simulationTicks", step.simulationTicks());
        out.addProperty("requestedMultiplier", step.requestedMultiplier());
        out.addProperty("wallTimeSeconds", wallTimeSeconds(step.elapsed()));
        out.add("timing", elapsed(step.elapsed()));
        out.addProperty("source", step.source());
        return out;
    }

    private static JsonObject failure(CaseResult resultCase) {
        JsonObject out = new JsonObject();
        out.addProperty("message", resultCase.failureMessage());
        out.addProperty("type", resultCase.failureType());
        addText(out, "stackTrace", resultCase.failureTrace());
        return out;
    }

    private static String statusName(CaseResult.Status status) {
        if (status == null) return "";
        return switch (status) {
            case PASSED -> "passed";
            case SKIPPED -> "skipped";
            case FAILED -> "failed";
            case ERROR -> "error";
            case TIMED_OUT -> "timedOut";
            case NOT_STARTED -> "notStarted";
            case RUNNING -> "running";
            default -> status.name()
                .toLowerCase(Locale.ROOT);
        };
    }

    private static void addText(JsonObject out, String name, String value) {
        if (value != null && !value.isEmpty()) out.addProperty(name, value);
    }
}
