package com.gtnewhorizons.horizonqa.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.client.ClientTaskQueue.Phase;

public class ClientTaskQueueTest {

    @Test
    public void pressAndReleaseRunBeforeSeparateTicksAndCompleteAfterReleaseProcessing() {
        ClientTaskQueue queue = new ClientTaskQueue();
        AtomicInteger input = new AtomicInteger();
        CompletableFuture<Integer> action = queue.submit(Phase.START, () -> input.incrementAndGet())
            .thenCompose(pressed -> queue.submit(Phase.START, () -> input.decrementAndGet()))
            .thenCompose(released -> queue.submit(Phase.END, input::get));
        queue.dispatch(Phase.END);
        assertEquals(0, input.get());
        queue.dispatch(Phase.START);
        assertEquals(1, input.get());
        queue.dispatch(Phase.END);
        queue.dispatch(Phase.FRAME);
        assertFalse(action.isDone());
        assertEquals(1, input.get());
        queue.dispatch(Phase.START);
        assertEquals(0, input.get());
        assertFalse(action.isDone());
        queue.dispatch(Phase.END);
        assertEquals(Integer.valueOf(0), action.join());
    }

    @Test
    public void inputWaitsForObservedReadinessWithoutRepeatingTheClick() {
        ClientTaskQueue queue = new ClientTaskQueue();
        AtomicBoolean hovered = new AtomicBoolean();
        AtomicInteger clicks = new AtomicInteger();
        CompletableFuture<Integer> click = queue.submitWhenReady(Phase.END, hovered::get, clicks::incrementAndGet);
        queue.dispatch(Phase.END);
        queue.dispatch(Phase.END);
        assertFalse(click.isDone());
        assertEquals(0, clicks.get());
        hovered.set(true);
        queue.dispatch(Phase.END);
        queue.dispatch(Phase.END);
        assertEquals(Integer.valueOf(1), click.join());
        assertEquals(1, clicks.get());
    }

    @Test
    public void readinessExceptionFailsInsteadOfRetryingAndClosingInvalidatesWaitingInput() {
        ClientTaskQueue queue = new ClientTaskQueue();
        CompletableFuture<Void> broken = queue
            .submitWhenReady(Phase.END, () -> { throw new IllegalStateException("hit test failed"); }, () -> null);
        queue.dispatch(Phase.END);
        assertTrue(broken.isCompletedExceptionally());
        AtomicInteger clicks = new AtomicInteger();
        CompletableFuture<Integer> waiting = queue.submitWhenReady(Phase.END, () -> false, clicks::incrementAndGet);
        queue.dispatch(Phase.END);
        queue.close();
        queue.dispatch(Phase.END);
        assertTrue(waiting.isCompletedExceptionally());
        assertEquals(0, clicks.get());
    }

    @Test
    public void invalidatedWorkCannotClickTheNextScreen() {
        ClientTaskQueue previous = new ClientTaskQueue();
        AtomicInteger clicks = new AtomicInteger();
        CompletableFuture<Integer> staleClick = previous.submit(Phase.END, clicks::incrementAndGet);
        previous.close();
        previous.dispatch(Phase.END);
        assertTrue(staleClick.isCompletedExceptionally());
        assertEquals(0, clicks.get());

        ClientTaskQueue next = new ClientTaskQueue();
        CompletableFuture<Integer> currentClick = next.submit(Phase.END, clicks::incrementAndGet);
        next.dispatch(Phase.END);
        previous.dispatch(Phase.END);
        assertEquals(Integer.valueOf(1), currentClick.join());
        assertEquals(1, clicks.get());
        assertTrue(
            previous.submit(Phase.END, clicks::incrementAndGet)
                .isCompletedExceptionally());
    }

    @Test
    public void frameWorkWaitsForItsBoundaryAndRetainsSubmissionOrder() {
        ClientTaskQueue queue = new ClientTaskQueue();
        CompletableFuture<String> frame = queue.submit(Phase.FRAME, () -> "pixels");
        CompletableFuture<String> click = queue.submit(Phase.END, () -> "click");
        queue.dispatch(Phase.END);
        assertFalse(frame.isDone());
        assertFalse(click.isDone());
        queue.dispatch(Phase.FRAME);
        assertEquals("pixels", frame.join());
        assertFalse(click.isDone());
        queue.dispatch(Phase.END);
        assertEquals("click", click.join());
    }
}
