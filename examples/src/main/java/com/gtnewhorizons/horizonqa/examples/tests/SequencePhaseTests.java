package com.gtnewhorizons.horizonqa.examples.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.BeforeBatch;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.examples.ExamplesMod;

@GameTestHolder(ExamplesMod.MODID)
public class SequencePhaseTests {

    @BeforeBatch("")
    public static void setup() {}

    @GameTest(timeoutTicks = 20)
    public static void phaseOrder(GameTestHelper helper) {
        List<String> events = new ArrayList<>();
        helper.startSequence()
            .thenExecuteAtStart(() -> events.add("start-0"))
            .thenExecute(() -> events.add("end-0"))
            .thenIdle(1)
            .thenExecuteAtStart(() -> events.add("start-1"))
            .thenExecute(() -> {
                events.add("end-1");
                helper.assertIterableEquals(Arrays.asList("start-0", "end-0", "start-1", "end-1"), events);
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    public static void defaultExecuteRunsAtEnd(GameTestHelper helper) {
        List<String> events = new ArrayList<>();
        helper.startSequence()
            .thenExecuteAtStart(() -> events.add("start"))
            .thenExecute(() -> {
                events.add("end");
                helper.assertIterableEquals(Arrays.asList("start", "end"), events);
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20, required = false)
    public static void invalidOrderingThrows(GameTestHelper helper) {
        try {
            helper.startSequence()
                .thenExecuteAtEnd(() -> {})
                .thenExecuteAtStart(() -> {});
            helper.fail("Expected IllegalStateException for END->START at the same tick");
        } catch (IllegalStateException e) {
            helper.succeed();
        }
    }

    @GameTest(timeoutTicks = 20)
    public static void idleCreatesRealBoundary(GameTestHelper helper) {
        List<String> events = new ArrayList<>();
        helper.startSequence()
            .thenExecuteAtStart(() -> events.add("a"))
            .thenIdle(1)
            .thenExecuteAtStart(() -> events.add("b"))
            .thenExecute(() -> { helper.assertIterableEquals(Arrays.asList("a", "b"), events); })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 5)
    public static void boundarySequenceRunsBeforeTimeout(GameTestHelper helper) {
        helper.startSequence()
            .thenIdle(5)
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 5)
    public static void succeedAtTimeoutObservesFinalTick(GameTestHelper helper) {
        final int[] observedTicks = { 0 };
        helper.onEachTick(() -> observedTicks[0]++);
        helper.startSequence()
            .thenIdle(5)
            .thenExecute(() -> helper.assertEquals(5, observedTicks[0], "Expected final tick to be observed"));
        helper.succeedAtTimeout();
    }
}
