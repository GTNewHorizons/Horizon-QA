package com.gtnewhorizons.horizonqa.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.gtnewhorizons.horizonqa.internal.GameTestDefinition;
import com.gtnewhorizons.horizonqa.internal.GameTestInstance;
import com.gtnewhorizons.horizonqa.internal.GameTestStatus;

public class GameTestHelperTickCallbackTest {

    private static final List<String> EVENTS = new ArrayList<>();
    private static TickCallbackHandle handle;

    @Before
    public void resetFixture() {
        EVENTS.clear();
        handle = null;
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
    }

    @Test
    public void startAndEndSequenceChangesRespectCallbackPhaseOrdering() throws Exception {
        GameTestInstance instance = instance("sequenceWindow");
        instance.start(null);

        tick(instance);

        assertEquals(Arrays.asList("enable at START", "callback", "disable at END"), EVENTS);
        assertFalse(handle.isEnabled());

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
            handle = helper.onEachTick(() -> EVENTS.add("callback"));
        }

        public static void sequenceWindow(GameTestHelper helper) {
            handle = helper.onEachTick(() -> EVENTS.add("callback"));
            handle.disable();
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

            helper.onEachTick(() -> {
                EVENTS.add("first");
                later[0].remove();
                helper.onEachTick(() -> EVENTS.add("added"));
            });
            later[0] = helper.onEachTick(() -> EVENTS.add("later"));
            self[0] = helper.onEachTick(() -> {
                EVENTS.add("self");
                self[0].remove();
            });
        }
    }
}
