package com.gtnewhorizons.horizonqa.report;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Standalone timings from the recorded monotonic intervals, never reconstructed from simulation ticks. */
public final class HtmlTimingReporter {

    private HtmlTimingReporter() {}

    public static File outputFile(File statusFile) {
        return new File(
            statusFile.getAbsoluteFile()
                .getParentFile(),
            "horizonqa-timing.html");
    }

    public static void write(RunResult result, File outputFile) throws IOException {
        AtomicReportWriter
            .write(outputFile, temporary -> Files.write(temporary, toHtml(result).getBytes(StandardCharsets.UTF_8)));
    }

    static String toHtml(RunResult result) {
        StringBuilder out = new StringBuilder(8192);
        out.append("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            .append("<title>Horizon QA timing</title><style>")
            .append(
                "body{font:16px/1.5 system-ui,sans-serif;margin:2rem auto;padding:0 1rem;max-width:1200px;color:#17232d;background:#fafbfc}")
            .append("h1,h2{line-height:1.2}table{border-collapse:collapse;width:100%;margin:1rem 0;background:white}")
            .append(
                "th,td{text-align:left;vertical-align:top;padding:.5rem;border-bottom:1px solid #dce2e8;overflow-wrap:anywhere}")
            .append("th{background:#eaf0f5}details{margin:1rem 0}summary{cursor:pointer;font-weight:600}")
            .append(".scroll{overflow-x:auto}.note{color:#475869}code{white-space:pre-wrap}")
            .append("</style><body><h1>Horizon QA timing</h1>");
        out.append("<p>Suite status: <strong>")
            .append(text(result.status()))
            .append("</strong>. Wall time: <strong>")
            .append(text(elapsed(result.elapsed())))
            .append("</strong>.</p>");
        out.append("<p>")
            .append(result.selectedTests())
            .append(" selected, ")
            .append(result.passed())
            .append(" passed, ")
            .append(result.failed())
            .append(" failed, ")
            .append(result.timedOut())
            .append(" timed out, ")
            .append(result.skipped())
            .append(" skipped.</p>");
        out.append("<p class=\"note\">Occupied wall time includes waiting and is not CPU time. ")
            .append(
                "Suite duration is measured independently. Test and step intervals may overlap and are not added together. ")
            .append("Separate bootstrap and setup measurements are unavailable. Unmeasured is not zero. ")
            .append(
                "Partial intervals ended before normal completion. Simulation ticks and requested acceleration are separate observations.</p>");
        if (!result.issues()
            .isEmpty()) {
            out.append("<h2>Run issues</h2><ul>");
            for (IssueResult issue : result.issues()) {
                out.append("<li>")
                    .append(text(issue.kind()))
                    .append(": ")
                    .append(text(issue.message()))
                    .append("</li>");
            }
            out.append("</ul>");
        }
        appendRankings(out, result);
        out.append("<h2>Cases and steps</h2>");
        for (CaseResult resultCase : result.cases()) appendCase(out, resultCase);
        return out.append("</body></html>\n")
            .toString();
    }

    private static void appendRankings(StringBuilder out, RunResult result) {
        List<CaseResult> cases = new ArrayList<>(result.cases());
        cases.removeIf(
            value -> value.timing()
                .total()
                .state() == ElapsedTime.State.UNAVAILABLE);
        cases.sort(
            Comparator.comparingDouble(
                (CaseResult value) -> value.timing()
                    .total()
                    .seconds())
                .reversed());
        out.append("<h2>Slowest tests by occupied wall time</h2>");
        table(out, "Test", "Status", "Total", "Execution", "Cleanup");
        for (CaseResult value : cases.subList(0, Math.min(10, cases.size()))) {
            row(
                out,
                value.id(),
                value.status()
                    .name(),
                elapsed(
                    value.timing()
                        .total()),
                elapsed(
                    value.timing()
                        .execution()),
                elapsed(
                    value.timing()
                        .cleanup()));
        }
        endTable(out);
        List<Map.Entry<CaseResult, StepResult>> steps = new ArrayList<>();
        for (CaseResult value : result.cases()) {
            for (StepResult step : value.steps()) {
                if (step.elapsed()
                    .state() != ElapsedTime.State.UNAVAILABLE)
                    steps.add(new AbstractMap.SimpleImmutableEntry<>(value, step));
            }
        }
        steps.sort(
            Comparator.comparingDouble(
                (Map.Entry<CaseResult, StepResult> value) -> value.getValue()
                    .elapsed()
                    .seconds())
                .reversed());
        out.append("<h2>Slowest steps by occupied wall time</h2>");
        table(out, "Test", "Step", "Operation", "Execution side", "Status", "Wall time");
        for (Map.Entry<CaseResult, StepResult> value : steps.subList(0, Math.min(10, steps.size()))) {
            StepResult step = value.getValue();
            row(
                out,
                value.getKey()
                    .id(),
                step.index() + ". " + step.label(),
                step.operation(),
                step.executionSide(),
                step.status(),
                elapsed(step.elapsed()));
        }
        endTable(out);
    }

    private static void appendCase(StringBuilder out, CaseResult value) {
        out.append("<details><summary>")
            .append(text(value.id()))
            .append(" · ")
            .append(
                text(
                    value.status()
                        .name()))
            .append(" · ")
            .append(
                text(
                    elapsed(
                        value.timing()
                            .total())))
            .append("</summary>");
        table(out, "Total", "Execution", "Cleanup", "Simulation ticks");
        row(
            out,
            elapsed(
                value.timing()
                    .total()),
            elapsed(
                value.timing()
                    .execution()),
            elapsed(
                value.timing()
                    .cleanup()),
            String.valueOf(value.tickCount()));
        endTable(out);
        if (!value.passed() && value.failureMessage() != null) {
            out.append("<p><strong>Outcome:</strong> ")
                .append(text(value.failureMessage()))
                .append("</p>");
        }
        if (value.steps()
            .isEmpty()) {
            out.append("<p>No recorded sequence steps.</p>");
        } else {
            table(
                out,
                "Step",
                "Operation",
                "Execution side",
                "Kind / phase",
                "Status",
                "Wall time",
                "Attempts",
                "Simulation ticks",
                "Requested multiplier",
                "Source");
            for (StepResult step : value.steps()) {
                row(
                    out,
                    step.index() + ". " + step.label(),
                    step.operation(),
                    step.executionSide(),
                    step.kind() + " / " + step.phase(),
                    step.status(),
                    elapsed(step.elapsed()),
                    String.valueOf(step.attempts()),
                    String.valueOf(step.simulationTicks()),
                    String.valueOf(step.requestedMultiplier()),
                    step.source());
            }
            endTable(out);
        }
        out.append("</details>");
    }

    private static void table(StringBuilder out, String... headings) {
        out.append("<div class=\"scroll\"><table><thead><tr>");
        for (String heading : headings) out.append("<th scope=\"col\">")
            .append(text(heading))
            .append("</th>");
        out.append("</tr></thead><tbody>");
    }

    private static void row(StringBuilder out, String... values) {
        out.append("<tr>");
        for (String value : values) out.append("<td>")
            .append(text(value))
            .append("</td>");
        out.append("</tr>");
    }

    private static void endTable(StringBuilder out) {
        out.append("</tbody></table></div>");
    }

    private static String elapsed(ElapsedTime value) {
        if (value.state() == ElapsedTime.State.UNAVAILABLE) return "unmeasured (unavailable)";
        return String.format(
            Locale.ROOT,
            "%.3f s (%s)",
            value.seconds(),
            value.state()
                .name()
                .toLowerCase(Locale.ROOT));
    }

    private static String text(String value) {
        return JUnitXmlReporter.escapeBody(value);
    }
}
