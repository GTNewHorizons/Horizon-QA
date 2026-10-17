package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;
import com.gtnewhorizons.horizonqa.client.ClientTaskQueue;
import com.gtnewhorizons.horizonqa.client.ClientTaskQueue.Phase;
import com.gtnewhorizons.horizonqa.report.CaseResult;

public class AsyncLifecycleTest {

    @Test
    public void acceleratedTimeoutStopsBeforePendingClientCleanupAndRetainsDiagnostics() throws Exception {
        CompletableFuture<Void> cleanup = new CompletableFuture<>();
        AtomicInteger restoration = new AtomicInteger();
        GameTestInstance instance = startAcceleratedWait(2, cleanup, restoration);
        instance.tickStart();
        instance.tickEnd();
        assertEquals(GameTestStatus.FAILED, instance.getStatus());
        assertEquals(1, instance.tickMultiplier());
        assertFalse(instance.isDone());
        assertTrue(
            instance.getFailureCause()
                .getMessage()
                .contains("server turbo x10"));
        cleanup.complete(null);
        instance.tickEnd();
        assertTrue(instance.isDone());
        assertEquals(1, restoration.get());
    }

    @Test
    public void failureAndAbortStopAccelerationBeforeAsynchronousCleanup() throws Exception {
        for (boolean abort : new boolean[] { false, true }) {
            CompletableFuture<Void> cleanup = new CompletableFuture<>();
            AtomicInteger restoration = new AtomicInteger();
            GameTestInstance instance = startAcceleratedWait(10, cleanup, restoration);
            if (abort) instance.abortExecution("cancel accelerated wait", null);
            else instance.fail(new IllegalStateException("per-tick failure"));
            assertEquals(abort ? GameTestStatus.ERROR : GameTestStatus.FAILED, instance.getStatus());
            assertEquals(1, instance.tickMultiplier());
            assertFalse(instance.isDone());
            cleanup.complete(null);
            instance.tickEnd();
            assertTrue(instance.isDone());
            assertEquals(1, restoration.get());
        }
    }

    private static GameTestInstance startAcceleratedWait(int budget, CompletableFuture<Void> cleanup,
        AtomicInteger restoration) throws Exception {
        GameTestInstance instance = startWithCleanup(10, cleanup, restoration::incrementAndGet);
        GameTestSequence sequence = new GameTestSequence(instance);
        instance.setSequence(sequence);
        sequence.thenWaitUntilAccelerated(
            "real simulation pending",
            budget,
            10,
            () -> { throw new AssertionError("not completed"); });
        instance.tickStart();
        instance.tickEnd();
        assertEquals(10, instance.tickMultiplier());
        return instance;
    }

    @Rule
    public Timeout nonblockingDeadline = Timeout.seconds(5);

    @After
    public void releaseExecutionOwnership() {
        GameTestRunner.shutdown();
    }

    @Test
    public void unfinishedOperationDoesNotBlockOrSubmitAgainAndContinuesOnlyAtEnd() {
        GameTestSequence sequence = new GameTestSequence(new GameTestInstance(null, 0, 0, 0));
        CompletableFuture<Void> operation = new CompletableFuture<>();
        AtomicInteger submissions = new AtomicInteger();
        AtomicInteger following = new AtomicInteger();
        sequence.thenExecuteAsync("client action", 10, () -> {
            submissions.incrementAndGet();
            return operation;
        })
            .thenExecute(following::incrementAndGet);
        sequence.tick(1, TestPhase.START);
        assertEquals(0, submissions.get());
        sequence.tick(1, TestPhase.END);
        sequence.tick(2, TestPhase.END);
        assertEquals(1, submissions.get());
        assertEquals(0, following.get());
        operation.complete(null);
        sequence.tick(3, TestPhase.START);
        assertEquals(0, following.get());
        sequence.tick(3, TestPhase.END);
        assertEquals(1, following.get());
    }

    @Test
    public void asynchronousAssertionWaitRetriesCompletedAssertionsButNotInFlightWork() {
        GameTestSequence sequence = new GameTestSequence(new GameTestInstance(null, 0, 0, 0));
        List<CompletableFuture<Void>> attempts = new ArrayList<>();
        sequence.thenWaitUntilAsync("packet visible", 5, () -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            attempts.add(future);
            return future;
        });
        sequence.tick(1, TestPhase.END);
        sequence.tick(2, TestPhase.END);
        assertEquals(1, attempts.size());
        attempts.get(0)
            .completeExceptionally(new AssertionError("not synchronized"));
        sequence.tick(3, TestPhase.END);
        sequence.tick(4, TestPhase.END);
        assertEquals(2, attempts.size());
        attempts.get(1)
            .complete(null);
        sequence.tick(5, TestPhase.END);
        assertNull(sequence.getActiveStep());
    }

    @Test
    public void asynchronousInfrastructureFailureRetainsItsOriginalClassification() {
        GameTestSequence sequence = new GameTestSequence(new GameTestInstance(null, 0, 0, 0));
        CompletableFuture<Void> future = new CompletableFuture<>();
        GameTestInfrastructureException cause = new GameTestInfrastructureException("CLIENT_INPUT", "no display");
        future.completeExceptionally(cause);
        sequence.thenWaitUntilAsync("native input", 10, () -> future);
        assertSame(cause, assertThrows(GameTestInfrastructureException.class, () -> sequence.tick(1, TestPhase.END)));
    }

    @Test
    public void inFlightTimeConsumesStepBudgetAndCancellationInvalidatesOperation() {
        GameTestSequence sequence = new GameTestSequence(new GameTestInstance(null, 0, 0, 0));
        CompletableFuture<Void> future = new CompletableFuture<>();
        sequence.thenExecuteAsync("never completed", 2, () -> future);
        sequence.tick(1, TestPhase.END);
        assertThrows(SequenceStepTimeoutException.class, () -> sequence.tick(2, TestPhase.END));
        sequence.cancelActiveOperation();
        assertTrue(future.isCancelled());
    }

    @Test
    public void queueCloseAndFutureCancellationPreventStaleActions() {
        ClientTaskQueue queue = new ClientTaskQueue();
        AtomicInteger mutations = new AtomicInteger();
        CompletableFuture<Integer> cancelled = queue.submit(Phase.END, mutations::incrementAndGet);
        cancelled.cancel(false);
        queue.dispatch(Phase.END);
        CompletableFuture<Integer> waiting = queue.submitWhenReady(Phase.END, () -> false, mutations::incrementAndGet);
        queue.dispatch(Phase.END);
        CompletableFuture<Integer> stale = queue.submit(Phase.FRAME, mutations::incrementAndGet);
        queue.close();
        queue.dispatch(Phase.END);
        queue.dispatch(Phase.FRAME);
        assertEquals(0, mutations.get());
        assertTrue(waiting.isCompletedExceptionally());
        assertTrue(stale.isCompletedExceptionally());
        assertTrue(
            queue.submit(Phase.END, mutations::incrementAndGet)
                .isCompletedExceptionally());
        ClientTaskQueue next = new ClientTaskQueue();
        next.submit(Phase.END, mutations::incrementAndGet);
        next.dispatch(Phase.END);
        assertEquals(1, mutations.get());
    }

    @Test
    public void asynchronousCleanupCompletesBeforeServerCleanupAndFinalResult() throws Exception {
        List<String> ordering = new ArrayList<>();
        CompletableFuture<Void> cleanup = new CompletableFuture<>();
        GameTestInstance instance = startWithCleanup(3, cleanup, () -> ordering.add("server"));
        instance.succeed();
        assertFalse(instance.isDone());
        instance.tickEnd();
        assertTrue(ordering.isEmpty());
        ordering.add("client");
        cleanup.complete(null);
        instance.tickEnd();
        instance.tickEnd();
        assertTrue(instance.isDone());
        assertEquals(Arrays.asList("client", "server"), ordering);
    }

    @Test
    public void cleanupTimeoutIsInfrastructureEvenForOptionalPassedTestAndStillRestoresServerState() throws Exception {
        CompletableFuture<Void> cleanup = new CompletableFuture<>();
        AtomicInteger restoration = new AtomicInteger();
        GameTestInstance instance = startWithCleanup(2, cleanup, restoration::incrementAndGet);
        instance.succeed();
        instance.tickEnd();
        instance.tickEnd();
        assertTrue(instance.isDone());
        assertTrue(cleanup.isCancelled());
        assertEquals(1, restoration.get());
        assertEquals(GameTestStatus.ERROR, instance.getStatus());
        assertEquals("CLEANUP_TIMEOUT", ((GameTestInfrastructureException) instance.getCleanupFailureCause()).kind());
        assertTrue(
            CaseResult.from(instance)
                .infrastructureError());
    }

    @Test
    public void asynchronousFatalCleanupRestoresServerStateAndRethrowsOriginalFatalError() throws Exception {
        CompletableFuture<Void> cleanup = new CompletableFuture<>();
        AtomicInteger restoration = new AtomicInteger();
        GameTestInstance instance = startWithCleanup(2, cleanup, restoration::incrementAndGet);
        instance.succeed();
        LinkageError fatal = new LinkageError("fatal asynchronous cleanup");
        cleanup.completeExceptionally(fatal);
        assertSame(fatal, assertThrows(LinkageError.class, instance::tickEnd));
        assertEquals(1, restoration.get());
        assertTrue(instance.isDone());
        assertEquals(GameTestStatus.ERROR, instance.getStatus());
    }

    private static GameTestInstance startWithCleanup(int budget, CompletableFuture<Void> cleanup, Runnable restoration)
        throws Exception {
        GameTestInstance instance = new GameTestInstance(
            new GameTestDefinition(
                "test:AsyncLifecycle.pending",
                AsyncLifecycleTest.class.getMethod("pending", GameTestHelper.class),
                "",
                100,
                "",
                false,
                0),
            0,
            0,
            0);
        instance.addAsyncCleanup(budget, () -> cleanup);
        instance.addCleanup(restoration);
        instance.start(null);
        return instance;
    }

    @Test
    public void terminalShutdownInterruptsAnOrderlyAbortAndRetainsPhysicalCleanupPromise() throws Exception {
        CompletableFuture<Void> physicalCleanup = new CompletableFuture<>();
        AtomicInteger restoration = new AtomicInteger();
        GameTestInstance instance = startWithCleanup(
            10,
            physicalCleanup.thenApply(ignored -> null),
            restoration::incrementAndGet);
        GameTestRunner runner = new GameTestRunner();
        assertTrue(runner.tryStart(GameTestRunner.Kind.BATCH, () -> runner.addInstance(instance)));
        runner.abortIfActive("cancel", null, () -> {});
        assertTrue(GameTestRunner.isBatchActive());
        GameTestRunner.shutdown();
        assertFalse(GameTestRunner.isBatchActive());
        assertTrue(instance.isDone());
        assertEquals(1, restoration.get());
        assertFalse(physicalCleanup.isDone());
    }

    public static void pending(GameTestHelper helper) {}
}
