package com.gtnewhorizons.horizonqa.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.HorizonQAProperties.SelectorType;
import com.gtnewhorizons.horizonqa.HorizonQAProperties.TestSelector;

@Desugar
public record GameTestSelection(List<GameTestDefinition> selectedTests, List<SelectionIssue> infrastructureIssues) {

    public static GameTestSelection from(DiscoveryResult discovery) {
        return from(
            discovery.validTests(),
            discovery.invalidTests(),
            discovery.duplicateIds(),
            HorizonQAProperties.selectsAllTests(),
            HorizonQAProperties.testSelectors());
    }

    public static GameTestSelection from(List<GameTestDefinition> validTests, List<InvalidTestDefinition> invalidTests,
        List<DuplicateTestId> duplicateIds, boolean selectsAllTests, List<TestSelector> selectors) {

        if (selectsAllTests) {
            return new GameTestSelection(immutableList(validTests), Collections.emptyList());
        }

        Set<String> selectedIds = new LinkedHashSet<>();
        List<SelectionIssue> issues = new ArrayList<>();
        Set<String> emittedIssueIds = new HashSet<>();

        for (TestSelector selector : selectors) {
            boolean matchedValid = false;
            for (GameTestDefinition def : validTests) {
                if (matches(selector, def.getTestId())) {
                    matchedValid = true;
                    selectedIds.add(def.getTestId());
                }
            }

            if (!matchedValid) {
                SelectionIssue issue = unmatchedIssue(
                    selector,
                    matchesInvalid(selector, invalidTests),
                    matchesDuplicate(selector, duplicateIds));
                if (emittedIssueIds.add(issue.id())) {
                    issues.add(issue);
                }
            }
        }

        List<GameTestDefinition> selected = new ArrayList<>();
        for (GameTestDefinition def : validTests) {
            if (selectedIds.contains(def.getTestId())) {
                selected.add(def);
            }
        }

        return new GameTestSelection(immutableList(selected), immutableList(issues));
    }

    public static SelectionIssue noSelectedTests(boolean selectedAllTests) {
        String selector = selectedAllTests ? "<all valid tests>" : HorizonQAProperties.rawTests();
        String message = selectedAllTests ? "No valid tests were discovered."
            : "No valid tests were selected by -D" + HorizonQAProperties.TESTS_PROPERTY + "=" + selector + ".";
        return new SelectionIssue("selection:noTestsSelected", "NO_TESTS_SELECTED", selector, message);
    }

    private static boolean matches(TestSelector selector, String testId) {
        if (selector.type() == SelectorType.NAMESPACE) {
            return testId.startsWith(selector.value() + ":");
        }
        return testId.equals(selector.value());
    }

    private static boolean matchesInvalid(TestSelector selector, List<InvalidTestDefinition> invalidTests) {
        for (InvalidTestDefinition invalidTest : invalidTests) {
            if (matches(selector, invalidTest.intendedTestId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesDuplicate(TestSelector selector, List<DuplicateTestId> duplicateIds) {
        for (DuplicateTestId duplicateId : duplicateIds) {
            if (matches(selector, duplicateId.testId())) {
                return true;
            }
        }
        return false;
    }

    private static SelectionIssue unmatchedIssue(TestSelector selector, boolean matchedInvalid,
        boolean matchedDuplicate) {
        String selectorKind = selector.type() == SelectorType.NAMESPACE ? "namespace" : "exact test id";
        String issueKind;
        String diagnosticKind;
        String message;
        if (matchedInvalid && matchedDuplicate) {
            issueKind = "excludedOnly";
            diagnosticKind = "EXCLUDED_TEST_SELECTION";
            message = "The " + selectorKind
                + " selector '"
                + selector.value()
                + "' matched only tests excluded during discovery; fix the discovery diagnostics before selecting it.";
        } else if (matchedInvalid) {
            issueKind = "invalidOnly";
            diagnosticKind = "INVALID_TEST_SELECTION";
            message = "The " + selectorKind
                + " selector '"
                + selector.value()
                + "' matched only invalid test definitions; fix the discovery diagnostics before selecting it.";
        } else if (matchedDuplicate) {
            issueKind = "duplicateOnly";
            diagnosticKind = "DUPLICATE_TEST_SELECTION";
            message = "The " + selectorKind
                + " selector '"
                + selector.value()
                + "' matched only duplicate test ids excluded during discovery; fix the duplicate id diagnostics before selecting it.";
        } else {
            issueKind = "unmatched";
            diagnosticKind = "UNMATCHED_SELECTOR";
            message = "The " + selectorKind + " selector '" + selector.value() + "' did not match any valid tests.";
        }

        String selectorType = selector.type() == SelectorType.NAMESPACE ? "namespace" : "test";
        return new SelectionIssue(
            "selection:" + issueKind + ":" + selectorType + ":" + selector.value(),
            diagnosticKind,
            selector.value(),
            message);
    }

    private static <T> List<T> immutableList(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    @Desugar
    public record SelectionIssue(String id, String kind, String selector, String message) {

    }
}
