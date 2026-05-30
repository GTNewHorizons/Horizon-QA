package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.HorizonQAProperties.SelectorType;
import com.gtnewhorizons.horizonqa.HorizonQAProperties.TestSelector;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;

public class GameTestSelectionTest {

    @Test
    public void selectsAllValidTestsWhenSelectorsAreAbsent() throws Exception {
        List<GameTestDefinition> validTests = Arrays
            .asList(definition("moda:Suite.first"), definition("modb:Suite.second"));

        GameTestSelection selection = GameTestSelection
            .from(validTests, Collections.emptyList(), Collections.emptyList(), true, Collections.emptyList());

        assertEquals(validTests, selection.selectedTests());
        assertTrue(
            selection.infrastructureIssues()
                .isEmpty());
    }

    @Test
    public void selectorsDeduplicateAndPreserveDiscoveryOrder() throws Exception {
        List<GameTestDefinition> validTests = Arrays
            .asList(definition("moda:Suite.first"), definition("moda:Suite.extra"), definition("modb:Suite.second"));
        List<TestSelector> selectors = Arrays.asList(
            new TestSelector(SelectorType.EXACT_TEST_ID, "modb:Suite.second"),
            new TestSelector(SelectorType.NAMESPACE, "moda"),
            new TestSelector(SelectorType.EXACT_TEST_ID, "moda:Suite.first"));

        GameTestSelection selection = GameTestSelection
            .from(validTests, Collections.emptyList(), Collections.emptyList(), false, selectors);

        assertEquals(validTests, selection.selectedTests());
        assertTrue(
            selection.infrastructureIssues()
                .isEmpty());
    }

    @Test
    public void unmatchedSelectorsDescribeWhyNothingValidMatched() throws Exception {
        Method method = DummyTests.class.getMethod("test", GameTestHelper.class);
        List<GameTestDefinition> validTests = Collections.singletonList(definition("moda:Suite.first"));
        List<InvalidTestDefinition> invalidTests = Collections
            .singletonList(new InvalidTestDefinition("bad:Broken.test", method, Collections.emptyList()));
        List<DuplicateTestId> duplicateIds = Collections
            .singletonList(new DuplicateTestId("dupe:Suite.same", Collections.singletonList(method)));
        List<TestSelector> selectors = Arrays.asList(
            new TestSelector(SelectorType.NAMESPACE, "missing"),
            new TestSelector(SelectorType.EXACT_TEST_ID, "bad:Broken.test"),
            new TestSelector(SelectorType.NAMESPACE, "dupe"));

        GameTestSelection selection = GameTestSelection.from(validTests, invalidTests, duplicateIds, false, selectors);

        assertEquals(Collections.emptyList(), selection.selectedTests());
        assertEquals(
            3,
            selection.infrastructureIssues()
                .size());
        assertEquals(
            "UNMATCHED_SELECTOR",
            selection.infrastructureIssues()
                .get(0)
                .kind());
        assertEquals(
            "INVALID_TEST_SELECTION",
            selection.infrastructureIssues()
                .get(1)
                .kind());
        assertEquals(
            "DUPLICATE_TEST_SELECTION",
            selection.infrastructureIssues()
                .get(2)
                .kind());
    }

    @Test
    public void repeatedUnmatchedSelectorsEmitOneIssue() throws Exception {
        List<TestSelector> selectors = Arrays.asList(
            new TestSelector(SelectorType.NAMESPACE, "missing"),
            new TestSelector(SelectorType.NAMESPACE, "missing"));

        GameTestSelection selection = GameTestSelection
            .from(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false, selectors);

        assertEquals(
            1,
            selection.infrastructureIssues()
                .size());
    }

    @Test
    public void noSelectedTestsDiagnosticIsSpecific() {
        GameTestSelection.SelectionIssue issue = GameTestSelection.noSelectedTests(true);

        assertEquals("selection:noTestsSelected", issue.id());
        assertEquals("NO_TESTS_SELECTED", issue.kind());
        assertEquals("<all valid tests>", issue.selector());
    }

    private static GameTestDefinition definition(String testId) throws Exception {
        return new GameTestDefinition(
            testId,
            DummyTests.class.getMethod("test", GameTestHelper.class),
            "",
            20,
            "",
            true,
            0);
    }

    public static final class DummyTests {

        public static void test(GameTestHelper helper) {}
    }
}
