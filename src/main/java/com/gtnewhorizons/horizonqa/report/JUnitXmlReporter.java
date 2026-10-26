package com.gtnewhorizons.horizonqa.report;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Locale;

public final class JUnitXmlReporter {

    private JUnitXmlReporter() {}

    public static void write(RunResult result, File outputFile) throws IOException {
        AtomicReportWriter.write(outputFile, tempFile -> {
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8))) {
                pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                pw.printf(
                    Locale.ROOT,
                    "<testsuite name=\"horizonqa\" tests=\"%d\" failures=\"%d\" errors=\"%d\" skipped=\"%d\""
                        + " time=\"%.3f\" timestamp=\"%s\" hostname=\"localhost\">%n",
                    result.cases()
                        .size()
                        + result.issues()
                            .size(),
                    result.junitFailures(),
                    result.junitErrors(),
                    result.junitSkipped(),
                    junitSeconds(result.elapsed()),
                    sanitizeAttr(
                        Instant.now()
                            .toString()));

                pw.println("  <properties>");
                pw.printf(
                    Locale.ROOT,
                    "    <property name=\"wallTimeState\" value=\"%s\"/>%n",
                    result.elapsed()
                        .state()
                        .name()
                        .toLowerCase(Locale.ROOT));
                pw.println("    <property name=\"timeMeaning\" value=\"occupied wall time, not CPU time\"/>");
                pw.println("    <property name=\"bootstrapTiming\" value=\"unavailable\"/>");
                pw.println("    <property name=\"setupTiming\" value=\"unavailable\"/>");
                pw.println("  </properties>");

                for (CaseResult resultCase : result.cases()) {
                    writeTestCase(pw, resultCase);
                }
                for (IssueResult issue : result.issues()) {
                    writeIssue(pw, issue);
                }

                pw.println("</testsuite>");
                if (pw.checkError()) {
                    throw new IOException("Failed while writing JUnit XML report");
                }
            }
        });
    }

    private static void writeTestCase(PrintWriter pw, CaseResult resultCase) {
        pw.printf(
            Locale.ROOT,
            "  <testcase name=\"%s\" classname=\"%s\" time=\"%.3f\">%n",
            sanitizeAttr(resultCase.name()),
            sanitizeAttr(resultCase.classname()),
            junitSeconds(
                resultCase.timing()
                    .total()));

        if (resultCase.failedRequiredCase()) {
            writeFailure(pw, resultCase);
        } else if (resultCase.infrastructureError()) {
            writeError(pw, resultCase);
        } else if (resultCase.skipped() || resultCase.failedOptionalCase() || resultCase.skippedBySetup()) {
            writeSkipped(pw, resultCase);
        }

        pw.println("    <system-out>");
        pw.print(
            escapeBody(
                "wallTimeState=" + resultCase.timing()
                    .total()
                    .state()
                    .name()
                    .toLowerCase(Locale.ROOT) + "\n"));
        if (hasText(resultCase.blockedByIssueId())) {
            pw.print(escapeBody("blockedByIssueId=" + resultCase.blockedByIssueId() + "\n"));
        }
        for (String line : resultCase.outputLines()) {
            pw.print(escapeBody(line + "\n"));
        }
        pw.println("    </system-out>");

        pw.println("  </testcase>");
    }

    private static double junitSeconds(ElapsedTime elapsed) {
        return elapsed.state() == ElapsedTime.State.UNAVAILABLE ? 0.0 : elapsed.seconds();
    }

    private static void writeFailure(PrintWriter pw, CaseResult resultCase) {
        pw.printf(
            Locale.ROOT,
            "    <failure message=\"%s\" type=\"%s\">%n",
            sanitizeAttr(resultCase.failureMessage()),
            sanitizeAttr(resultCase.failureType()));
        pw.print(escapeBody(resultCase.failureTrace()));
        pw.println("    </failure>");
    }

    private static void writeError(PrintWriter pw, CaseResult resultCase) {
        String trace = resultCase.failureTrace();
        if (trace == null || trace.isEmpty()) {
            pw.printf(
                Locale.ROOT,
                "    <error message=\"%s\" type=\"%s\"/>%n",
                sanitizeAttr(resultCase.failureMessage()),
                sanitizeAttr(resultCase.failureType()));
            return;
        }
        pw.printf(
            Locale.ROOT,
            "    <error message=\"%s\" type=\"%s\">%n",
            sanitizeAttr(resultCase.failureMessage()),
            sanitizeAttr(resultCase.failureType()));
        pw.print(escapeBody(trace));
        pw.println("    </error>");
    }

    private static void writeSkipped(PrintWriter pw, CaseResult resultCase) {
        String trace = resultCase.failureTrace();
        if (trace == null || trace.isEmpty()) {
            pw.printf(
                Locale.ROOT,
                "    <skipped message=\"%s\" type=\"%s\"/>%n",
                sanitizeAttr(resultCase.failureMessage()),
                sanitizeAttr(resultCase.failureType()));
            return;
        }
        pw.printf(
            Locale.ROOT,
            "    <skipped message=\"%s\" type=\"%s\">%n",
            sanitizeAttr(resultCase.failureMessage()),
            sanitizeAttr(resultCase.failureType()));
        pw.print(escapeBody(trace));
        pw.println("    </skipped>");
    }

    private static void writeIssue(PrintWriter pw, IssueResult issue) {
        pw.printf(
            Locale.ROOT,
            "  <testcase name=\"%s\" classname=\"%s\" time=\"0.000\">%n",
            sanitizeAttr(issue.name()),
            sanitizeAttr(issue.classname()));
        if (hasText(issue.stackTrace())) {
            pw.printf(
                Locale.ROOT,
                "    <error message=\"%s\" type=\"%s\">%n",
                sanitizeAttr(issue.message()),
                sanitizeAttr(issue.kind()));
            pw.print(escapeBody(issue.stackTrace()));
            pw.println("    </error>");
        } else {
            pw.printf(
                Locale.ROOT,
                "    <error message=\"%s\" type=\"%s\"/>%n",
                sanitizeAttr(issue.message()),
                sanitizeAttr(issue.kind()));
        }
        pw.println("    <system-out>");
        pw.println("wallTimeState=unavailable");
        if (hasText(issue.details())) pw.print(escapeBody(issue.details()));
        pw.println("    </system-out>");
        pw.println("  </testcase>");
    }

    /**
     * Single-pass XML 1.0 attribute sanitizer.
     * Escapes XML entities and strips invalid/control characters.
     *
     * @param s string to sanitize
     */
    static String sanitizeAttr(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
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
    static String escapeBody(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
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
            || (cp >= 0x20 && cp <= 0x7E)
            || (cp >= 0xA0 && cp <= 0xD7FF)
            || (cp >= 0xE000 && cp <= 0xFFFD)
            || (cp >= 0x10000 && cp <= 0x10FFFF);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }
}
