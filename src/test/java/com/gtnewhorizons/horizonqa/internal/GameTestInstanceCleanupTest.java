package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.report.CaseResult;
import com.gtnewhorizons.horizonqa.report.RunResult;

public class GameTestInstanceCleanupTest {

    @Test
    public void cleanupAssertionErrorBecomesInfrastructureErrorCase() throws Exception {
        GameTestInstance instance = instance("mod:CleanupTests.cleanupFails", "cleanupFails");

        instance.start(null);

        assertEquals(GameTestStatus.ERROR, instance.getStatus());
        assertTrue(instance.isDone());
        assertNull(instance.getFailureCause());
        assertTrue(instance.getCleanupFailureCause() instanceof AssertionError);

        CaseResult resultCase = CaseResult.from(instance);
        assertEquals(CaseResult.Status.ERROR, resultCase.status());
        assertEquals(CaseResult.CLEANUP_ERROR, resultCase.failureType());
        assertEquals("cleanup broke", resultCase.failureMessage());
        assertTrue(
            resultCase.failureTrace()
                .contains("java.lang.AssertionError: cleanup broke"));
        assertTrue(resultCase.infrastructureError());
        assertFalse(resultCase.failedRequiredCase());
        assertFalse(outputContains(resultCase, "Assertion failed"));
        assertTrue(outputContains(resultCase, "error after 0 simulated tick"));

        RunResult result = RunResult
            .completedCases("ci", Collections.singletonList(resultCase), Collections.emptyList(), "TEST.xml");
        assertEquals(2, result.exitCode());
        assertEquals(0, result.junitFailures());
        assertEquals(1, result.junitErrors());
    }

    @Test
    public void assertionFailureAndCleanupFailureRemainSeparate() throws Exception {
        GameTestInstance instance = instance("mod:CleanupTests.assertionThenCleanupFails", "assertionThenCleanupFails");

        instance.start(null);

        assertEquals(GameTestStatus.ERROR, instance.getStatus());
        assertEquals(
            "assertion broke",
            instance.getFailureCause()
                .getMessage());
        assertEquals(
            "cleanup broke after assertion",
            instance.getCleanupFailureCause()
                .getMessage());

        CaseResult resultCase = CaseResult.from(instance);
        assertEquals(CaseResult.Status.ERROR, resultCase.status());
        assertEquals(CaseResult.CLEANUP_ERROR, resultCase.failureType());
        assertEquals("cleanup broke after assertion", resultCase.failureMessage());
        assertTrue(outputContains(resultCase, "Assertion failed"));
        assertTrue(outputContains(resultCase, "assertion broke"));
        assertFalse(resultCase.failedRequiredCase());

        RunResult result = RunResult
            .completedCases("ci", Collections.singletonList(resultCase), Collections.emptyList(), "TEST.xml");
        assertEquals(2, result.exitCode());
        assertEquals(0, result.requiredFailures());
    }

    private static GameTestInstance instance(String id, String methodName) throws Exception {
        Method method = TestDefinitions.class.getMethod(methodName, GameTestHelper.class);
        GameTestDefinition definition = new GameTestDefinition(id, method, "", 100, "", true, 0);
        return new GameTestInstance(definition, 0, 0, 0);
    }

    private static boolean outputContains(CaseResult resultCase, String text) {
        for (String line : resultCase.outputLines()) {
            if (line.contains(text)) {
                return true;
            }
        }
        return false;
    }

    public static final class TestDefinitions {

        public static void cleanupFails(GameTestHelper helper) {
            helper.afterTest(() -> { throw new AssertionError("cleanup broke"); });
            helper.succeed();
        }

        public static void assertionThenCleanupFails(GameTestHelper helper) {
            helper.afterTest(() -> { throw new AssertionError("cleanup broke after assertion"); });
            helper.fail("assertion broke");
        }
    }
}
