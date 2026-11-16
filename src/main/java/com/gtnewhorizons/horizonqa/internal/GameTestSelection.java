package com.gtnewhorizons.horizonqa.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;

@Desugar
public record GameTestSelection(List<GameTestDefinition> selectedTests, List<SelectionIssue> infrastructureIssues) {

    public GameTestSelection {
        selectedTests = immutableList(selectedTests);
        infrastructureIssues = immutableList(infrastructureIssues);
    }

    public static SelectionIssue noSelectedTests(boolean selectedAllTests, String rawSelectors) {
        String selector = selectedAllTests ? "<all valid tests>" : rawSelectors;
        String message = selectedAllTests ? "No valid tests were discovered."
            : "No valid tests were selected by -D" + HorizonQAProperties.TESTS_PROPERTY + "=" + selector + ".";
        return new SelectionIssue("selection:noTestsSelected", "NO_TESTS_SELECTED", selector, message);
    }

    private static <T> List<T> immutableList(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    @Desugar
    public record SelectionIssue(String id, String kind, String selector, String message) {

    }
}
