package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import com.google.common.base.Ticker;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.report.ElapsedTime;
import com.gtnewhorizons.horizonqa.report.StepResult;

public class RuntimeTimingTest {

    @Test
    public void throwingActionHasAFailedMeasurementAndPendingStepsStayUnmeasured() throws Exception {
        Fixture fixture = new Fixture();
        fixture.sequence.thenExecute("broken action", () -> {
            fixture.nanos.set(500_000_000L);
            throw new IllegalStateException("failure");
        })
            .thenExecute(() -> {});
        fixture.tick();
        StepResult failed = fixture.instance.stepResults()
            .get(0);
        assertEquals("FAILED", failed.status());
        assertEquals(
            0.5,
            failed.elapsed()
                .seconds(),
            0);
        assertEquals(
            ElapsedTime.State.COMPLETE,
            failed.elapsed()
                .state());
        assertEquals(
            ElapsedTime.State.UNAVAILABLE,
            fixture.instance.stepResults()
                .get(1)
                .elapsed()
                .state());
    }

    @Test
    public void asyncRetriesIncludeQueueTimeAndIgnoreSimulationRate() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<Void> first = new CompletableFuture<>();
        CompletableFuture<Void> second = new CompletableFuture<>();
        int[] attempts = { 0 };
        fixture.sequence.thenWaitUntilAsync("queued assertion", 100, () -> attempts[0]++ == 0 ? first : second);
        fixture.sequence.thenWaitUntilAccelerated("server state", 10, 10, () -> {});
        fixture.tick();
        fixture.nanos.set(2_000_000_000L);
        first.completeExceptionally(new AssertionError("not ready"));
        fixture.tick();
        fixture.tick();
        fixture.nanos.set(5_000_000_000L);
        second.complete(null);
        fixture.tick();
        StepResult result = fixture.instance.stepResults()
            .get(0);
        assertEquals(
            5,
            result.elapsed()
                .seconds(),
            0);
        assertEquals(2, result.attempts());
        assertEquals("COMPLETED", result.status());
        assertEquals(
            0,
            fixture.instance.stepResults()
                .get(1)
                .elapsed()
                .seconds(),
            0);
        assertEquals(
            10,
            fixture.instance.stepResults()
                .get(1)
                .requestedMultiplier());
    }

    @Test
    public void abortFreezesInFlightStepAndLeavesFutureStepsUnmeasured() throws Exception {
        Fixture fixture = new Fixture();
        fixture.sequence.thenExecuteAsync("capture", 100, CompletableFuture::new)
            .thenExecute(() -> {});
        fixture.tick();
        fixture.nanos.set(3_000_000_000L);
        fixture.instance.abortExecution("cancelled", null);
        fixture.nanos.set(7_000_000_000L);
        StepResult active = fixture.instance.stepResults()
            .get(0);
        assertEquals("INTERRUPTED", active.status());
        assertEquals(
            ElapsedTime.State.PARTIAL,
            active.elapsed()
                .state());
        assertEquals(
            3,
            active.elapsed()
                .seconds(),
            0);
        StepResult pending = fixture.instance.stepResults()
            .get(1);
        assertEquals("PENDING", pending.status());
        assertEquals(
            ElapsedTime.State.UNAVAILABLE,
            pending.elapsed()
                .state());
        assertEquals(
            ElapsedTime.State.PARTIAL,
            fixture.instance.timing()
                .total()
                .state());
    }

    @Test
    public void idleIsMeasuredWithoutChangingItsTickBoundary() throws Exception {
        Fixture fixture = new Fixture();
        fixture.sequence.thenExecute(() -> {})
            .thenIdle(3)
            .thenExecute(() -> assertEquals(4, fixture.instance.getTickCount()));
        fixture.tick();
        fixture.nanos.set(1_000_000_000L);
        fixture.tick();
        fixture.tick();
        fixture.nanos.set(4_000_000_000L);
        fixture.tick();
        StepResult idle = fixture.instance.stepResults()
            .get(1);
        assertEquals("IDLE", idle.kind());
        assertEquals(
            4,
            idle.elapsed()
                .seconds(),
            0);
        assertEquals("COMPLETED", idle.status());
        assertTrue(
            fixture.instance.stepResults()
                .get(0)
                .label()
                .contains("RuntimeTimingTest.java"));
    }

    @Test
    public void cleanupIsSeparateAndTotalIsObservedRatherThanSummedSteps() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<Void> cleanup = new CompletableFuture<>();
        fixture.instance.addAsyncCleanup(10, () -> cleanup);
        fixture.nanos.set(2_000_000_000L);
        fixture.instance.succeed();
        assertEquals(
            2,
            fixture.instance.timing()
                .execution()
                .seconds(),
            0);
        fixture.nanos.set(6_000_000_000L);
        cleanup.complete(null);
        fixture.instance.tickEnd();
        assertEquals(
            4,
            fixture.instance.timing()
                .cleanup()
                .seconds(),
            0);
        assertEquals(
            6,
            fixture.instance.timing()
                .total()
                .seconds(),
            0);
        assertEquals(
            ElapsedTime.State.COMPLETE,
            fixture.instance.timing()
                .total()
                .state());
    }

    public static void define(GameTestHelper helper) {}

    private static final class Fixture {

        final AtomicLong nanos = new AtomicLong();
        final GameTestInstance instance;
        final GameTestSequence sequence;

        Fixture() throws Exception {
            GameTestDefinition definition = new GameTestDefinition(
                "timing:test",
                RuntimeTimingTest.class.getMethod("define", GameTestHelper.class),
                "",
                200,
                "",
                true,
                0);
            instance = new GameTestInstance(definition, 0, 0, 0, null, new Ticker() {

                @Override
                public long read() {
                    return nanos.get();
                }
            });
            instance.start(null);
            sequence = new GameTestSequence(instance);
            instance.setSequence(sequence);
        }

        void tick() {
            instance.tickStart();
            instance.tickEnd();
        }
    }
}
