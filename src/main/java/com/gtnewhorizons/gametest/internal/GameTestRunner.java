package com.gtnewhorizons.gametest.internal;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class GameTestRunner {

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

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        if (onFirstTick != null) {
            Runnable action = onFirstTick;
            onFirstTick = null;
            action.run();
        }

        if (!running) return;

        for (GameTestInstance inst : instances) {
            if (!inst.isDone()) {
                inst.tick();
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
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    public void unregister() {
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
    }
}
