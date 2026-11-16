package com.gtnewhorizons.horizonqa.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.gtnewhorizons.horizonqa.HorizonQAProperties;

public final class GameTestRunner {

    private static GameTestRunner activeRunner;

    private final List<GameTestInstance> instances = new ArrayList<>();
    private Runnable onAllDone;
    private Runnable onFirstTick;
    private boolean aborting;
    private boolean running;
    private Kind kind;

    boolean tryStart(Kind requestedKind, Runnable startWork) {
        Objects.requireNonNull(requestedKind, "requestedKind");
        Objects.requireNonNull(startWork, "startWork");
        if (!tryAcquire(requestedKind)) return false;

        try {
            startWork.run();
            releaseIfIdle();
            return true;
        } catch (RuntimeException | Error e) {
            abortAndRelease("Execution startup failed", e);
            throw e;
        }
    }

    void run(List<GameTestInstance> batch, Runnable onComplete) {
        requireOwnership();
        instances.clear();
        instances.addAll(batch);
        onAllDone = onComplete;
        if (batch.isEmpty()) {
            running = false;
            Runnable callback = onAllDone;
            onAllDone = null;
            if (callback != null) callback.run();
        } else {
            running = true;
        }
    }

    void addInstance(GameTestInstance inst) {
        requireOwnership();
        instances.add(inst);
        running = true;
    }

    void scheduleOnFirstTick(Runnable action) {
        requireOwnership();
        onFirstTick = action;
    }

    public static void handleTickStart() {
        GameTestRunner runner = activeRunner();
        if (runner == null) return;
        try {
            runner.doTickStart();
            runner.releaseIfIdle();
        } catch (RuntimeException | Error e) {
            runner.abortAndRelease("Execution failed during the START phase", e);
            throw e;
        }
    }

    public static void handleTickEnd() {
        GameTestRunner runner = activeRunner();
        if (runner == null) return;
        try {
            runner.doTickEnd();
            runner.releaseIfIdle();
        } catch (RuntimeException | Error e) {
            runner.abortAndRelease("Execution failed during the END phase", e);
            throw e;
        }
    }

    public static synchronized boolean isBatchActive() {
        return activeRunner != null && activeRunner.kind == Kind.BATCH;
    }

    public static boolean isTurboActive() {
        return isBatchActive() && HorizonQAProperties.usesHeadlessServerBehavior()
            && HorizonQAProperties.turboMultiplier() > 1;
    }

    public static void shutdown() {
        GameTestRunner runner = activeRunner();
        if (runner != null) {
            runner.abortAndRelease("Server stopped before test completion", null);
        }
    }

    void abortIfActive(String message) {
        abortAndRelease(message, null);
    }

    void abortIfActive(String message, Throwable cause) {
        abortAndRelease(message, cause);
    }

    private void doTickStart() {
        if (onFirstTick != null) {
            Runnable action = onFirstTick;
            onFirstTick = null;
            action.run();
        }

        if (!running) return;

        for (GameTestInstance inst : instances) {
            if (!inst.isDone()) {
                inst.tickStart();
            }
        }
    }

    private void doTickEnd() {
        if (!running) return;

        for (GameTestInstance inst : instances) {
            if (!inst.isDone()) {
                inst.tickEnd();
            }
        }

        boolean allDone = true;
        for (GameTestInstance inst : instances) {
            if (!inst.isDone()) {
                allDone = false;
                break;
            }
        }

        if (allDone && onAllDone != null) {
            instances.clear();
            running = false;
            Runnable callback = onAllDone;
            onAllDone = null;
            callback.run();
        } else if (allDone && !instances.isEmpty()) {
            instances.clear();
            running = false;
        }
    }

    private boolean tryAcquire(Kind requestedKind) {
        synchronized (GameTestRunner.class) {
            if (activeRunner == null) {
                activeRunner = this;
                kind = requestedKind;
                return true;
            }
            return activeRunner == this && !aborting && kind == Kind.INTERACTIVE && requestedKind == Kind.INTERACTIVE;
        }
    }

    private void releaseIfIdle() {
        if (!running && onFirstTick == null) {
            release();
        }
    }

    private void abortAndRelease(String message, Throwable cause) {
        if (activeRunner != this || aborting) return;
        aborting = true;
        List<GameTestInstance> aborted = new ArrayList<>(instances);
        instances.clear();
        onAllDone = null;
        onFirstTick = null;
        running = false;
        Throwable abortFailure = null;
        try {
            for (GameTestInstance instance : aborted) {
                try {
                    instance.abortExecution(message, cause);
                } catch (Throwable failure) {
                    if (abortFailure == null) {
                        abortFailure = failure;
                    } else if (failure != abortFailure) {
                        abortFailure.addSuppressed(failure);
                    }
                }
            }
        } finally {
            release();
        }
        if (abortFailure instanceof RuntimeException runtime) throw runtime;
        if (abortFailure instanceof Error error) throw error;
    }

    private void release() {
        synchronized (GameTestRunner.class) {
            if (activeRunner == this) {
                activeRunner = null;
            }
            aborting = false;
            kind = null;
        }
    }

    private void requireOwnership() {
        if (activeRunner() != this) {
            throw new IllegalStateException("GameTest runner does not own execution.");
        }
    }

    private static synchronized GameTestRunner activeRunner() {
        return activeRunner;
    }

    enum Kind {
        BATCH,
        INTERACTIVE
    }
}
