package com.gtnewhorizons.gametest.internal;

import java.util.ArrayDeque;
import java.util.Deque;

public class GameTestSequence {

    private final GameTestInstance instance;
    private final Deque<SequenceEvent> events = new ArrayDeque<>();
    private long currentScheduledTick = 0;

    public GameTestSequence(GameTestInstance instance) {
        this.instance = instance;
    }

    public GameTestSequence thenIdle(int ticks) {
        currentScheduledTick += ticks;
        return this;
    }

    public GameTestSequence thenExecute(Runnable action) {
        events.add(new SequenceEvent(currentScheduledTick, action, false));
        return this;
    }

    public GameTestSequence thenExecuteFor(int ticks, Runnable action) {
        for (int i = 0; i < ticks; i++) {
            events.add(new SequenceEvent(currentScheduledTick + i, action, false));
        }
        currentScheduledTick += ticks;
        return this;
    }

    public GameTestSequence thenWaitUntil(Runnable condition) {
        events.add(new SequenceEvent(currentScheduledTick, condition, true));
        return this;
    }

    public GameTestSequence thenWaitUntil(int maxTicks, Runnable condition) {
        events.add(new SequenceEvent(currentScheduledTick, condition, true));
        currentScheduledTick += maxTicks;
        return this;
    }

    public void thenSucceed() {
        events.add(new SequenceEvent(currentScheduledTick, instance::succeed, false));
    }

    public void thenFail(String message) {
        events.add(new SequenceEvent(currentScheduledTick, () -> instance.fail(message), false));
    }

    void tick(long testTick) {
        while (!events.isEmpty() && !instance.isDone()) {
            SequenceEvent head = events.peek();
            if (testTick < head.scheduledTick) break;

            if (head.conditional) {
                try {
                    head.action.run();
                    events.poll();
                } catch (AssertionError e) {
                    break;
                }
            } else {
                events.poll();
                head.action.run();
            }
        }
    }

    private static final class SequenceEvent {

        final long scheduledTick;
        final Runnable action;
        final boolean conditional;

        SequenceEvent(long scheduledTick, Runnable action, boolean conditional) {
            this.scheduledTick = scheduledTick;
            this.action = action;
            this.conditional = conditional;
        }
    }
}
