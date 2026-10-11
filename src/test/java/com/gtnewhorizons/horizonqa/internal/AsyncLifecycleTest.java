package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;
import com.gtnewhorizons.horizonqa.client.ClientTaskQueue;
import com.gtnewhorizons.horizonqa.client.ClientTaskQueue.Phase;
import com.gtnewhorizons.horizonqa.report.CaseResult;

public class AsyncLifecycleTest {

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
        assertEquals(null, sequence.getActiveStep());
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
        GameTestInstance instance = instance();
        List<String> ordering = new ArrayList<>();
        CompletableFuture<Void> cleanup = new CompletableFuture<>();
        instance.addAsyncCleanup(3, () -> cleanup);
        instance.addCleanup(() -> ordering.add("server"));
        instance.start(null);
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
        GameTestInstance instance = instance();
        CompletableFuture<Void> cleanup = new CompletableFuture<>();
        AtomicInteger restoration = new AtomicInteger();
        instance.addAsyncCleanup(2, () -> cleanup);
        instance.addCleanup(restoration::incrementAndGet);
        instance.start(null);
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
        GameTestInstance instance = instance();
        CompletableFuture<Void> cleanup = new CompletableFuture<>();
        AtomicInteger restoration = new AtomicInteger();
        instance.addAsyncCleanup(2, () -> cleanup);
        instance.addCleanup(restoration::incrementAndGet);
        instance.start(null);
        instance.succeed();
        LinkageError fatal = new LinkageError("fatal asynchronous cleanup");
        cleanup.completeExceptionally(fatal);
        assertSame(fatal, assertThrows(LinkageError.class, instance::tickEnd));
        assertEquals(1, restoration.get());
        assertTrue(instance.isDone());
        assertEquals(GameTestStatus.ERROR, instance.getStatus());
    }

    private static GameTestInstance instance() throws Exception {
        return new GameTestInstance(
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
    }

    @Test
    public void terminalShutdownInterruptsAnOrderlyAbortAndRetainsPhysicalCleanupPromise() throws Exception {
        GameTestInstance instance = instance();
        CompletableFuture<Void> physicalCleanup = new CompletableFuture<>();
        AtomicInteger restoration = new AtomicInteger();
        instance.addAsyncCleanup(10, () -> physicalCleanup.thenApply(ignored -> null));
        instance.addCleanup(restoration::incrementAndGet);
        instance.start(null);
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
