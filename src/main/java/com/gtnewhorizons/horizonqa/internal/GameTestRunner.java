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
    private Runnable manualCleanup;
    private Runnable manualTick;

    /** Retains exclusive execution until explicit close, setup failure or server shutdown. */
    public boolean tryStartManual(Runnable setup, Runnable cleanup) {
        Objects.requireNonNull(setup, "setup");
        Objects.requireNonNull(cleanup, "cleanup");
        return tryStart(Kind.MANUAL, () -> {
            manualCleanup = cleanup;
            setup.run();
        });
    }

    /** Releases a manual laboratory and runs its registered cleanup exactly once. */
    public void closeManual() {
        if (kind == Kind.MANUAL) abortAndRelease("Manual session closed", null);
    }

    /** Runs laboratory supply work after each normal world tick while this session remains open. */
    public void onManualTick(Runnable action) {
        requireOwnership();
        if (kind != Kind.MANUAL || aborting) throw new IllegalStateException("No open manual session");
        manualTick = Objects.requireNonNull(action, "action");
    }

    public static void closeActiveManual() {
        GameTestRunner runner = activeRunner();
        if (runner != null) runner.closeManual();
    }

    public static synchronized boolean isManualActive() {
        return activeRunner != null && activeRunner.kind == Kind.MANUAL;
    }

    boolean tryStart(Kind requestedKind, Runnable startWork) {
        Objects.requireNonNull(requestedKind, "requestedKind");
        Objects.requireNonNull(startWork, "startWork");
        if (!tryAcquire(requestedKind)) return false;

        try {
            startWork.run();
            releaseIfIdle();
            return true;
        } catch (RuntimeException | Error e) {
            abortAfterFailure("Execution startup failed", e);
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
        ReportedRun.pollAbortRequest();
        GameTestRunner runner = activeRunner();
        if (runner == null) return;
        try {
            runner.doTickStart();
            runner.releaseIfIdle();
        } catch (RuntimeException | Error e) {
            runner.abortAfterFailure("Execution failed during the START phase", e);
            throw e;
        }
    }

    public static void handleTickEnd() {
        ReportedRun.pollAbortRequest();
        GameTestRunner runner = activeRunner();
        if (runner == null) return;
        try {
            runner.doTickEnd();
            ReportedRun.updateProgress();
            runner.releaseIfIdle();
        } catch (RuntimeException | Error e) {
            runner.abortAfterFailure("Execution failed during the END phase", e);
            throw e;
        }
    }

    public static synchronized boolean isBatchActive() {
        return activeRunner != null && activeRunner.kind == Kind.BATCH;
    }

    public static boolean isTurboActive() {
        return tickMultiplier() > 1;
    }

    /** Effective server-loop rate, read on the server thread between full world ticks */
    public static int tickMultiplier() {
        GameTestRunner runner = activeRunner();
        if (runner == null || runner.kind != Kind.BATCH || !HorizonQAProperties.usesHeadlessServerBehavior()) return 1;
        int configured = HorizonQAProperties.turboMultiplier();
        if (configured > 1) return configured;
        int multiplier = HorizonQAProperties.MAX_TURBO_MULTIPLIER;
        boolean hasActiveTest = false;
        for (GameTestInstance instance : runner.instances) {
            if (instance.isDone()) continue;
            hasActiveTest = true;
            multiplier = Math.min(multiplier, instance.tickMultiplier());
        }
        return hasActiveTest ? multiplier : 1;
    }

    public static void shutdown() {
        GameTestRunner runner = activeRunner();
        if (runner != null) {
            runner.abortAndRelease("Server stopped before test completion", null);
        }
    }

    /** Keeps execution owned while asynchronous cleanup is polled by normal END ticks */
    void abortIfActive(String message, Throwable cause, Runnable afterCleanup) {
        if (activeRunner != this) {
            afterCleanup.run();
            return;
        }
        if (aborting) return;
        aborting = true;
        onFirstTick = null;
        onAllDone = afterCleanup;
        running = true;
        for (GameTestInstance instance : instances) {
            instance.abortExecution(message, cause);
        }
        if (instances.isEmpty()) {
            doTickEnd();
            releaseIfIdle();
        }
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
        if (manualTick != null) manualTick.run();
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
        if (!running && onFirstTick == null && manualCleanup == null) {
            release();
        }
    }

    private void abortAfterFailure(String message, Throwable failure) {
        try {
            abortAndRelease(message, failure);
        } catch (RuntimeException | Error cleanupFailure) {
            if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
        }
    }

    /** Terminal shutdown cannot rely on further server ticks to settle cleanup */
    void abortAndRelease(String message, Throwable cause) {
        if (activeRunner != this) return;
        if (aborting && instances.isEmpty()) return;
        aborting = true;
        List<GameTestInstance> aborted = new ArrayList<>(instances);
        instances.clear();
        onAllDone = null;
        onFirstTick = null;
        running = false;
        manualTick = null;
        Throwable abortFailure = null;
        try {
            for (GameTestInstance instance : aborted) {
                try {
                    instance.abortExecution(message, cause);
                    instance.interruptCleanup(message);
                } catch (Throwable failure) {
                    if (abortFailure == null) {
                        abortFailure = failure;
                    } else if (failure != abortFailure) {
                        abortFailure.addSuppressed(failure);
                    }
                }
            }
        } finally {
            Runnable cleanup = manualCleanup;
            manualCleanup = null;
            try {
                if (cleanup != null) cleanup.run();
            } finally {
                release();
            }
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
        INTERACTIVE,
        MANUAL
    }
}
