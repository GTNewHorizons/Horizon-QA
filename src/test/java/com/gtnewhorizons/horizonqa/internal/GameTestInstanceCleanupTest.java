package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;
import com.gtnewhorizons.horizonqa.report.CaseResult;
import com.gtnewhorizons.horizonqa.report.RunResult;

public class GameTestInstanceCleanupTest {

    @Test
    public void fatalAsynchronousCleanupRestoresServerStateAndRethrowsOriginalError() throws Exception {
        GameTestInstance instance = instance("mod:CleanupTests.succeeds", "succeeds");
        CompletableFuture<Void> cleanup = new CompletableFuture<>();
        AtomicInteger restorations = new AtomicInteger();
        InternalError fatal = new InternalError("asynchronous cleanup failed fatally");
        instance.addAsyncCleanup(3, () -> cleanup);
        instance.addCleanup(restorations::incrementAndGet);
        instance.start(null);
        cleanup.completeExceptionally(fatal);

        assertSame(fatal, assertThrows(InternalError.class, instance::tickEnd));
        assertEquals(1, restorations.get());
        assertTrue(instance.isDone());
        assertEquals(GameTestStatus.ERROR, instance.getStatus());
        assertSame(fatal, instance.getCleanupFailureCause());
    }

    @Test
    public void fatalRestorationStillFinalizesTheInstanceAndRunsRemainingCleanup() throws Exception {
        GameTestInstance instance = instance("mod:CleanupTests.succeeds", "succeeds");
        InternalError fatal = new InternalError("server restoration failed fatally");
        AtomicInteger restorations = new AtomicInteger();
        instance.addCleanup(() -> { throw fatal; });
        instance.addCleanup(restorations::incrementAndGet);

        assertSame(fatal, assertThrows(InternalError.class, () -> instance.start(null)));
        assertEquals(1, restorations.get());
        assertTrue(instance.isDone());
        assertEquals(GameTestStatus.ERROR, instance.getStatus());
        assertSame(fatal, instance.getCleanupFailureCause());
    }

    @Test
    public void asynchronousTeardownCompletesBeforeServerCleanupAndResult() throws Exception {
        GameTestInstance instance = instance("mod:CleanupTests.cleanupFails", "cleanupFails");
        CompletableFuture<Void> clientTeardown = new CompletableFuture<>();
        AtomicInteger cleaned = new AtomicInteger();
        instance.addAsyncCleanup(3, () -> clientTeardown);
        instance.addCleanup(cleaned::incrementAndGet);
        instance.start(null);
        assertFalse(instance.isDone());
        assertEquals(0, cleaned.get());
        clientTeardown.complete(null);
        assertFalse(instance.isDone());
        assertEquals(0, cleaned.get());
        instance.tickEnd();
        assertTrue(instance.isDone());
        assertEquals(1, cleaned.get());
    }

    @Test
    public void unresponsiveTeardownProducesInfrastructureFailure() throws Exception {
        GameTestInstance instance = instance("mod:CleanupTests.cleanupFails", "cleanupFails");
        CompletableFuture<Void> physicalCleanup = new CompletableFuture<>();
        instance.addAsyncCleanup(2, () -> physicalCleanup);
        instance.start(null);
        instance.tickEnd();
        assertFalse(instance.isDone());
        instance.tickEnd();
        assertTrue(instance.isDone());
        assertEquals(GameTestStatus.ERROR, instance.getStatus());
        assertFalse(physicalCleanup.isDone());
        assertTrue(
            instance.getCleanupFailureCause()
                .getMessage()
                .contains("within 2 ticks"));
    }

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
        assertTrue(
            resultCase.failureTrace()
                .contains("assertion broke"));
        assertTrue(outputContains(resultCase, "Assertion failed"));
        assertTrue(outputContains(resultCase, "assertion broke"));
        assertFalse(resultCase.failedRequiredCase());

        RunResult result = RunResult
            .completedCases("ci", Collections.singletonList(resultCase), Collections.emptyList(), "TEST.xml");
        assertEquals(2, result.exitCode());
        assertEquals(0, result.requiredFailures());
    }

    @Test
    public void abortedExecutionRemainsPrimaryWhenCleanupAlsoFails() throws Exception {
        GameTestInstance instance = instance("mod:CleanupTests.abortThenCleanupFails", "abortThenCleanupFails");
        instance.start(null);

        instance.abortExecution("execution stopped", null);

        assertEquals(GameTestStatus.ERROR, instance.getStatus());
        assertTrue(instance.getFailureCause() instanceof GameTestInfrastructureException);
        assertEquals("EXECUTION_ABORTED", ((GameTestInfrastructureException) instance.getFailureCause()).kind());
        assertEquals(
            "cleanup broke during abort",
            instance.getCleanupFailureCause()
                .getMessage());

        CaseResult resultCase = CaseResult.from(instance);
        assertEquals("EXECUTION_ABORTED", resultCase.failureType());
        assertEquals("execution stopped", resultCase.failureMessage());
        assertTrue(
            resultCase.failureTrace()
                .contains("cleanup broke during abort"));
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

        public static void succeeds(GameTestHelper helper) {
            helper.succeed();
        }

        public static void cleanupFails(GameTestHelper helper) {
            helper.afterTest(() -> { throw new AssertionError("cleanup broke"); });
            helper.succeed();
        }

        public static void assertionThenCleanupFails(GameTestHelper helper) {
            helper.afterTest(() -> { throw new AssertionError("cleanup broke after assertion"); });
            helper.fail("assertion broke");
        }

        public static void abortThenCleanupFails(GameTestHelper helper) {
            helper.afterTest(() -> { throw new AssertionError("cleanup broke during abort"); });
        }
    }
}
