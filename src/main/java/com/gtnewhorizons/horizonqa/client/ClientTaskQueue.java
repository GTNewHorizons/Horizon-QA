package com.gtnewhorizons.horizonqa.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Serial client work with a test lifetime and explicit tick/frame dispatch. No Minecraft class loading. */
public final class ClientTaskQueue {

    public enum Phase {
        START,
        END,
        FRAME
    }

    private final Deque<Task<?>> pending = new ArrayDeque<>();
    private boolean closed;

    public synchronized <T> CompletableFuture<T> submit(Phase phase, Callable<T> action) {
        return submitWhenReady(phase, () -> true, action);
    }

    public synchronized <T> CompletableFuture<T> submitWhenReady(Phase phase, BooleanSupplier ready,
        Callable<T> action) {
        Task<T> task = new Task<>(phase, ready, action);
        if (closed) task.reject();
        else pending.add(task);
        return task.result;
    }

    /** Invalidates unclaimed work. A claimed action may finish, so teardown must run on its client thread. */
    public synchronized void close() {
        closed = true;
        while (!pending.isEmpty()) pending.remove()
            .reject();
    }

    public void dispatch(Phase phase) {
        Task<?> task;
        synchronized (this) {
            task = pending.peek();
            if (closed || task == null || task.phase != phase) return;
            pending.remove();
        }
        if (!task.execute()) {
            synchronized (this) {
                if (closed) task.reject();
                else pending.addFirst(task);
            }
        }
    }

    private static final class Task<T> {

        final Phase phase;
        final BooleanSupplier ready;
        final Callable<T> action;
        final CompletableFuture<T> result = new CompletableFuture<>();

        Task(Phase phase, BooleanSupplier ready, Callable<T> action) {
            this.phase = phase;
            this.ready = ready;
            this.action = action;
        }

        void reject() {
            result.completeExceptionally(new IllegalStateException("Client test has ended"));
        }

        boolean execute() {
            try {
                if (!ready.getAsBoolean()) return false;
                result.complete(action.call());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
            return true;
        }
    }
}
