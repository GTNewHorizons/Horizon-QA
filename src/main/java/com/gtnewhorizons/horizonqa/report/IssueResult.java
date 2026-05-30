package com.gtnewhorizons.horizonqa.report;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.HorizonQAProperties.PropertyIssue;
import com.gtnewhorizons.horizonqa.internal.GameTestSelection.SelectionIssue;

@Desugar
public record IssueResult(String id, String kind, String classname, String name, String message, String details,
    boolean fatalInCi) {

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
}
