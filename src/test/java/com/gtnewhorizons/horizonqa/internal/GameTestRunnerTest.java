package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;

public class GameTestRunnerTest {

    private static int cleanupRuns;
    private static boolean cleanupAcquiredOwnership;
    private static GameTestRunner cleanupCandidate;

    private static final class FakeInstance extends GameTestInstance {

        final List<String> events;
        int tickStarts;
        int tickEnds;
        boolean done;

        FakeInstance() {
            this(new ArrayList<>());
        }

        FakeInstance(List<String> events) {
            super(null, 0, 0, 0);
            this.events = events;
        }

        @Override
        public void tickStart() {
            tickStarts++;
            events.add("START");
        }

        @Override
        public void tickEnd() {
            tickEnds++;
            events.add("END");
        }

        @Override
        public boolean isDone() {
            return done;
        }
    }

    @After
    public void resetExecution() {
        GameTestRunner.shutdown();
        cleanupRuns = 0;
        cleanupAcquiredOwnership = false;
        cleanupCandidate = null;
    }

    @Test
    public void activeRunnerDeliversStartBeforeWorldAndEndAfterWorld() {
        List<String> events = new ArrayList<>();
        GameTestRunner runner = new GameTestRunner();
        FakeInstance instance = new FakeInstance(events);

        assertTrue(runner.tryStart(GameTestRunner.Kind.INTERACTIVE, () -> runner.addInstance(instance)));

        GameTestRunner.handleTickStart();
        events.add("WORLD");
        GameTestRunner.handleTickEnd();

        assertEquals(Arrays.asList("START", "WORLD", "END"), events);
    }

    @Test
    public void ticksAreNoOpsWithoutAnOwner() {
        GameTestRunner.handleTickStart();
        GameTestRunner.handleTickEnd();
    }

    @Test
    public void interactiveOwnerCannotBeReplacedByBatchOwner() {
        GameTestRunner interactive = new GameTestRunner();
        FakeInstance active = new FakeInstance();
        assertTrue(interactive.tryStart(GameTestRunner.Kind.INTERACTIVE, () -> interactive.addInstance(active)));

        GameTestRunner batch = new GameTestRunner();
        FakeInstance rejected = new FakeInstance();
        assertFalse(batch.tryStart(GameTestRunner.Kind.BATCH, () -> batch.addInstance(rejected)));

        tick();

        assertEquals(1, active.tickStarts);
        assertEquals(1, active.tickEnds);
        assertEquals(0, rejected.tickStarts);
        assertEquals(0, rejected.tickEnds);
    }

    @Test
    public void batchOwnerCannotBeReplacedByInteractiveOwner() {
        GameTestRunner batch = new GameTestRunner();
        FakeInstance active = new FakeInstance();
        assertTrue(batch.tryStart(GameTestRunner.Kind.BATCH, () -> batch.addInstance(active)));
        assertTrue(GameTestRunner.isBatchActive());

        GameTestRunner interactive = new GameTestRunner();
        assertFalse(
            interactive.tryStart(GameTestRunner.Kind.INTERACTIVE, () -> interactive.addInstance(new FakeInstance())));
        assertTrue(GameTestRunner.isBatchActive());
    }

    @Test
    public void sameInteractiveOwnerCanAddWork() {
        GameTestRunner runner = new GameTestRunner();
        FakeInstance first = new FakeInstance();
        FakeInstance second = new FakeInstance();
        assertTrue(runner.tryStart(GameTestRunner.Kind.INTERACTIVE, () -> runner.addInstance(first)));

        assertTrue(runner.tryStart(GameTestRunner.Kind.INTERACTIVE, () -> runner.addInstance(second)));
        tick();

        assertEquals(1, first.tickStarts);
        assertEquals(1, second.tickStarts);
    }

    @Test
    public void normalCompletionReleasesOwnership() {
        GameTestRunner first = new GameTestRunner();
        FakeInstance completed = new FakeInstance();
        completed.done = true;
        assertTrue(first.tryStart(GameTestRunner.Kind.BATCH, () -> first.addInstance(completed)));

        tick();

        GameTestRunner replacement = new GameTestRunner();
        assertTrue(
            replacement.tryStart(GameTestRunner.Kind.INTERACTIVE, () -> replacement.addInstance(new FakeInstance())));
    }

    @Test
    public void completionCallbackFiresOnceAndReleasesOwnership() {
        int[] callbackRuns = new int[1];
        GameTestRunner runner = new GameTestRunner();
        FakeInstance instance = new FakeInstance();
        assertTrue(
            runner.tryStart(
                GameTestRunner.Kind.BATCH,
                () -> runner.run(Collections.singletonList(instance), () -> callbackRuns[0]++)));

        tick();
        assertEquals(0, callbackRuns[0]);

        instance.done = true;
        tick();
        tick();

        assertEquals(1, callbackRuns[0]);
        assertTrue(new GameTestRunner().tryStart(GameTestRunner.Kind.INTERACTIVE, () -> {}));
    }

    @Test
    public void startupFailureReleasesOwnership() {
        GameTestRunner runner = new GameTestRunner();
        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> runner.tryStart(GameTestRunner.Kind.BATCH, () -> { throw new IllegalStateException("boom"); }));

        assertEquals("boom", failure.getMessage());
        assertFalse(GameTestRunner.isBatchActive());
        assertTrue(new GameTestRunner().tryStart(GameTestRunner.Kind.INTERACTIVE, () -> {}));
    }

    @Test
    public void firstTickFailureReleasesOwnership() {
        GameTestRunner runner = new GameTestRunner();
        assertTrue(
            runner.tryStart(
                GameTestRunner.Kind.BATCH,
                () -> runner.scheduleOnFirstTick(() -> { throw new IllegalStateException("boom"); })));

        IllegalStateException failure = assertThrows(IllegalStateException.class, GameTestRunner::handleTickStart);

        assertEquals("boom", failure.getMessage());
        assertFalse(GameTestRunner.isBatchActive());
        assertTrue(new GameTestRunner().tryStart(GameTestRunner.Kind.INTERACTIVE, () -> {}));
    }

    @Test
    public void completionFailureReleasesOwnership() {
        GameTestRunner runner = new GameTestRunner();
        FakeInstance completed = new FakeInstance();
        completed.done = true;
        assertTrue(
            runner.tryStart(
                GameTestRunner.Kind.BATCH,
                () -> runner
                    .run(Collections.singletonList(completed), () -> { throw new IllegalStateException("boom"); })));

        GameTestRunner.handleTickStart();
        IllegalStateException failure = assertThrows(IllegalStateException.class, GameTestRunner::handleTickEnd);

        assertEquals("boom", failure.getMessage());
        assertFalse(GameTestRunner.isBatchActive());
        assertTrue(new GameTestRunner().tryStart(GameTestRunner.Kind.INTERACTIVE, () -> {}));
    }

    @Test
    public void shutdownClearsOwnershipAndRunsInstanceCleanup() throws Exception {
        cleanupCandidate = new GameTestRunner();
        GameTestDefinition definition = new GameTestDefinition(
            "horizonqatest:Runner.pending",
            GameTestRunnerTest.class.getMethod("pendingTest", GameTestHelper.class),
            "",
            20,
            "",
            true,
            0);
        GameTestInstance instance = new GameTestInstance(definition, 0, 0, 0);
        instance.start(null);
        GameTestRunner runner = new GameTestRunner();
        assertTrue(runner.tryStart(GameTestRunner.Kind.BATCH, () -> runner.addInstance(instance)));
        assertTrue(GameTestRunner.isBatchActive());

        GameTestRunner.shutdown();

        assertFalse(GameTestRunner.isBatchActive());
        assertFalse(GameTestRunner.isTurboActive());
        assertEquals(1, cleanupRuns);
        assertFalse(cleanupAcquiredOwnership);
        assertEquals(GameTestStatus.ERROR, instance.getStatus());
        assertTrue(instance.getFailureCause() instanceof GameTestInfrastructureException);
        assertEquals("EXECUTION_ABORTED", ((GameTestInfrastructureException) instance.getFailureCause()).kind());
        assertTrue(cleanupCandidate.tryStart(GameTestRunner.Kind.INTERACTIVE, () -> {}));
    }

    @Test
    public void emptyCompletionIsOneShot() {
        int[] callbackRuns = new int[1];
        GameTestRunner runner = new GameTestRunner();
        assertTrue(
            runner.tryStart(
                GameTestRunner.Kind.BATCH,
                () -> runner.run(Collections.emptyList(), () -> callbackRuns[0]++)));

        GameTestRunner replacement = new GameTestRunner();
        FakeInstance completed = new FakeInstance();
        completed.done = true;
        assertTrue(replacement.tryStart(GameTestRunner.Kind.INTERACTIVE, () -> replacement.addInstance(completed)));
        tick();

        assertEquals(1, callbackRuns[0]);
    }

    @Test
    public void unownedRunnerCannotSubmitWork() {
        GameTestRunner runner = new GameTestRunner();

        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> runner.addInstance(new FakeInstance()));

        assertEquals("GameTest runner does not own execution.", failure.getMessage());
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

    public static void pendingTest(GameTestHelper helper) {
        helper.afterTest(() -> {
            cleanupRuns++;
            GameTestRunner.shutdown();
            cleanupAcquiredOwnership = cleanupCandidate.tryStart(GameTestRunner.Kind.INTERACTIVE, () -> {});
        });
    }

    private static void tick() {
        GameTestRunner.handleTickStart();
        GameTestRunner.handleTickEnd();
    }
}
