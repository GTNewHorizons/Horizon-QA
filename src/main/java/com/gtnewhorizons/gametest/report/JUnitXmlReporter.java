package com.gtnewhorizons.gametest.report;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.event.TestEvent;
import com.gtnewhorizons.gametest.internal.GameTestInstance;
import com.gtnewhorizons.gametest.internal.GameTestStatus;

public final class JUnitXmlReporter {

    private static final double TICKS_PER_SECOND = 20.0;

    private JUnitXmlReporter() {}

    public static void write(List<GameTestInstance> instances, File outputFile) throws IOException {
        Path path = outputFile.toPath();
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        var rollup = rollup(instances);

        try (var pw = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pw.printf(
                "<testsuite name=\"gametest\" tests=\"%d\" failures=\"%d\" errors=\"%d\" skipped=\"%d\""
                    + " time=\"%.3f\" timestamp=\"%s\" hostname=\"localhost\">%n",
                instances.size(),
                rollup.failures(),
                rollup.errors(),
                rollup.skipped(),
                suiteDuration(instances),
                sanitizeAttr(
                    Instant.now()
                        .toString()));

            for (var inst : instances) {
                writeTestCase(pw, inst);
            }

            pw.println("</testsuite>");
        }
    }

    private static void writeTestCase(PrintWriter pw, GameTestInstance inst) {
        String testId = inst.getDefinition()
            .getTestId();
        int sep = Math.max(testId.lastIndexOf('.'), testId.lastIndexOf('#'));

        var classname = sep > 0 ? testId.substring(0, sep) : "gametest";
        var name = sep > 0 ? testId.substring(sep + 1) : testId;
        double time = inst.getTickCount() / TICKS_PER_SECOND;

        var status = inst.getStatus();
        var warnings = inst.getWarnings();
        var events = inst.getRecorder()
            .snapshot();
        boolean hasWarnings = !warnings.isEmpty();
        boolean hasEvents = !events.isEmpty();

        if (status == GameTestStatus.PASSED && !hasWarnings && !hasEvents) {
            pw.printf(
                "  <testcase name=\"%s\" classname=\"%s\" time=\"%.3f\"/>%n",
                sanitizeAttr(name),
                sanitizeAttr(classname),
                time);
            return;
        }

        pw.printf(
            "  <testcase name=\"%s\" classname=\"%s\" time=\"%.3f\">%n",
            sanitizeAttr(name),
            sanitizeAttr(classname),
            time);

        if (status == GameTestStatus.FAILED) {
            Throwable cause = inst.getFailureCause();
            var message = cause != null ? cause.getMessage() : "Test failed";
            var type = cause != null ? cause.getClass()
                .getName() : "java.lang.AssertionError";

            pw.printf("    <failure message=\"%s\" type=\"%s\">%n", sanitizeAttr(message), sanitizeAttr(type));
            if (cause != null) {
                pw.print(escapeBody(stackTrace(cause)));
            }
            pw.println("    </failure>");
        } else if (status == GameTestStatus.TIMED_OUT) {
            pw.printf(
                "    <error message=\"Timed out after %d ticks\" type=\"%s\"/>%n",
                inst.getTickCount(),
                sanitizeAttr("GameTestTimeoutError"));
        } else if (status != GameTestStatus.PASSED) {
            pw.printf(
                "    <error message=\"Test did not complete (status: %s)\" type=\"%s\"/>%n",
                sanitizeAttr(status.toString()),
                sanitizeAttr("GameTestError"));
        }

        if (hasWarnings || hasEvents) {
            pw.println("    <system-out>");
            for (TestEvent e : events) {
                pw.print(escapeBody(formatEvent(e) + "\n"));
            }
            for (String w : warnings) {
                pw.print(escapeBody("WARNING: " + w + "\n"));
            }
            pw.println("    </system-out>");
        }

        pw.println("  </testcase>");
    }

    private static String formatEvent(TestEvent e) {
        return String.format("[t=%5d] [%-11s] %s", e.tick(), e.category(), e.summary());
    }

    private static double suiteDuration(List<GameTestInstance> instances) {
        return instances.stream()
            .mapToInt(GameTestInstance::getTickCount)
            .max()
            .orElse(0) / TICKS_PER_SECOND;
    }

    private static String stackTrace(Throwable t) {
        var sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static SuiteRollup rollup(List<GameTestInstance> instances) {
        long failures = 0, errors = 0, skipped = 0;
        for (var inst : instances) {
            switch (inst.getStatus()) {
                case PASSED -> {}
                case FAILED -> failures++;
                case TIMED_OUT, NOT_STARTED, RUNNING -> errors++;
            }
        }
        return new SuiteRollup(failures, errors, skipped);
    }

    @Desugar
    private record SuiteRollup(long failures, long errors, long skipped) {}

    /**
     * Single-pass XML 1.0 attribute sanitizer.
     * Escapes XML entities and strips invalid/control characters.
     * 
     * @param s string to sanitize
     */
    private static String sanitizeAttr(String s) {
        if (s == null) return "";
        var out = new StringBuilder(s.length() + 16);
        for (int offset = 0; offset < s.length();) {
            int cp = s.codePointAt(offset);
            switch (cp) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\r', '\n', '\t' -> out.append(' ');
                default -> {
                    if (isValidXml10Char(cp)) {
                        out.appendCodePoint(cp);
                    }
                }
            }
            offset += Character.charCount(cp);
        }
        return out.toString();
    }

    /**
     * Single-pass XML 1.0 body sanitizer.
     * Escapes entities and strips invalid characters (preserves standard whitespace).
     * 
     * @param s string to sanitize
     */
    private static String escapeBody(String s) {
        if (s == null) return "";
        var out = new StringBuilder(s.length() + 16);
        for (int offset = 0; offset < s.length();) {
            int cp = s.codePointAt(offset);
            switch (cp) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                default -> {
                    if (isValidXml10Char(cp)) {
                        out.appendCodePoint(cp);
                    }
                }
            }
            offset += Character.charCount(cp);
        }
        return out.toString();
    }

    /**
     * Valid XML 1.0 chars, excluding the 0x7F-0x9F control block to prevent CI parser crashes.
     * 
     * @param cp code point
     */
    private static boolean isValidXml10Char(int cp) {
        return cp == 0x9 || cp == 0xA
            || cp == 0xD
            || (cp >= 0x20 && cp <= 0x7E) // Standard printable ASCII
            || (cp >= 0xA0 && cp <= 0xD7FF) // Valid Unicode (excluding C1 controls)
            || (cp >= 0xE000 && cp <= 0xFFFD) // Valid Unicode
            || (cp >= 0x10000 && cp <= 0x10FFFF);// Supplementary planes
    }
}
