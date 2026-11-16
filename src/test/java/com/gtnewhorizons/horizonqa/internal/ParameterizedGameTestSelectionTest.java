package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.HorizonQAProperties.SelectorType;
import com.gtnewhorizons.horizonqa.HorizonQAProperties.TestSelector;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.MethodSource;

public class ParameterizedGameTestSelectionTest {

    @Test
    public void exactCaseSelectorDiagnosesInvalidParameterizedBase() throws Exception {
        String baseTestId = "matrix:BrokenTests.voltage";
        Method method = TestDefinitions.class.getMethod("test", GameTestHelper.class, int.class);
        InvalidTestDefinition invalid = new InvalidTestDefinition(
            baseTestId,
            method,
            Collections.singletonList(
                new DiscoveryIssue(
                    "discovery:invalidTest:matrix:BrokenTests.voltage:methodSource",
                    "DISCOVERY_ERROR",
                    "method source 'missingRows' was not found in the test holder")));

        GameTestSelection selection = catalog(
            Collections.emptyList(),
            Collections.singletonList(invalid),
            Collections.emptyList()).select(
                false,
                Collections.singletonList(new TestSelector(SelectorType.TEST_ID_PREFIX, baseTestId + "[lv]")));

        assertTrue(
            selection.selectedTests()
                .isEmpty());
        assertEquals(
            1,
            selection.infrastructureIssues()
                .size());
        assertEquals(
            "INVALID_TEST_SELECTION",
            selection.infrastructureIssues()
                .get(0)
                .kind());
        assertTrue(
            selection.infrastructureIssues()
                .get(0)
                .message()
                .contains("method source 'missingRows' was not found"));
    }

    @Test
    public void exactCaseSelectorSelectsDiscoverySkippedBasePlaceholder() {
        String baseTestId = "matrix:OptionalTests.voltage";
        GameTestDefinition skipped = GameTestDefinition.parameterizedSkippedAtDiscovery(
            baseTestId,
            "example.compat.OptionalTests",
            "",
            20,
            "",
            true,
            0,
            "Required mod is not loaded: optionalmod");

        GameTestSelection selection = catalog(
            Collections.singletonList(skipped),
            Collections.emptyList(),
            Collections.emptyList()).select(
                false,
                Collections.singletonList(new TestSelector(SelectorType.TEST_ID_PREFIX, baseTestId + "[lv]")));

        assertEquals(Collections.singletonList(skipped), selection.selectedTests());
        assertTrue(
            selection.infrastructureIssues()
                .isEmpty());
    }

    @Test
    public void exactCaseSelectorDiagnosesDuplicateParameterizedBase() {
        String baseTestId = "matrix:DuplicateTests.voltage";
        DuplicateTestId duplicate = new DuplicateTestId(
            baseTestId,
            Collections.emptyList(),
            Collections.emptyList(),
            true);

        GameTestSelection selection = catalog(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.singletonList(duplicate)).select(
                false,
                Collections.singletonList(new TestSelector(SelectorType.TEST_ID_PREFIX, baseTestId + "[lv]")));

        assertEquals(
            1,
            selection.infrastructureIssues()
                .size());
        assertEquals(
            "DUPLICATE_TEST_SELECTION",
            selection.infrastructureIssues()
                .get(0)
                .kind());
    }

    private static GameTestCatalog catalog(java.util.List<GameTestDefinition> tests,
        java.util.List<InvalidTestDefinition> invalidTests, java.util.List<DuplicateTestId> duplicateIds) {
        return new GameTestCatalog(
            tests,
            Collections.emptyMap(),
            Collections.emptyMap(),
            invalidTests,
            Collections.emptyList(),
            duplicateIds,
            Collections.emptyList());
    }

    public static final class TestDefinitions {

        @MethodSource("missingRows")
        public static void test(GameTestHelper helper, int value) {}
    }
}
