package com.gtnewhorizons.horizonqa.internal;

import java.util.ArrayList;
import java.util.List;

public class GameTestRunner {

    private static GameTestRunner activeRunner;

    private final List<GameTestInstance> instances = new ArrayList<>();
    private Runnable onAllDone;
    private Runnable onFirstTick;
    private boolean running = false;

    public void run(List<GameTestInstance> batch, Runnable onComplete) {
        instances.clear();
        instances.addAll(batch);
        onAllDone = onComplete;
        if (batch.isEmpty()) {
            running = false;
            if (onComplete != null) onComplete.run();
        } else {
            running = true;
        }
    }

    public void addInstance(GameTestInstance inst) {
        instances.add(inst);
        running = true;
    }

    public void scheduleOnFirstTick(Runnable action) {
        onFirstTick = action;
        running = true;
    }

    public static void handleTickStart() {
        GameTestRunner runner = activeRunner;
        if (runner != null) {
            runner.doTickStart();
        }
    }

    public static void handleTickEnd() {
        GameTestRunner runner = activeRunner;
        if (runner != null) {
            runner.doTickEnd();
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
            running = false;
            Runnable callback = onAllDone;
            onAllDone = null;
            callback.run();
        } else if (allDone && !instances.isEmpty()) {
            instances.clear();
            running = false;
        }
    }

    public void register() {
        activeRunner = this;
    }

    public void unregister() {
        if (activeRunner == this) {
            activeRunner = null;
        }
    }
}
