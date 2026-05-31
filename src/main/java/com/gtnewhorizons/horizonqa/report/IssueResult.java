package com.gtnewhorizons.horizonqa.report;

import java.io.PrintWriter;
import java.io.StringWriter;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.HorizonQAProperties.PropertyIssue;
import com.gtnewhorizons.horizonqa.internal.GameTestSelection.SelectionIssue;

@Desugar
public record IssueResult(String id, String kind, String classname, String name, String message, String details,
    boolean fatalInCi, String stackTrace) {

    public IssueResult(String id, String kind, String classname, String name, String message, String details,
        boolean fatalInCi) {
        this(id, kind, classname, name, message, details, fatalInCi, "");
    }

    public IssueResult {
        stackTrace = stackTrace == null ? "" : stackTrace;
    }

    public static IssueResult selection(SelectionIssue issue) {
        return new IssueResult(
            issue.id(),
            issue.kind(),
            "horizonqa.selection",
            "selector:" + issue.selector(),
            issue.message(),
            "issue.id=" + issue.id() + "\nselector=" + issue.selector() + "\n",
            true);
    }

    public static IssueResult property(PropertyIssue issue) {
        return new IssueResult(
            issue.id(),
            issue.kind(),
            "horizonqa.configuration",
            "config:" + issue.property(),
            issue.message(),
            "issue.id=" + issue.id() + "\nproperty=" + issue.property() + "\n",
            issue.fatalInCi());
    }

    public static IssueResult reporting(String reporter, String target, Exception error) {
        String name = reporter == null || reporter.isEmpty() ? "report" : reporter;
        String message = error != null && error.getMessage() != null ? error.getMessage() : "unknown reporting error";
        String id = "reporting:" + name;
        String details = "issue.id=" + id + "\nreporter=" + name + "\ntarget=" + (target == null ? "" : target) + "\n";
        return new IssueResult(
            id,
            "REPORTING_ERROR",
            "horizonqa.reporting",
            "report:" + name,
            "Failed to write " + name + " report: " + message,
            details,
            true,
            stackTrace(error));
    }

    public static IssueResult reportPath(String id, String name, String target, String message, Exception error) {
        String safeId = id == null || id.isEmpty() ? "reportPath:unknown" : id;
        String safeName = name == null || name.isEmpty() ? "unknown" : name;
        String details = "issue.id=" + safeId + "\ntarget=" + (target == null ? "" : target) + "\n";
        return new IssueResult(
            safeId,
            "REPORT_PATH_ERROR",
            "horizonqa.reporting",
            "report-path:" + safeName,
            message == null || message.isEmpty() ? "Invalid report output path" : message,
            details,
            true,
            stackTrace(error));
    }

    private static String stackTrace(Exception error) {
        if (error == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
