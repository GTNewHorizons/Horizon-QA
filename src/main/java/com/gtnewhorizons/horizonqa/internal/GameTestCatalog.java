package com.gtnewhorizons.horizonqa.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.HorizonQAProperties.SelectorType;
import com.gtnewhorizons.horizonqa.HorizonQAProperties.TestSelector;
import com.gtnewhorizons.horizonqa.api.annotation.MethodSource;
import com.gtnewhorizons.horizonqa.internal.GameTestSelection.SelectionIssue;

public final class GameTestCatalog {

    private final List<GameTestDefinition> tests;
    private final Map<String, List<Method>> beforeBatchMethods;
    private final Map<String, List<Method>> afterBatchMethods;
    private final DiscoveryDiagnostics diagnostics;

    GameTestCatalog(List<GameTestDefinition> tests, Map<String, List<Method>> beforeBatchMethods,
        Map<String, List<Method>> afterBatchMethods, List<InvalidTestDefinition> invalidTests,
        List<InvalidBatchHook> invalidHooks, List<DuplicateTestId> duplicateIds, List<DiscoveryIssue> issues) {

        this.tests = immutableList(tests);
        this.beforeBatchMethods = immutableHookMap(beforeBatchMethods);
        this.afterBatchMethods = immutableHookMap(afterBatchMethods);
        diagnostics = new DiscoveryDiagnostics(invalidTests, invalidHooks, duplicateIds, issues);
    }

    public List<GameTestDefinition> tests() {
        return tests;
    }

    public GameTestSelection select(boolean selectAll, List<TestSelector> selectors) {
        Objects.requireNonNull(selectors, "selectors");
        if (selectAll) {
            return new GameTestSelection(tests, Collections.emptyList());
        }

        Set<String> selectedIds = new LinkedHashSet<>();
        List<SelectionIssue> issues = new ArrayList<>();
        Set<String> emittedIssueIds = new HashSet<>();

        for (TestSelector selector : selectors) {
            boolean matchedValid = false;
            for (GameTestDefinition def : tests) {
                if (matches(selector, def)) {
                    matchedValid = true;
                    selectedIds.add(def.getTestId());
                }
            }

            if (!matchedValid) {
                InvalidTestDefinition matchedInvalid = matchingInvalid(selector);
                SelectionIssue issue = unmatchedIssue(selector, matchedInvalid, matchesDuplicate(selector));
                if (emittedIssueIds.add(issue.id())) {
                    issues.add(issue);
                }
            }
        }

        List<GameTestDefinition> selected = new ArrayList<>();
        for (GameTestDefinition def : tests) {
            if (selectedIds.contains(def.getTestId())) {
                selected.add(def);
            }
        }
        return new GameTestSelection(selected, issues);
    }

    public List<GameTestDefinition> matchingTests(String selectorValue) {
        Objects.requireNonNull(selectorValue, "selectorValue");
        SelectorType type = selectorValue.indexOf(':') >= 0 ? SelectorType.TEST_ID_PREFIX
            : SelectorType.NAMESPACE_OR_HOLDER;
        TestSelector selector = new TestSelector(type, selectorValue);
        List<GameTestDefinition> selected = new ArrayList<>();
        for (GameTestDefinition def : tests) {
            if (matches(selector, def)) {
                selected.add(def);
            }
        }
        return immutableList(selected);
    }

    public GameTestDefinition findTest(String testId) {
        Objects.requireNonNull(testId, "testId");
        for (GameTestDefinition def : tests) {
            if (def.getTestId()
                .equals(testId)) return def;
        }
        return null;
    }

    public InvalidTestDefinition findInvalidTest(String testId) {
        Objects.requireNonNull(testId, "testId");
        for (InvalidTestDefinition invalidTest : diagnostics.invalidTests()) {
            if (invalidTest.intendedTestId()
                .equals(testId)) return invalidTest;
        }
        return null;
    }

    public List<String> testIds() {
        List<String> ids = new ArrayList<>(tests.size());
        for (GameTestDefinition test : tests) {
            ids.add(test.getTestId());
        }
        return immutableList(ids);
    }

    public List<String> selectorCandidates() {
        Set<String> selectors = new LinkedHashSet<>();
        for (GameTestDefinition def : tests) {
            String id = def.getBaseTestId();
            int colon = id.indexOf(':');
            int dot = id.lastIndexOf('.');
            if (colon > 0) selectors.add(id.substring(0, colon));
            selectors.add(def.getHolderSimpleName());
            if (colon > 0 && dot > colon) selectors.add(id.substring(0, dot));
            selectors.add(id);
            selectors.add(def.getTestId());
        }
        return immutableList(new ArrayList<>(selectors));
    }

    public List<String> templateNames() {
        Set<String> names = new LinkedHashSet<>();
        for (GameTestDefinition def : tests) {
            String templateName = def.getTemplateName();
            if (templateName != null && !templateName.isEmpty()) {
                names.add(templateName);
            }
        }
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        return immutableList(sorted);
    }

    public DiscoveryDiagnostics diagnostics() {
        return diagnostics;
    }

    BatchHooks batchHooks(String batch) {
        Objects.requireNonNull(batch, "batch");
        return new BatchHooks(
            beforeBatchMethods.getOrDefault(batch, Collections.emptyList()),
            afterBatchMethods.getOrDefault(batch, Collections.emptyList()));
    }

    private boolean matches(TestSelector selector, GameTestDefinition definition) {
        if (matches(selector, definition.getTestId(), definition.getHolderClassName())) {
            return true;
        }
        return definition.isUnresolvedCaseFamily() && isCaseSelectorForBase(selector, definition.getBaseTestId());
    }

    private static boolean matches(TestSelector selector, String testId, Method holderMethod) {
        return matches(
            selector,
            testId,
            holderMethod == null ? ""
                : holderMethod.getDeclaringClass()
                    .getName());
    }

    private static boolean matches(TestSelector selector, String testId, String holderClassName) {
        if (selector.type() == SelectorType.TEST_ID_PREFIX) {
            return testId.startsWith(selector.value());
        }
        if (testId.startsWith(selector.value() + ":")) {
            return true;
        }
        if (holderClassName == null || holderClassName.isEmpty()) {
            return false;
        }
        int nested = holderClassName.lastIndexOf('$');
        int separator = nested >= 0 ? nested : holderClassName.lastIndexOf('.');
        String simpleName = separator >= 0 ? holderClassName.substring(separator + 1) : holderClassName;
        String canonicalName = holderClassName.replace('$', '.');
        return simpleName.equals(selector.value()) || canonicalName.equals(selector.value())
            || holderClassName.equals(selector.value());
    }

    private InvalidTestDefinition matchingInvalid(TestSelector selector) {
        for (InvalidTestDefinition invalidTest : diagnostics.invalidTests()) {
            if (matches(selector, invalidTest.intendedTestId(), invalidTest.method())) {
                return invalidTest;
            }
            Method method = invalidTest.method();
            if (method != null && method.getAnnotation(MethodSource.class) != null
                && isCaseSelectorForBase(selector, invalidTest.intendedTestId())) {
                return invalidTest;
            }
        }
        return null;
    }

    private boolean matchesDuplicate(TestSelector selector) {
        for (DuplicateTestId duplicateId : diagnostics.duplicateIds()) {
            if (matches(selector, duplicateId.testId(), "")) {
                return true;
            }
            if (duplicateId.parameterized() && isCaseSelectorForBase(selector, duplicateId.testId())) {
                return true;
            }
            for (String holderClassName : duplicateId.holderClassNames()) {
                if (matches(selector, duplicateId.testId(), holderClassName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isCaseSelectorForBase(TestSelector selector, String baseTestId) {
        return selector.type() == SelectorType.TEST_ID_PREFIX && selector.value()
            .startsWith(baseTestId + "[");
    }

    private static SelectionIssue unmatchedIssue(TestSelector selector, InvalidTestDefinition matchedInvalid,
        boolean matchedDuplicate) {
        boolean invalid = matchedInvalid != null;
        String selectorKind = selector.type() == SelectorType.NAMESPACE_OR_HOLDER ? "namespace or holder"
            : "test id prefix";
        String issueKind;
        String diagnosticKind;
        String message;
        if (invalid && matchedDuplicate) {
            issueKind = "excludedOnly";
            diagnosticKind = "EXCLUDED_TEST_SELECTION";
            message = "The " + selectorKind
                + " selector '"
                + selector.value()
                + "' matched only tests excluded during discovery; fix the discovery diagnostics before selecting it.";
        } else if (invalid) {
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
        message = appendDiscoveryDiagnostics(message, matchedInvalid);

        String selectorType = selector.type() == SelectorType.NAMESPACE_OR_HOLDER ? "namespace" : "test";
        return new SelectionIssue(
            "selection:" + issueKind + ":" + selectorType + ":" + selector.value(),
            diagnosticKind,
            selector.value(),
            message);
    }

    private static String appendDiscoveryDiagnostics(String message, InvalidTestDefinition invalidTest) {
        if (invalidTest == null || invalidTest.issues()
            .isEmpty()) {
            return message;
        }
        StringBuilder detailed = new StringBuilder(message).append(" Discovery diagnostic");
        if (invalidTest.issues()
            .size() > 1) {
            detailed.append('s');
        }
        detailed.append(": ");
        for (int i = 0; i < invalidTest.issues()
            .size(); i++) {
            if (i > 0) detailed.append(" | ");
            detailed.append(
                invalidTest.issues()
                    .get(i)
                    .message());
        }
        return detailed.toString();
    }

    private static <T> List<T> immutableList(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(source, "source")));
    }

    private static Map<String, List<Method>> immutableHookMap(Map<String, List<Method>> source) {
        Map<String, List<Method>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<Method>> entry : Objects.requireNonNull(source, "source")
            .entrySet()) {
            copy.put(entry.getKey(), immutableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    @Desugar
    public record DiscoveryDiagnostics(List<InvalidTestDefinition> invalidTests, List<InvalidBatchHook> invalidHooks,
        List<DuplicateTestId> duplicateIds, List<DiscoveryIssue> issues) {

        public DiscoveryDiagnostics {
            invalidTests = immutableList(invalidTests);
            invalidHooks = immutableList(invalidHooks);
            duplicateIds = immutableList(duplicateIds);
            issues = immutableList(issues);
        }
    }

    @Desugar
    record BatchHooks(List<Method> beforeMethods, List<Method> afterMethods) {

    }
}
