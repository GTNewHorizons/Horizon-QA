package com.gtnewhorizons.horizonqa.internal;

import java.util.ArrayDeque;
import java.util.Deque;

public class GameTestSequence {

    private final GameTestInstance instance;
    private final Deque<SequenceEvent> events = new ArrayDeque<>();
    private long currentScheduledTick = 0;
    private long lastScheduledTick = -1;
    private TestPhase lastPhase = null;
    private boolean hasEvents = false;

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
        return addEvent(TestPhase.START, action, false);
    }

    public GameTestSequence thenExecuteAtEnd(Runnable action) {
        return addEvent(TestPhase.END, action, false);
    }

    public GameTestSequence thenExecuteFor(int ticks, Runnable action) {
        return thenExecuteForAtEnd(ticks, action);
    }

    public GameTestSequence thenExecuteForAtStart(int ticks, Runnable action) {
        for (int i = 0; i < ticks; i++) {
            addEvent(TestPhase.START, action, false);
            if (i + 1 < ticks) thenIdle(1);
        }
        return this;
    }

    public GameTestSequence thenExecuteForAtEnd(int ticks, Runnable action) {
        for (int i = 0; i < ticks; i++) {
            addEvent(TestPhase.END, action, false);
            if (i + 1 < ticks) thenIdle(1);
        }
        return this;
    }

    public GameTestSequence thenWaitUntil(Runnable condition) {
        return thenWaitUntilAtEnd(condition);
    }

    public GameTestSequence thenWaitUntil(int maxTicks, Runnable condition) {
        return thenWaitUntilAtEnd(maxTicks, condition);
    }

    public GameTestSequence thenWaitUntilAtStart(Runnable condition) {
        return addEvent(TestPhase.START, condition, true);
    }

    public GameTestSequence thenWaitUntilAtEnd(Runnable condition) {
        return addEvent(TestPhase.END, condition, true);
    }

    public GameTestSequence thenWaitUntilAtStart(int maxTicks, Runnable condition) {
        addEvent(TestPhase.START, condition, true);
        advanceAfterBoundedWait(maxTicks);
        return this;
    }

    public GameTestSequence thenWaitUntilAtEnd(int maxTicks, Runnable condition) {
        addEvent(TestPhase.END, condition, true);
        advanceAfterBoundedWait(maxTicks);
        return this;
    }

    public void thenSucceed() {
        addEvent(TestPhase.END, instance::succeed, false);
    }

    public void thenFail(String message) {
        addEvent(TestPhase.END, () -> instance.fail(message), false);
    }

    private GameTestSequence addEvent(TestPhase phase, Runnable action, boolean conditional) {
        long tick = resolveEventTick(phase);
        if (lastPhase == TestPhase.END && phase == TestPhase.START && tick == lastScheduledTick) {
            throw new IllegalStateException(
                "Cannot schedule a START-phase sequence event after an END-phase event at the same tick. "
                    + "Insert thenIdle(1) before the START-phase event.");
        }
        events.add(new SequenceEvent(tick, phase, action, conditional));
        currentScheduledTick = tick;
        lastScheduledTick = tick;
        lastPhase = phase;
        hasEvents = true;
        return this;
    }

    private long resolveEventTick(TestPhase phase) {
        if (hasEvents) return Math.max(1, currentScheduledTick);
        if (currentScheduledTick <= 0) return 1;
        return phase == TestPhase.START ? currentScheduledTick + 1 : currentScheduledTick;
    }

    private void advanceAfterBoundedWait(int maxTicks) {
        currentScheduledTick += Math.max(0, maxTicks - 1);
    }

    // Breaking on phase mismatch is safe because the ordering constraint (START before END at the
    // same tick, ticks always ascending) guarantees remaining events are either same-tick later-phase
    // or a later tick — both will be processed by the matching phase call.
    void tick(long currentTick, TestPhase phase) {
        while (!events.isEmpty() && !instance.isDone()) {
            SequenceEvent head = events.peek();
            if (currentTick < head.scheduledTick) break;
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
