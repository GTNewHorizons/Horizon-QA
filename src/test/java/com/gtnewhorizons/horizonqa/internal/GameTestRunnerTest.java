package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Collections;

import org.junit.After;
import org.junit.Test;

public class GameTestRunnerTest {

    private static final class FakeInstance extends GameTestInstance {

        int tickStarts;
        int tickEnds;
        boolean done;

        FakeInstance() {
            super(null, 0, 0, 0);
        }

        @Override
        public void tickStart() {
            tickStarts++;
        }

        @Override
        public void tickEnd() {
            tickEnds++;
        }

        @Override
        public boolean isDone() {
            return done;
        }
    }

    private int callbackRuns;

    @After
    public void clearActiveRunner() {
        GameTestRunner sentinel = new GameTestRunner();
        sentinel.register();
        sentinel.unregister();
    }

    private static void tick() {
        GameTestRunner.handleTickStart();
        GameTestRunner.handleTickEnd();
    }

    @Test
    public void registeredRunnerReceivesStaticTicks() {
        GameTestRunner runner = new GameTestRunner();
        FakeInstance inst = new FakeInstance();
        runner.run(Collections.singletonList(inst), () -> callbackRuns++);
        runner.register();

        tick();

        assertEquals(1, inst.tickStarts);
        assertEquals(1, inst.tickEnds);
        assertEquals(0, callbackRuns);
    }

    @Test
    public void ticksAreNoOpsWhenNoRunnerRegistered() {
        GameTestRunner runner = new GameTestRunner();
        FakeInstance inst = new FakeInstance();
        runner.run(Collections.singletonList(inst), () -> callbackRuns++);

        tick();

        assertEquals(0, inst.tickStarts);
        assertEquals(0, inst.tickEnds);
    }

    @Test
    public void unregisterStopsTickDelivery() {
        GameTestRunner runner = new GameTestRunner();
        FakeInstance inst = new FakeInstance();
        runner.run(Collections.singletonList(inst), () -> callbackRuns++);
        runner.register();
        runner.unregister();

        tick();

        assertEquals(0, inst.tickStarts);
        assertEquals(0, inst.tickEnds);
    }

    @Test
    public void unregisterFromNonActiveRunnerLeavesActiveRunnerInPlace() {
        GameTestRunner active = new GameTestRunner();
        FakeInstance inst = new FakeInstance();
        active.run(Collections.singletonList(inst), () -> callbackRuns++);
        active.register();

        new GameTestRunner().unregister();
        tick();

        assertEquals(1, inst.tickStarts);
        assertEquals(1, inst.tickEnds);
    }

    @Test
    public void registerSilentlyOverwritesPreviousRunner() {
        GameTestRunner first = new GameTestRunner();
        FakeInstance firstInst = new FakeInstance();
        first.run(Collections.singletonList(firstInst), () -> callbackRuns++);
        first.register();

        GameTestRunner second = new GameTestRunner();
        FakeInstance secondInst = new FakeInstance();
        second.run(Collections.singletonList(secondInst), null);
        second.register();

        tick();

        assertEquals(0, firstInst.tickStarts);
        assertEquals(0, firstInst.tickEnds);
        assertEquals(1, secondInst.tickStarts);
        assertEquals(1, secondInst.tickEnds);
    }

    @Test
    public void unregisterOfOverwrittenRunnerLeavesNewRunnerActive() {
        GameTestRunner first = new GameTestRunner();
        first.register();

        GameTestRunner second = new GameTestRunner();
        FakeInstance inst = new FakeInstance();
        second.run(Collections.singletonList(inst), null);
        second.register();

        first.unregister();
        tick();

        assertEquals(1, inst.tickStarts);
        assertEquals(1, inst.tickEnds);
    }

    @Test
    public void overwrittenRunnerRetainsStateAndResumesAfterReRegister() {
        GameTestRunner orphan = new GameTestRunner();
        FakeInstance inst = new FakeInstance();
        orphan.run(Collections.singletonList(inst), () -> callbackRuns++);
        orphan.register();
        new GameTestRunner().register();

        tick();
        assertEquals(0, inst.tickStarts);

        orphan.register();
        tick();
        assertEquals(1, inst.tickStarts);
        assertEquals(1, inst.tickEnds);

        inst.done = true;
        tick();
        assertEquals(1, callbackRuns);
    }

    @Test
    public void runnerStaysRegisteredAfterBatchCompletes() {
        GameTestRunner runner = new GameTestRunner();
        FakeInstance inst = new FakeInstance();
        inst.done = true;
        runner.run(Collections.singletonList(inst), () -> callbackRuns++);
        runner.register();

        tick();
        assertEquals(1, callbackRuns);

        FakeInstance late = new FakeInstance();
        runner.addInstance(late);
        tick();

        assertEquals(1, late.tickStarts);
        assertEquals(1, late.tickEnds);
    }

    @Test
    public void completionCallbackFiresOnceWhenAllInstancesDone() {
        GameTestRunner runner = new GameTestRunner();
        FakeInstance inst = new FakeInstance();
        runner.run(Collections.singletonList(inst), () -> callbackRuns++);
        runner.register();

        tick();
        assertEquals(0, callbackRuns);

        inst.done = true;
        tick();
        assertEquals(1, callbackRuns);

        tick();
        assertEquals(1, callbackRuns);
        assertEquals(1, inst.tickStarts);
        assertEquals(1, inst.tickEnds);
    }

    @Test
    public void rerunReplacesPendingBatchAndDropsItsCallback() {
        GameTestRunner runner = new GameTestRunner();
        FakeInstance firstInst = new FakeInstance();
        int[] firstCallbackRuns = new int[1];
        runner.run(Collections.singletonList(firstInst), () -> firstCallbackRuns[0]++);
        runner.register();

        FakeInstance secondInst = new FakeInstance();
        secondInst.done = true;
        runner.run(Collections.singletonList(secondInst), () -> callbackRuns++);

        tick();

        assertEquals(0, firstInst.tickStarts);
        assertEquals(0, firstCallbackRuns[0]);
        assertEquals(1, callbackRuns);
    }

    @Test
    public void emptyBatchInvokesCallbackImmediatelyWithoutRegistration() {
        GameTestRunner runner = new GameTestRunner();
        runner.run(Collections.emptyList(), () -> callbackRuns++);

        assertEquals(1, callbackRuns);
    }

    @Test
    public void emptyBatchWithNullCallbackIsNoOp() {
        new GameTestRunner().run(Collections.emptyList(), null);
    }

    @Test
    public void gridLayoutUsesConfiguredOriginAndKeepsRowsRelativeToIt() {
        GameTestGridLayout grid = new GameTestGridLayout(16, 128, -32);

        assertArrayEquals(new int[] { 16, 128, -32 }, grid.allocateOrigin());
        assertArrayEquals(new int[] { 24, 128, -32 }, grid.allocateOrigin());

        for (int i = 0; i < 8; i++) {
            grid.allocateOrigin();
        }

        assertArrayEquals(new int[] { 16, 128, -24 }, grid.allocateOrigin());
    }

    @Test
    public void emptyBatchCallbackRemainsArmedAndFiresAgainOnLaterCompletion() {
        GameTestRunner runner = new GameTestRunner();
        runner.run(Collections.emptyList(), () -> callbackRuns++);
        runner.register();
        assertEquals(1, callbackRuns);

        FakeInstance inst = new FakeInstance();
        inst.done = true;
        runner.addInstance(inst);
        tick();

        assertEquals(2, callbackRuns);
    }

    @Test
    public void emptyBatchCallbackExceptionPropagatesFromRun() {
        GameTestRunner runner = new GameTestRunner();
        try {
            runner.run(Collections.emptyList(), () -> { throw new IllegalStateException("boom"); });
            fail("expected callback exception to propagate");
        } catch (IllegalStateException e) {
            assertEquals("boom", e.getMessage());
        }
    }

    @Test
    public void completionCallbackExceptionPropagatesAndDisarmsCallback() {
        GameTestRunner runner = new GameTestRunner();
        FakeInstance inst = new FakeInstance();
        inst.done = true;
        runner.run(Collections.singletonList(inst), () -> {
            callbackRuns++;
            throw new IllegalStateException("boom");
        });
        runner.register();

        GameTestRunner.handleTickStart();
        try {
            GameTestRunner.handleTickEnd();
            fail("expected callback exception to propagate");
        } catch (IllegalStateException e) {
            assertEquals("boom", e.getMessage());
        }
        assertEquals(1, callbackRuns);

        tick();
        assertEquals(1, callbackRuns);
        assertEquals(0, inst.tickStarts);
        assertEquals(0, inst.tickEnds);
    }

    @Test
    public void completedInstancesWithoutCallbackAreClearedOnTickEnd() {
        GameTestRunner runner = new GameTestRunner();
        FakeInstance inst = new FakeInstance();
        inst.done = true;
        runner.register();
        runner.addInstance(inst);

        tick();

        // Flip the instance back to not-done: if it had survived the cleanup it would tick again below.
        inst.done = false;
        FakeInstance replacement = new FakeInstance();
        runner.addInstance(replacement);
        tick();

        assertEquals(0, inst.tickStarts);
        assertEquals(1, replacement.tickStarts);
        assertEquals(1, replacement.tickEnds);
    }
}
