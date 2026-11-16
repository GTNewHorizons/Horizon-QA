package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestAssertException;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.LabelResolutionException;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.event.SequenceStepFinished;
import com.gtnewhorizons.horizonqa.api.event.SequenceStepStarted;
import com.gtnewhorizons.horizonqa.api.event.TestFinished;
import com.gtnewhorizons.horizonqa.internal.GameTestSequence.SequenceStepSnapshot;
import com.gtnewhorizons.horizonqa.internal.GameTestSequence.StepKind;
import com.gtnewhorizons.horizonqa.internal.GameTestSequence.StepState;
import com.gtnewhorizons.horizonqa.report.CaseResult;

public class GameTestSequenceTest {

    @Test
    public void finishedStepSummaryOnlyShowsUsefulTiming() {
        assertEquals(
            "Completed sequence step 1/1 'execute'",
            new SequenceStepFinished(1, 1, 1, "execute", "completed", 1, 1).summary());
        assertEquals(
            "Completed sequence step 1/1 'wait' after 3 ticks",
            new SequenceStepFinished(3, 1, 1, "wait", "completed", 3, 3).summary());
        assertEquals(
            "Failed sequence step 1/1 'pending' without starting",
            new SequenceStepFinished(3, 1, 1, "pending", "failed", 0, 0).summary());
        assertEquals(
            "Completed sequence step 1/1 'spaced' after 2 attempts over 3 ticks",
            new SequenceStepFinished(3, 1, 1, "spaced", "completed", 2, 3).summary());
        assertEquals(
            "Completed sequence step 1/1 'sparse' after 1 attempt over 2 ticks",
            new SequenceStepFinished(2, 1, 1, "sparse", "completed", 1, 2).summary());
    }

    @Test
    public void boundedWaitFailsOnItsFinalAllowedTickAndRetainsAssertion() {
        GameTestInstance instance = new GameTestInstance(null, 0, 0, 0);
        GameTestSequence sequence = new GameTestSequence(instance);
        AtomicInteger attempts = new AtomicInteger();
        GameTestAssertException lastAssertion = new GameTestAssertException(
            "network is inactive",
            new TestPos(4, 5, 6));

        sequence.thenWaitUntil("network becomes active", 3, () -> {
            attempts.incrementAndGet();
            throw lastAssertion;
        });

        sequence.tick(1, TestPhase.END);
        sequence.tick(2, TestPhase.END);
        SequenceStepTimeoutException failure = assertThrows(
            SequenceStepTimeoutException.class,
            () -> sequence.tick(3, TestPhase.END));

        assertEquals(3, attempts.get());
        assertSame(lastAssertion, failure.getCause());
        assertTrue(
            failure.getMessage()
                .contains("network becomes active"));
        assertTrue(
            failure.getMessage()
                .contains("within 3 tick(s) after 3 attempt(s)"));
        assertTrue(
            failure.getMessage()
                .contains("Last assertion: network is inactive"));
        assertTrue(failure.hasPosition());
        assertEquals(4, failure.getX());
        assertEquals(5, failure.getY());
        assertEquals(6, failure.getZ());
        assertEquals(new TestPos(4, 5, 6), failure.getPos());

        SequenceStepSnapshot active = sequence.getActiveStep();
        assertNotNull(active);
        assertEquals(1, active.index());
        assertEquals(1, active.totalSteps());
        assertEquals(StepKind.WAIT_UNTIL, active.kind());
        assertEquals(StepState.FAILED, active.state());
        assertEquals(1, active.scheduledTick());
        assertEquals(3, active.deadlineTick());
        assertEquals(1, active.startedTick());
        assertEquals(3, active.completedTick());
        assertEquals(3, active.attempts());
        assertSame(lastAssertion, active.lastAssertion());

        List<?> events = instance.getRecorder()
            .snapshot();
        assertEquals(2, events.size());
        SequenceStepStarted started = (SequenceStepStarted) events.get(0);
        assertEquals("network becomes active", started.label());
        assertEquals("WAIT_UNTIL", started.kind());
        assertEquals("END", started.phase());
        SequenceStepFinished finished = (SequenceStepFinished) events.get(1);
        assertEquals("failed", finished.outcome());
        assertEquals(3, finished.attempts());
        assertEquals(3, finished.elapsedTicks());
    }

    @Test
    public void successfulBoundedWaitContinuesImmediately() {
        GameTestInstance instance = new GameTestInstance(null, 0, 0, 0);
        GameTestSequence sequence = new GameTestSequence(instance);
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();

        sequence.thenWaitUntil(3, () -> { if (attempts.incrementAndGet() < 2) throw new AssertionError("not yet"); })
            .thenExecute(executions::incrementAndGet);

        sequence.tick(1, TestPhase.END);

        assertEquals(1, attempts.get());
        assertEquals(0, executions.get());

        sequence.tick(2, TestPhase.END);

        assertEquals(2, attempts.get());
        assertEquals(1, executions.get());
        assertEquals(
            StepState.COMPLETED,
            sequence.getSteps()
                .get(0)
                .state());
        assertEquals(
            StepState.COMPLETED,
            sequence.getSteps()
                .get(1)
                .state());
        assertEquals(
            2,
            sequence.getSteps()
                .get(1)
                .completedTick());

        List<?> events = instance.getRecorder()
            .snapshot();
        assertEquals(4, events.size());
        assertTrue(events.get(0) instanceof SequenceStepStarted);
        SequenceStepFinished waitFinished = (SequenceStepFinished) events.get(1);
        assertEquals("completed", waitFinished.outcome());
        assertEquals(2, waitFinished.attempts());
        assertEquals(2, waitFinished.elapsedTicks());
        assertTrue(events.get(2) instanceof SequenceStepStarted);
        SequenceStepFinished executeFinished = (SequenceStepFinished) events.get(3);
        assertEquals("completed", executeFinished.outcome());
        assertEquals(1, executeFinished.attempts());
        assertEquals(1, executeFinished.elapsedTicks());
    }

    @Test
    public void successfulStartPhaseBoundedWaitContinuesAtEndOfSameTick() {
        GameTestSequence sequence = new GameTestSequence(new GameTestInstance(null, 0, 0, 0));
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();

        sequence
            .thenWaitUntilAtStart(3, () -> { if (attempts.incrementAndGet() < 2) throw new AssertionError("not yet"); })
            .thenExecuteAtEnd(executions::incrementAndGet);

        sequence.tick(1, TestPhase.START);
        sequence.tick(1, TestPhase.END);
        sequence.tick(2, TestPhase.START);

        assertEquals(2, attempts.get());
        assertEquals(0, executions.get());

        sequence.tick(2, TestPhase.END);

        assertEquals(1, executions.get());
        assertEquals(
            2,
            sequence.getSteps()
                .get(1)
                .completedTick());
    }

    @Test
    public void idleAfterBoundedWaitStartsWhenTheWaitCompletes() {
        GameTestSequence sequence = new GameTestSequence(new GameTestInstance(null, 0, 0, 0));
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();

        sequence.thenWaitUntil(5, () -> { if (attempts.incrementAndGet() < 3) throw new AssertionError("not yet"); })
            .thenIdle(2)
            .thenExecute(executions::incrementAndGet);

        sequence.tick(1, TestPhase.END);
        sequence.tick(2, TestPhase.END);
        sequence.tick(3, TestPhase.END);
        sequence.tick(4, TestPhase.END);

        assertEquals(3, attempts.get());
        assertEquals(0, executions.get());
        assertEquals(
            5,
            sequence.getActiveStep()
                .scheduledTick());

        sequence.tick(5, TestPhase.END);

        assertEquals(1, executions.get());
    }

    @Test
    public void boundedWaitBudgetStartsWhenTheStepBecomesActive() {
        GameTestSequence sequence = new GameTestSequence(new GameTestInstance(null, 0, 0, 0));
        AtomicInteger firstAttempts = new AtomicInteger();
        AtomicInteger boundedAttempts = new AtomicInteger();

        sequence.thenWaitUntil(
            () -> { if (firstAttempts.incrementAndGet() < 5) throw new AssertionError("first step still waiting"); })
            .thenWaitUntil(3, () -> {
                boundedAttempts.incrementAndGet();
                throw new AssertionError("bounded step still waiting");
            });

        for (int tick = 1; tick <= 6; tick++) {
            sequence.tick(tick, TestPhase.END);
        }
        SequenceStepTimeoutException failure = assertThrows(
            SequenceStepTimeoutException.class,
            () -> sequence.tick(7, TestPhase.END));

        assertEquals(5, firstAttempts.get());
        assertEquals(3, boundedAttempts.get());
        assertEquals(
            5,
            failure.step()
                .startedTick());
        assertEquals(
            7,
            failure.step()
                .deadlineTick());
    }

    @Test
    public void unboundedWaitExposesItsLatestFailureAndSource() {
        GameTestSequence sequence = new GameTestSequence(new GameTestInstance(null, 0, 0, 0));
        AtomicInteger attempts = new AtomicInteger();

        sequence.thenWaitUntil(
            "advertise pattern",
            () -> { throw new AssertionError("attempt " + attempts.incrementAndGet()); });
        sequence.tick(1, TestPhase.END);
        sequence.tick(2, TestPhase.END);

        List<SequenceStepSnapshot> steps = sequence.getSteps();
        assertEquals(1, steps.size());
        SequenceStepSnapshot active = sequence.getActiveStep();
        assertEquals(StepState.RUNNING, active.state());
        assertEquals(2, active.attempts());
        assertEquals(
            "attempt 2",
            active.lastAssertion()
                .getMessage());
        assertEquals("advertise pattern", active.label());
        assertEquals(
            "GameTestSequenceTest.java",
            active.source()
                .fileName());
        assertTrue(
            active.source()
                .lineNumber() > 0);
    }

    @Test
    public void unexpectedWaitFailureMarksTheStepFailed() {
        GameTestSequence sequence = new GameTestSequence(new GameTestInstance(null, 0, 0, 0));
        IllegalStateException failure = new IllegalStateException("unexpected callback failure");

        sequence.thenWaitUntil(() -> { throw failure; });

        assertSame(failure, assertThrows(IllegalStateException.class, () -> sequence.tick(1, TestPhase.END)));

        SequenceStepSnapshot active = sequence.getActiveStep();
        assertNotNull(active);
        assertEquals(StepState.FAILED, active.state());
        assertEquals(1, active.startedTick());
        assertEquals(1, active.completedTick());
        assertEquals(1, active.attempts());
    }

    @Test
    public void testTimeoutReportsTheBlockingSequenceStep() throws Exception {
        GameTestInstance instance = instance("mod:SequenceTests.unboundedWait", "unboundedWait", 2);

        instance.start(null);
        tick(instance);
        tick(instance);

        assertEquals(GameTestStatus.TIMED_OUT, instance.getStatus());
        assertTrue(instance.getFailureCause() instanceof GameTestTimeoutException);
        assertTrue(
            instance.getFailureCause()
                .getMessage()
                .contains("Timed out after 2 ticks"));
        assertTrue(
            instance.getFailureCause()
                .getMessage()
                .contains("network becomes active"));
        assertTrue(
            instance.getFailureCause()
                .getMessage()
                .contains("attempts: 2"));
        assertTrue(
            instance.getFailureCause()
                .getMessage()
                .contains("last assertion: network is inactive"));

        CaseResult result = CaseResult.from(instance);
        assertEquals(CaseResult.Status.TIMED_OUT, result.status());
        assertEquals(
            instance.getFailureCause()
                .getMessage(),
            result.failureMessage());
        assertEquals("GameTestTimeoutError", result.failureType());
        assertTrue(
            result.failureTrace()
                .contains("GameTestTimeoutException"));
        assertTrue(
            result.failureTrace()
                .contains("Caused by: "));
        assertTrue(
            result.failureTrace()
                .contains("network is inactive"));
        assertTrue(
            result.outputLines()
                .stream()
                .anyMatch(line -> line.contains("Failed sequence step 1/1 'network becomes active'")));
    }

    @Test
    public void executeFailureKeepsOriginalCauseAndAddsStepContext() throws Exception {
        GameTestInstance instance = instance("mod:SequenceTests.failingExecute", "failingExecute", 2);

        instance.start(null);
        tick(instance);

        assertEquals(GameTestStatus.FAILED, instance.getStatus());
        assertTrue(instance.getFailureCause() instanceof GameTestAssertException);
        assertEquals(
            "output is missing",
            instance.getFailureCause()
                .getMessage());
        assertEquals(new TestPos(4, 5, 6), ((GameTestAssertException) instance.getFailureCause()).getPos());
        assertTrue(
            instance.getFailureContext()
                .contains("validate crafted output"));

        CaseResult result = CaseResult.from(instance);
        assertTrue(
            result.failureMessage()
                .contains("Sequence step 1/1 \"validate crafted output\""));
        assertTrue(
            result.failureMessage()
                .endsWith(": output is missing"));
        assertTrue(
            result.failureTrace()
                .startsWith(instance.getFailureContext()));
        assertTrue(
            result.outputLines()
                .stream()
                .anyMatch(line -> line.contains("Failed sequence step 1/1 'validate crafted output'")));
    }

    @Test
    public void sequenceInfrastructureFailureKeepsItsErrorKind() throws Exception {
        GameTestInstance instance = instance("mod:SequenceTests.missingLabel", "missingLabel", 2);

        instance.start(null);
        tick(instance);

        assertEquals(GameTestStatus.ERROR, instance.getStatus());
        assertTrue(instance.getFailureCause() instanceof LabelResolutionException);
        CaseResult result = CaseResult.from(instance);
        assertEquals(LabelResolutionException.ERROR_KIND, result.failureType());
        assertTrue(
            result.failureMessage()
                .contains("resolve fixture label"));
    }

    @Test
    public void terminalStepFinishesBeforeTheTest() throws Exception {
        GameTestInstance instance = instance("mod:SequenceTests.endingSequence", "endingSequence", 2);

        instance.start(null);
        tick(instance);

        assertEquals(GameTestStatus.PASSED, instance.getStatus());
        List<?> events = instance.getRecorder()
            .snapshot();
        assertEquals(6, events.size());
        SequenceStepFinished terminalStep = (SequenceStepFinished) events.get(4);
        assertEquals("succeed test", terminalStep.label());
        assertEquals("completed", terminalStep.outcome());
        assertTrue(events.get(5) instanceof TestFinished);
    }

    @Test
    public void boundedWaitRequiresAPositiveDeadline() {
        GameTestSequence sequence = new GameTestSequence(new GameTestInstance(null, 0, 0, 0));

        IllegalArgumentException failure = assertThrows(
            IllegalArgumentException.class,
            () -> sequence.thenWaitUntil(0, () -> {}));

        assertEquals("maxTicks must be greater than zero", failure.getMessage());
    }

    private static GameTestInstance instance(String id, String methodName, int timeoutTicks) throws Exception {
        Method method = TestDefinitions.class.getMethod(methodName, GameTestHelper.class);
        GameTestDefinition definition = new GameTestDefinition(id, method, "", timeoutTicks, "", true, 0);
        return new GameTestInstance(definition, 0, 0, 0);
    }

    private static void tick(GameTestInstance instance) {
        instance.tickStart();
        instance.tickEnd();
    }

    public static final class TestDefinitions {

        public static void unboundedWait(GameTestHelper helper) {
            helper.startSequence()
                .thenWaitUntil("network becomes active", () -> helper.fail("network is inactive"));
        }

        public static void failingExecute(GameTestHelper helper) {
            helper.startSequence()
                .thenExecute(
                    "validate crafted output",
                    () -> { throw new GameTestAssertException("output is missing", new TestPos(4, 5, 6)); });
        }

        public static void missingLabel(GameTestHelper helper) {
            helper.startSequence()
                .thenExecute("resolve fixture label", () -> helper.pos("missing"));
        }

        public static void endingSequence(GameTestHelper helper) {
            helper.startSequence()
                .thenExecute("observe state", () -> {})
                .thenSucceed();
        }
    }
}
