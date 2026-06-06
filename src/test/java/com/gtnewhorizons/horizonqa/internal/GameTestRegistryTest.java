package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.annotation.AfterBatch;
import com.gtnewhorizons.horizonqa.api.annotation.BeforeBatch;
import com.gtnewhorizons.horizonqa.internal.InvalidBatchHook.HookPhase;

public class GameTestRegistryTest {

    @Test
    public void beforeBatchHooksMustReturnVoid() throws Exception {
        Method method = Hooks.class.getMethod("nonVoidBefore");

        List<DiscoveryIssue> issues = collectBatchMethodIssues(method, HookPhase.BEFORE);

        assertEquals(1, issues.size());
        assertEquals(
            "discovery:invalidHook:before:" + Hooks.class.getName() + "#nonVoidBefore:returnType",
            issues.get(0)
                .id());
        assertEquals(
            "DISCOVERY_ERROR",
            issues.get(0)
                .kind());
        assertTrue(
            issues.get(0)
                .message()
                .contains("must return void"));
    }

    @Test
    public void afterBatchHooksMustReturnVoid() throws Exception {
        Method method = Hooks.class.getMethod("nonVoidAfter");

        List<DiscoveryIssue> issues = collectBatchMethodIssues(method, HookPhase.AFTER);

        assertEquals(1, issues.size());
        assertEquals(
            "discovery:invalidHook:after:" + Hooks.class.getName() + "#nonVoidAfter:returnType",
            issues.get(0)
                .id());
        assertEquals(
            "DISCOVERY_ERROR",
            issues.get(0)
                .kind());
        assertTrue(
            issues.get(0)
                .message()
                .contains("must return void"));
    }

    @Test
    public void publicStaticVoidNoArgBatchHooksAreValid() throws Exception {
        Method method = Hooks.class.getMethod("validBefore");

        List<DiscoveryIssue> issues = collectBatchMethodIssues(method, HookPhase.BEFORE);

        assertTrue(issues.isEmpty());
    }

    private static List<DiscoveryIssue> collectBatchMethodIssues(Method method, HookPhase phase) throws Exception {
        Method validator = GameTestRegistry.class
            .getDeclaredMethod("collectBatchMethodIssues", Method.class, HookPhase.class, List.class);
        validator.setAccessible(true);

        List<DiscoveryIssue> issues = new ArrayList<>();
        validator.invoke(null, method, phase, issues);
        return issues;
    }

    public static final class Hooks {

        @BeforeBatch("setup")
        public static void validBefore() {}

        @BeforeBatch("setup")
        public static boolean nonVoidBefore() {
            return true;
        }

        @AfterBatch("setup")
        public static String nonVoidAfter() {
            return "done";
        }
    }
}
