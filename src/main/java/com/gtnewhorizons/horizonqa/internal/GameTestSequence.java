package com.gtnewhorizons.horizonqa.internal;

import java.util.ArrayDeque;
import java.util.Deque;

public class GameTestSequence {

    private final GameTestInstance instance;
    private final Deque<SequenceEvent> events = new ArrayDeque<>();
    private long currentScheduledTick = 0;
    private long lastScheduledTick = -1;
    private TestPhase lastPhase = null;

    public GameTestSequence(GameTestInstance instance) {
        this.instance = instance;
    }

    public GameTestSequence thenIdle(int ticks) {
        currentScheduledTick += ticks;
        return this;
    }

    public GameTestSequence thenExecute(Runnable action) {
        return thenExecuteAtEnd(action);
    }

    public GameTestSequence thenExecuteAtStart(Runnable action) {
        return addEvent(currentScheduledTick, TestPhase.START, action, false);
    }

    public GameTestSequence thenExecuteAtEnd(Runnable action) {
        return addEvent(currentScheduledTick, TestPhase.END, action, false);
    }

    public GameTestSequence thenExecuteFor(int ticks, Runnable action) {
        return thenExecuteForAtEnd(ticks, action);
    }

    public GameTestSequence thenExecuteForAtStart(int ticks, Runnable action) {
        for (int i = 0; i < ticks; i++) {
            addEvent(currentScheduledTick + i, TestPhase.START, action, false);
        }
        currentScheduledTick += ticks;
        return this;
    }

    public GameTestSequence thenExecuteForAtEnd(int ticks, Runnable action) {
        for (int i = 0; i < ticks; i++) {
            addEvent(currentScheduledTick + i, TestPhase.END, action, false);
        }
        currentScheduledTick += ticks;
        return this;
    }

    public GameTestSequence thenWaitUntil(Runnable condition) {
        return thenWaitUntilAtEnd(condition);
    }

    public GameTestSequence thenWaitUntil(int maxTicks, Runnable condition) {
        return thenWaitUntilAtEnd(maxTicks, condition);
    }

    public GameTestSequence thenWaitUntilAtStart(Runnable condition) {
        return addEvent(currentScheduledTick, TestPhase.START, condition, true);
    }

    public GameTestSequence thenWaitUntilAtEnd(Runnable condition) {
        return addEvent(currentScheduledTick, TestPhase.END, condition, true);
    }

    public GameTestSequence thenWaitUntilAtStart(int maxTicks, Runnable condition) {
        addEvent(currentScheduledTick, TestPhase.START, condition, true);
        currentScheduledTick += maxTicks;
        return this;
    }

    public GameTestSequence thenWaitUntilAtEnd(int maxTicks, Runnable condition) {
        addEvent(currentScheduledTick, TestPhase.END, condition, true);
        currentScheduledTick += maxTicks;
        return this;
    }

    public void thenSucceed() {
        addEvent(currentScheduledTick, TestPhase.END, instance::succeed, false);
    }

    public void thenFail(String message) {
        addEvent(currentScheduledTick, TestPhase.END, () -> instance.fail(message), false);
    }

    private GameTestSequence addEvent(long tick, TestPhase phase, Runnable action, boolean conditional) {
        if (lastPhase == TestPhase.END && phase == TestPhase.START && tick == lastScheduledTick) {
            throw new IllegalStateException(
                "Cannot schedule a START-phase sequence event after an END-phase event at the same tick. "
                    + "Insert thenIdle(1) before the START-phase event.");
        }
        events.add(new SequenceEvent(tick, phase, action, conditional));
        lastScheduledTick = tick;
        lastPhase = phase;
        return this;
    }

    // Breaking on phase mismatch is safe because the ordering constraint (START before END at the
    // same tick, ticks always ascending) guarantees remaining events are either same-tick later-phase
    // or a later tick — both will be processed by the matching phase call.
    void tick(long sequenceTick, TestPhase phase) {
        while (!events.isEmpty() && !instance.isDone()) {
            SequenceEvent head = events.peek();
            if (sequenceTick < head.scheduledTick) break;
            if (head.phase != phase) break;

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
        final TestPhase phase;
        final Runnable action;
        final boolean conditional;

        SequenceEvent(long scheduledTick, TestPhase phase, Runnable action, boolean conditional) {
            this.scheduledTick = scheduledTick;
            this.phase = phase;
            this.action = action;
            this.conditional = conditional;
        }
    }
}
