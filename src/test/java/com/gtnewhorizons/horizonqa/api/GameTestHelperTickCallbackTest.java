package com.gtnewhorizons.horizonqa.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.event.TickCallbackStateChanged;
import com.gtnewhorizons.horizonqa.internal.GameTestDefinition;
import com.gtnewhorizons.horizonqa.internal.GameTestInstance;
import com.gtnewhorizons.horizonqa.internal.GameTestStatus;
import com.gtnewhorizons.horizonqa.report.CaseResult;

public class GameTestHelperTickCallbackTest {

    private static final List<String> EVENTS = new ArrayList<>();
    private static TickCallbackHandle handle;
    private static GameTestAssertException callbackFailure;

    @Before
    public void resetFixture() {
        EVENTS.clear();
        handle = null;
        callbackFailure = null;
    }

    @Test
    public void callbackCanBeDisabledReenabledAndRemoved() throws Exception {
        GameTestInstance instance = instance("controllableCallback");
        instance.start(null);

        assertTrue(handle.isEnabled());
        assertFalse(handle.isRemoved());
        tick(instance);
        assertEquals(Arrays.asList("callback"), EVENTS);

        handle.disable();
        assertFalse(handle.isEnabled());
        tick(instance);
        assertEquals(Arrays.asList("callback"), EVENTS);

        handle.enable();
        assertTrue(handle.isEnabled());
        tick(instance);
        assertEquals(Arrays.asList("callback", "callback"), EVENTS);

        handle.remove();
        handle.remove();
        handle.enable();
        handle.disable();
        assertFalse(handle.isEnabled());
        assertTrue(handle.isRemoved());
        tick(instance);
        assertEquals(Arrays.asList("callback", "callback"), EVENTS);
        assertEquals(GameTestStatus.RUNNING, instance.getStatus());
        assertEquals(
            Arrays.asList("registered-enabled", "disabled", "enabled", "removed"),
            instance.getRecorder()
                .snapshot()
                .stream()
                .filter(TickCallbackStateChanged.class::isInstance)
                .map(TickCallbackStateChanged.class::cast)
                .map(TickCallbackStateChanged::state)
                .collect(Collectors.toList()));
    }

    @Test
    public void startAndEndSequenceChangesRespectCallbackPhaseOrdering() throws Exception {
        GameTestInstance instance = instance("sequenceWindow");
        instance.start(null);

        tick(instance);

        assertEquals(Arrays.asList("enable at START", "callback", "disable at END"), EVENTS);
        assertFalse(handle.isEnabled());
        assertEquals(
            Arrays.asList("registered-disabled", "enabled", "disabled"),
            instance.getRecorder()
                .snapshot()
                .stream()
                .filter(TickCallbackStateChanged.class::isInstance)
                .map(TickCallbackStateChanged.class::cast)
                .map(TickCallbackStateChanged::state)
                .collect(Collectors.toList()));

        tick(instance);

        assertEquals(Arrays.asList("enable at START", "callback", "disable at END"), EVENTS);
    }

    @Test
    public void callbacksCanRemoveOrRegisterCallbacksDuringDispatch() throws Exception {
        GameTestInstance instance = instance("mutateDuringDispatch");
        instance.start(null);

        tick(instance);
        assertEquals(Arrays.asList("first", "self"), EVENTS);

        tick(instance);
        assertEquals(Arrays.asList("first", "self", "first", "added"), EVENTS);
    }

    @Test
    public void callbackDispatchStopsWhenTestCompletes() throws Exception {
        GameTestInstance instance = instance("succeedDuringDispatch");
        instance.start(null);

        tick(instance);

        assertEquals(Arrays.asList("succeed"), EVENTS);
        assertEquals(GameTestStatus.PASSED, instance.getStatus());
    }

    @Test
    public void callbackFailureKeepsCauseAndReportsItsName() throws Exception {
        GameTestInstance instance = instance("failingCallback");
        instance.start(null);

        tick(instance);

        assertEquals(GameTestStatus.FAILED, instance.getStatus());
        assertSame(callbackFailure, instance.getFailureCause());
        assertEquals(new TestPos(7, 8, 9), callbackFailure.getPos());
        CaseResult result = CaseResult.from(instance);
        assertTrue(
            result.failureMessage()
                .contains("Per-tick callback 'cell conservation' failed"));
        assertTrue(
            result.failureTrace()
                .startsWith("Per-tick callback 'cell conservation' failed"));
    }

    @Test
    public void callbackNamesMustNotBeBlank() {
        GameTestHelper helper = new GameTestHelper(new GameTestInstance(null, 0, 0, 0), null, 0, 0, 0);

        IllegalArgumentException failure = assertThrows(
            IllegalArgumentException.class,
            () -> helper.onEachTick(" ", () -> {}));

        assertEquals("onEachTick name must not be blank", failure.getMessage());
    }

    private static GameTestInstance instance(String methodName) throws Exception {
        Method method = TestDefinitions.class.getMethod(methodName, GameTestHelper.class);
        GameTestDefinition definition = new GameTestDefinition(
            "mod:TickCallbackTests." + methodName,
            method,
            "",
            20,
            "",
            true,
            0);
        return new GameTestInstance(definition, 0, 0, 0);
    }

    private static void tick(GameTestInstance instance) {
        instance.tickStart();
        instance.tickEnd();
    }

    public static final class TestDefinitions {

        public static void controllableCallback(GameTestHelper helper) {
            handle = helper.onEachTick("controllable callback", () -> EVENTS.add("callback"));
        }

        public static void sequenceWindow(GameTestHelper helper) {
            handle = helper.onEachTickDisabled("sequence window", () -> EVENTS.add("callback"));
            helper.startSequence()
                .thenExecuteAtStart(() -> {
                    EVENTS.add("enable at START");
                    handle.enable();
                })
                .thenExecute(() -> {
                    EVENTS.add("disable at END");
                    handle.disable();
                });
        }

        public static void mutateDuringDispatch(GameTestHelper helper) {
            TickCallbackHandle[] later = new TickCallbackHandle[1];
            TickCallbackHandle[] self = new TickCallbackHandle[1];

            helper.onEachTick("first callback", () -> {
                EVENTS.add("first");
                later[0].remove();
                helper.onEachTick("added callback", () -> EVENTS.add("added"));
            });
            later[0] = helper.onEachTick("later callback", () -> EVENTS.add("later"));
            self[0] = helper.onEachTick("self-removing callback", () -> {
                EVENTS.add("self");
                self[0].remove();
            });
        }

        public static void succeedDuringDispatch(GameTestHelper helper) {
            helper.onEachTick("complete test", () -> {
                EVENTS.add("succeed");
                helper.succeed();
            });
            helper.onEachTick("must not run after completion", () -> {
                EVENTS.add("after completion");
                throw new AssertionError("Callback ran after the test completed");
            });
        }

        public static void failingCallback(GameTestHelper helper) {
            callbackFailure = new GameTestAssertException("cell count changed", new TestPos(7, 8, 9));
            helper.onEachTick("cell conservation", () -> { throw callbackFailure; });
        }
    }
}
