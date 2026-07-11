package com.gtnewhorizons.horizonqa.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class GameTestSequence {

    private final GameTestInstance instance;
    private final Deque<SequenceStep> pendingSteps = new ArrayDeque<>();
    private final List<SequenceStep> steps = new ArrayList<>();
    private long currentScheduledTick = 0;
    private long lastScheduledTick = -1;
    private TestPhase lastPhase = null;
    private boolean hasEvents = false;

    public GameTestSequence(GameTestInstance instance) {
        this.instance = instance;
    }

    public GameTestSequence thenIdle(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must not be negative");
        currentScheduledTick += ticks;
        return this;
    }

    public GameTestSequence thenExecute(Runnable action) {
        return thenExecuteAtEnd(action);
    }

    public GameTestSequence thenExecute(String label, Runnable action) {
        return thenExecuteAtEnd(label, action);
    }

    public GameTestSequence thenExecuteAtStart(Runnable action) {
        return thenExecuteAtStart(null, action);
    }

    public GameTestSequence thenExecuteAtStart(String label, Runnable action) {
        return addStep(TestPhase.START, StepKind.EXECUTE, label, action, -1);
    }

    public GameTestSequence thenExecuteAtEnd(Runnable action) {
        return thenExecuteAtEnd(null, action);
    }

    public GameTestSequence thenExecuteAtEnd(String label, Runnable action) {
        return addStep(TestPhase.END, StepKind.EXECUTE, label, action, -1);
    }

    public GameTestSequence thenExecuteFor(int ticks, Runnable action) {
        return thenExecuteForAtEnd(ticks, action);
    }

    public GameTestSequence thenExecuteForAtStart(int ticks, Runnable action) {
        for (int i = 0; i < ticks; i++) {
            addStep(TestPhase.START, StepKind.EXECUTE, null, action, -1);
            if (i + 1 < ticks) thenIdle(1);
        }
        return this;
    }

    public GameTestSequence thenExecuteForAtEnd(int ticks, Runnable action) {
        for (int i = 0; i < ticks; i++) {
            addStep(TestPhase.END, StepKind.EXECUTE, null, action, -1);
            if (i + 1 < ticks) thenIdle(1);
        }
        return this;
    }

    public GameTestSequence thenWaitUntil(Runnable condition) {
        return thenWaitUntilAtEnd(condition);
    }

    public GameTestSequence thenWaitUntil(String label, Runnable condition) {
        return thenWaitUntilAtEnd(label, condition);
    }

    public GameTestSequence thenWaitUntil(int maxTicks, Runnable condition) {
        return thenWaitUntilAtEnd(maxTicks, condition);
    }

    public GameTestSequence thenWaitUntil(String label, int maxTicks, Runnable condition) {
        return thenWaitUntilAtEnd(label, maxTicks, condition);
    }

    public GameTestSequence thenWaitUntilAtStart(Runnable condition) {
        return thenWaitUntilAtStart(null, condition);
    }

    public GameTestSequence thenWaitUntilAtStart(String label, Runnable condition) {
        return addStep(TestPhase.START, StepKind.WAIT_UNTIL, label, condition, -1);
    }

    public GameTestSequence thenWaitUntilAtEnd(Runnable condition) {
        return thenWaitUntilAtEnd(null, condition);
    }

    public GameTestSequence thenWaitUntilAtEnd(String label, Runnable condition) {
        return addStep(TestPhase.END, StepKind.WAIT_UNTIL, label, condition, -1);
    }

    public GameTestSequence thenWaitUntilAtStart(int maxTicks, Runnable condition) {
        return thenWaitUntilAtStart(null, maxTicks, condition);
    }

    public GameTestSequence thenWaitUntilAtStart(String label, int maxTicks, Runnable condition) {
        validateMaxTicks(maxTicks);
        return addStep(TestPhase.START, StepKind.WAIT_UNTIL, label, condition, maxTicks);
    }

    public GameTestSequence thenWaitUntilAtEnd(int maxTicks, Runnable condition) {
        return thenWaitUntilAtEnd(null, maxTicks, condition);
    }

    public GameTestSequence thenWaitUntilAtEnd(String label, int maxTicks, Runnable condition) {
        validateMaxTicks(maxTicks);
        return addStep(TestPhase.END, StepKind.WAIT_UNTIL, label, condition, maxTicks);
    }

    public void thenSucceed() {
        addStep(TestPhase.END, StepKind.EXECUTE, "succeed test", instance::succeed, -1);
    }

    public void thenFail(String message) {
        addStep(TestPhase.END, StepKind.EXECUTE, "fail test", () -> instance.fail(message), -1);
    }

    /** Immutable snapshots of all sequence steps in declaration order. */
    public List<SequenceStepSnapshot> getSteps() {
        List<SequenceStepSnapshot> snapshots = new ArrayList<>(steps.size());
        for (SequenceStep step : steps) {
            snapshots.add(step.snapshot(steps.size()));
        }
        return Collections.unmodifiableList(snapshots);
    }

    /** The blocking or next scheduled step, or {@code null} after the sequence has completed. */
    public SequenceStepSnapshot getActiveStep() {
        SequenceStep step = pendingSteps.peek();
        return step != null ? step.snapshot(steps.size()) : null;
    }

    String describeActiveStep(long currentTick) {
        SequenceStepSnapshot step = getActiveStep();
        if (step == null) return "Sequence has no remaining step";

        StringBuilder message = new StringBuilder("Sequence blocked at ");
        message.append(step.describe());
        if (step.state() == StepState.PENDING && currentTick < step.scheduledTick()) {
            message.append("; scheduled for tick ")
                .append(step.scheduledTick());
        } else if (step.startedTick() >= 0) {
            message.append("; active since tick ")
                .append(step.startedTick());
        }
        if (step.attempts() > 0) {
            message.append("; attempts: ")
                .append(step.attempts());
        }
        appendLastAssertion(message, step.lastAssertion());
        return message.toString();
    }

    private GameTestSequence addStep(TestPhase phase, StepKind kind, String label, Runnable action, int maxTicks) {
        if (action == null) throw new IllegalArgumentException("sequence action must not be null");
        long tick = resolveEventTick(phase);
        if (lastPhase == TestPhase.END && phase == TestPhase.START && tick == lastScheduledTick) {
            throw new IllegalStateException(
                "Cannot schedule a START-phase sequence event after an END-phase event at the same tick. "
                    + "Insert thenIdle(1) before the START-phase event.");
        }
        SequenceStep step = new SequenceStep(
            steps.size() + 1,
            tick,
            maxTicks,
            phase,
            kind,
            normalizeLabel(label),
            captureSource(),
            action);
        pendingSteps.add(step);
        steps.add(step);
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

    private static void validateMaxTicks(int maxTicks) {
        if (maxTicks <= 0) throw new IllegalArgumentException("maxTicks must be greater than zero");
    }

    // Breaking on phase mismatch is safe because the ordering constraint (START before END at the
    // same tick, ticks always ascending) guarantees remaining steps are either same-tick later-phase
    // or a later tick — both will be processed by the matching phase call.
    void tick(long currentTick, TestPhase phase) {
        while (!pendingSteps.isEmpty() && !instance.isDone()) {
            SequenceStep head = pendingSteps.peek();
            if (currentTick < head.scheduledTick) break;
            if (head.phase != phase) break;

            head.start(currentTick);
            if (head.kind == StepKind.WAIT_UNTIL) {
                head.attempts++;
                try {
                    head.action.run();
                    long schedulingDelay = currentTick - head.scheduledTick;
                    head.complete(currentTick);
                    pendingSteps.poll();
                    shiftPendingSteps(schedulingDelay);
                } catch (AssertionError e) {
                    head.lastAssertion = e;
                    if (head.deadlineTick >= 0 && currentTick >= head.deadlineTick) {
                        head.fail(currentTick);
                        throw new SequenceStepTimeoutException(head.snapshot(steps.size()), e);
                    }
                    break;
                } catch (RuntimeException | Error e) {
                    head.fail(currentTick);
                    throw e;
                }
            } else {
                try {
                    head.action.run();
                    head.complete(currentTick);
                    pendingSteps.poll();
                } catch (RuntimeException | Error e) {
                    head.fail(currentTick);
                    throw e;
                }
            }
        }
    }

    private void shiftPendingSteps(long ticks) {
        if (ticks <= 0) return;
        // Steps are stored at declaration-time absolute ticks. Rebase them after a wait finishes late so
        // explicit thenIdle gaps remain relative to the wait's actual completion.
        for (SequenceStep step : pendingSteps) {
            step.scheduledTick += ticks;
        }
    }

    private static String normalizeLabel(String label) {
        if (label == null) return "";
        return label.trim();
    }

    private static SourceLocation captureSource() {
        StackTraceElement[] trace = new Throwable().getStackTrace();
        String sequenceClass = GameTestSequence.class.getName();
        for (StackTraceElement frame : trace) {
            if (!frame.getClassName()
                .equals(sequenceClass)) {
                return new SourceLocation(frame.getClassName(), frame.getFileName(), frame.getLineNumber());
            }
        }
        return new SourceLocation("unknown", "Unknown Source", -1);
    }

    private static void appendLastAssertion(StringBuilder message, AssertionError assertion) {
        if (assertion == null) return;
        String assertionMessage = assertion.getMessage();
        message.append("; last assertion: ");
        message.append(
            assertionMessage == null || assertionMessage.isEmpty() ? assertion.getClass()
                .getName() : assertionMessage);
    }

    public enum StepKind {
        EXECUTE,
        WAIT_UNTIL
    }

    public enum StepState {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public static final class SourceLocation {

        private final String className;
        private final String fileName;
        private final int lineNumber;

        SourceLocation(String className, String fileName, int lineNumber) {
            this.className = className;
            this.fileName = fileName;
            this.lineNumber = lineNumber;
        }

        public String className() {
            return className;
        }

        public String fileName() {
            return fileName;
        }

        public int lineNumber() {
            return lineNumber;
        }

        @Override
        public String toString() {
            return lineNumber >= 0 ? fileName + ":" + lineNumber : fileName;
        }
    }

    public static final class SequenceStepSnapshot {

        private final int index;
        private final int totalSteps;
        private final long scheduledTick;
        private final int maxTicks;
        private final long deadlineTick;
        private final long startedTick;
        private final long completedTick;
        private final int attempts;
        private final TestPhase phase;
        private final StepKind kind;
        private final StepState state;
        private final String label;
        private final SourceLocation source;
        private final AssertionError lastAssertion;

        SequenceStepSnapshot(int index, int totalSteps, long scheduledTick, int maxTicks, long deadlineTick,
            long startedTick, long completedTick, int attempts, TestPhase phase, StepKind kind, StepState state,
            String label, SourceLocation source, AssertionError lastAssertion) {
            this.index = index;
            this.totalSteps = totalSteps;
            this.scheduledTick = scheduledTick;
            this.maxTicks = maxTicks;
            this.deadlineTick = deadlineTick;
            this.startedTick = startedTick;
            this.completedTick = completedTick;
            this.attempts = attempts;
            this.phase = phase;
            this.kind = kind;
            this.state = state;
            this.label = label;
            this.source = source;
            this.lastAssertion = lastAssertion;
        }

        public int index() {
            return index;
        }

        public int totalSteps() {
            return totalSteps;
        }

        public long scheduledTick() {
            return scheduledTick;
        }

        public int maxTicks() {
            return maxTicks;
        }

        public long deadlineTick() {
            return deadlineTick;
        }

        public long startedTick() {
            return startedTick;
        }

        public long completedTick() {
            return completedTick;
        }

        public int attempts() {
            return attempts;
        }

        public TestPhase phase() {
            return phase;
        }

        public StepKind kind() {
            return kind;
        }

        public StepState state() {
            return state;
        }

        public String label() {
            return label;
        }

        public SourceLocation source() {
            return source;
        }

        public AssertionError lastAssertion() {
            return lastAssertion;
        }

        public String describe() {
            StringBuilder description = new StringBuilder();
            description.append("step ")
                .append(index)
                .append('/')
                .append(totalSteps)
                .append(' ');
            if (!label.isEmpty()) description.append('"')
                .append(label)
                .append("\" ");
            description.append('(')
                .append(kind)
                .append(' ')
                .append(phase)
                .append(" at ")
                .append(source)
                .append(')');
            return description.toString();
        }
    }

    private static final class SequenceStep {

        final int index;
        long scheduledTick;
        final int maxTicks;
        final TestPhase phase;
        final StepKind kind;
        final String label;
        final SourceLocation source;
        final Runnable action;
        StepState state = StepState.PENDING;
        long deadlineTick = -1;
        long startedTick = -1;
        long completedTick = -1;
        int attempts;
        AssertionError lastAssertion;

        SequenceStep(int index, long scheduledTick, int maxTicks, TestPhase phase, StepKind kind, String label,
            SourceLocation source, Runnable action) {
            this.index = index;
            this.scheduledTick = scheduledTick;
            this.maxTicks = maxTicks;
            this.phase = phase;
            this.kind = kind;
            this.label = label;
            this.source = source;
            this.action = action;
        }

        void start(long currentTick) {
            if (state != StepState.PENDING) return;
            state = StepState.RUNNING;
            startedTick = currentTick;
            if (maxTicks > 0) deadlineTick = currentTick + maxTicks - 1L;
        }

        void complete(long currentTick) {
            state = StepState.COMPLETED;
            completedTick = currentTick;
        }

        void fail(long currentTick) {
            state = StepState.FAILED;
            completedTick = currentTick;
        }

        SequenceStepSnapshot snapshot(int totalSteps) {
            return new SequenceStepSnapshot(
                index,
                totalSteps,
                scheduledTick,
                maxTicks,
                deadlineTick,
                startedTick,
                completedTick,
                attempts,
                phase,
                kind,
                state,
                label,
                source,
                lastAssertion);
        }
    }
}
