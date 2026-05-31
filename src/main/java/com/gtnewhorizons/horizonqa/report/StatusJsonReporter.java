package com.gtnewhorizons.horizonqa.report;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.gtnewhorizons.horizonqa.HorizonQAProperties;

public final class StatusJsonReporter {

    private static final int SCHEMA_VERSION = 1;

    private StatusJsonReporter() {}

    public static void write(RunResult result, File outputFile) throws IOException {
        AtomicReportWriter.write(
            outputFile,
            tempFile -> Files.write(tempFile, toJson(result, outputFile).getBytes(StandardCharsets.UTF_8)));
    }

    static String toJson(RunResult result, File outputFile) {
        StringBuilder out = new StringBuilder(4096);
        boolean first = true;

        out.append("{\n");
        first = appendNumberField(out, 1, "schemaVersion", SCHEMA_VERSION, first);
        first = appendStringField(out, 1, "status", result.status(), first);
        first = appendNumberField(out, 1, "exitCode", result.exitCode(), first);
        first = appendConfiguration(out, first);
        first = appendCounts(out, result, first);
        first = appendReports(out, result, outputFile, first);
        first = appendIssues(out, result, first);
        appendTests(out, result, first);
        out.append("\n}\n");

        return out.toString();
    }

    private static boolean appendConfiguration(StringBuilder out, boolean first) {
        appendFieldPrefix(out, 1, first);
        appendQuoted(out, "configuration");
        out.append(": {\n");

        boolean configFirst = true;
        configFirst = appendStringField(out, 2, "mode", HorizonQAProperties.modeName(), configFirst);
        configFirst = appendStringField(out, 2, "rawMode", HorizonQAProperties.rawMode(), configFirst);
        configFirst = appendStringField(out, 2, "tests", HorizonQAProperties.rawTests(), configFirst);
        configFirst = appendBooleanField(out, 2, "selectsAllTests", HorizonQAProperties.selectsAllTests(), configFirst);
        configFirst = appendBooleanField(out, 2, "allowNoTests", HorizonQAProperties.allowNoTests(), configFirst);
        configFirst = appendBooleanField(out, 2, "eventsEnabled", HorizonQAProperties.eventsEnabled(), configFirst);
        configFirst = appendStringField(out, 2, "reportFile", HorizonQAProperties.reportFile(), configFirst);
        configFirst = appendStringField(out, 2, "reportDir", HorizonQAProperties.reportDir(), configFirst);
        appendStringField(out, 2, "statusFile", HorizonQAProperties.statusFile(), configFirst);

        out.append('\n');
        indent(out, 1);
        out.append('}');
        return false;
    }

    private static boolean appendCounts(StringBuilder out, RunResult result, boolean first) {
        appendFieldPrefix(out, 1, first);
        appendQuoted(out, "counts");
        out.append(": {\n");

        boolean countFirst = true;
        countFirst = appendNumberField(out, 2, "selectedTests", result.selectedTests(), countFirst);
        countFirst = appendNumberField(out, 2, "passed", result.passed(), countFirst);
        countFirst = appendNumberField(out, 2, "failed", result.failed(), countFirst);
        countFirst = appendNumberField(out, 2, "timedOut", result.timedOut(), countFirst);
        countFirst = appendNumberField(out, 2, "incomplete", result.incomplete(), countFirst);
        countFirst = appendNumberField(out, 2, "requiredFailures", result.requiredFailures(), countFirst);
        countFirst = appendNumberField(out, 2, "optionalFailures", result.optionalFailures(), countFirst);
        countFirst = appendNumberField(
            out,
            2,
            "issues",
            result.issues()
                .size(),
            countFirst);
        countFirst = appendNumberField(out, 2, "diagnosticErrors", result.diagnosticErrors(), countFirst);
        countFirst = appendNumberField(out, 2, "junitFailures", result.junitFailures(), countFirst);
        countFirst = appendNumberField(out, 2, "junitErrors", result.junitErrors(), countFirst);
        appendNumberField(out, 2, "junitSkipped", result.junitSkipped(), countFirst);

        out.append('\n');
        indent(out, 1);
        out.append('}');
        return false;
    }

    private static boolean appendReports(StringBuilder out, RunResult result, File outputFile, boolean first) {
        appendFieldPrefix(out, 1, first);
        appendQuoted(out, "reports");
        out.append(": {\n");

        boolean reportFirst = true;
        reportFirst = appendStringField(out, 2, "junit", result.junitReport(), reportFirst);
        appendStringField(out, 2, "status", outputFile == null ? null : outputFile.getPath(), reportFirst);

        out.append('\n');
        indent(out, 1);
        out.append('}');
        return false;
    }

    private static boolean appendIssues(StringBuilder out, RunResult result, boolean first) {
        appendFieldPrefix(out, 1, first);
        appendQuoted(out, "issues");
        out.append(": [");
        if (!result.issues()
            .isEmpty()) {
            out.append('\n');
            for (int i = 0; i < result.issues()
                .size(); i++) {
                if (i > 0) {
                    out.append(",\n");
                }
                appendIssue(
                    out,
                    result.issues()
                        .get(i));
            }
            out.append('\n');
            indent(out, 1);
        }
        out.append(']');
        return false;
    }

    private static void appendIssue(StringBuilder out, IssueResult issue) {
        indent(out, 2);
        out.append("{\n");

        boolean first = true;
        first = appendStringField(out, 3, "id", issue.id(), first);
        first = appendStringField(out, 3, "kind", issue.kind(), first);
        first = appendStringField(out, 3, "source", issue.classname(), first);
        first = appendStringField(out, 3, "name", issue.name(), first);
        first = appendStringField(out, 3, "message", issue.message(), first);
        first = appendBooleanField(out, 3, "fatalInCi", issue.fatalInCi(), first);
        if (hasText(issue.details())) {
            first = appendStringField(out, 3, "details", issue.details(), first);
        }
        if (hasText(issue.stackTrace())) {
            appendStringField(out, 3, "stackTrace", issue.stackTrace(), first);
        }

        out.append('\n');
        indent(out, 2);
        out.append('}');
    }

    private static void appendTests(StringBuilder out, RunResult result, boolean first) {
        appendFieldPrefix(out, 1, first);
        appendQuoted(out, "tests");
        out.append(": [");
        if (!result.cases()
            .isEmpty()) {
            out.append('\n');
            for (int i = 0; i < result.cases()
                .size(); i++) {
                if (i > 0) {
                    out.append(",\n");
                }
                appendTest(
                    out,
                    result.cases()
                        .get(i));
            }
            out.append('\n');
            indent(out, 1);
        }
        out.append(']');
    }

    private static void appendTest(StringBuilder out, CaseResult resultCase) {
        indent(out, 2);
        out.append("{\n");

        boolean first = true;
        first = appendStringField(out, 3, "id", resultCase.id(), first);
        first = appendStringField(out, 3, "classname", resultCase.classname(), first);
        first = appendStringField(out, 3, "name", resultCase.name(), first);
        first = appendStringField(out, 3, "status", statusName(resultCase.status()), first);
        first = appendBooleanField(out, 3, "required", resultCase.required(), first);
        first = appendNumberField(out, 3, "ticks", resultCase.tickCount(), first);
        first = appendNumberField(out, 3, "timeSeconds", resultCase.timeSeconds(), first);
        if (hasText(resultCase.blockedByIssueId())) {
            first = appendStringField(out, 3, "blockedByIssueId", resultCase.blockedByIssueId(), first);
        }
        if (!resultCase.passed()) {
            appendFailure(out, resultCase, first);
        }

        out.append('\n');
        indent(out, 2);
        out.append('}');
    }

    private static void appendFailure(StringBuilder out, CaseResult resultCase, boolean first) {
        appendFieldPrefix(out, 3, first);
        appendQuoted(out, "failure");
        out.append(": {\n");

        boolean failureFirst = true;
        failureFirst = appendStringField(out, 4, "message", resultCase.failureMessage(), failureFirst);
        failureFirst = appendStringField(out, 4, "type", resultCase.failureType(), failureFirst);
        if (hasText(resultCase.failureTrace())) {
            appendStringField(out, 4, "stackTrace", resultCase.failureTrace(), failureFirst);
        }

        out.append('\n');
        indent(out, 3);
        out.append('}');
    }

    private static String statusName(CaseResult.Status status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case PASSED -> "passed";
            case FAILED -> "failed";
            case TIMED_OUT -> "timedOut";
            case NOT_STARTED -> "notStarted";
            case RUNNING -> "running";
            default -> status.name()
                .toLowerCase();
        };
    }

    private static boolean appendStringField(StringBuilder out, int indent, String name, String value, boolean first) {
        appendFieldPrefix(out, indent, first);
        appendQuoted(out, name);
        out.append(": ");
        appendStringOrNull(out, value);
        return false;
    }

    private static boolean appendBooleanField(StringBuilder out, int indent, String name, boolean value,
        boolean first) {
        appendFieldPrefix(out, indent, first);
        appendQuoted(out, name);
        out.append(": ")
            .append(value);
        return false;
    }

    private static boolean appendNumberField(StringBuilder out, int indent, String name, long value, boolean first) {
        appendFieldPrefix(out, indent, first);
        appendQuoted(out, name);
        out.append(": ")
            .append(value);
        return false;
    }

    private static boolean appendNumberField(StringBuilder out, int indent, String name, double value, boolean first) {
        appendFieldPrefix(out, indent, first);
        appendQuoted(out, name);
        out.append(": ")
            .append(value);
        return false;
    }

    private static void appendFieldPrefix(StringBuilder out, int indent, boolean first) {
        if (!first) {
            out.append(",\n");
        }
        indent(out, indent);
    }

    private static void appendStringOrNull(StringBuilder out, String value) {
        if (value == null) {
            out.append("null");
            return;
        }
        appendQuoted(out, value);
    }

    private static void appendQuoted(StringBuilder out, String value) {
        out.append('"')
            .append(escape(value))
            .append('"');
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private static void indent(StringBuilder out, int indent) {
        for (int i = 0; i < indent; i++) {
            out.append("  ");
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            switch (cp) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (cp < 0x20 || cp > 0x7E) {
                        appendUnicodeEscape(out, cp);
                    } else {
                        out.appendCodePoint(cp);
                    }
                    break;
            }
            offset += Character.charCount(cp);
        }
        return out.toString();
    }

    private static void appendUnicodeEscape(StringBuilder out, int cp) {
        if (cp <= 0xFFFF) {
            appendHexEscape(out, (char) cp);
            return;
        }
        char[] chars = Character.toChars(cp);
        for (char c : chars) {
            appendHexEscape(out, c);
        }
    }

    private static void appendHexEscape(StringBuilder out, char c) {
        out.append("\\u");
        String hex = Integer.toHexString(c);
        for (int i = hex.length(); i < 4; i++) {
            out.append('0');
        }
        out.append(hex);
    }
}
