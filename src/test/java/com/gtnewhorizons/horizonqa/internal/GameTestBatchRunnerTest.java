package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.internal.InvalidBatchHook.HookPhase;
import com.gtnewhorizons.horizonqa.report.CaseResult;
import com.gtnewhorizons.horizonqa.report.IssueResult;

public class GameTestBatchRunnerTest {

    @Test
    public void sortsHookMethodsByDeclaringClassThenMethodName() throws Exception {
        Method beta = BetaHooks.class.getMethod("beta");
        Method zeta = AlphaHooks.class.getMethod("zeta");
        Method alpha = AlphaHooks.class.getMethod("alpha");

        List<Method> sorted = GameTestBatchRunner.sortedHookMethods(Arrays.asList(beta, zeta, alpha));

        assertEquals(Arrays.asList(alpha, zeta, beta), sorted);
    }

    @Test
    public void beforeHookFailureCreatesOneRootIssueAndSkippedCases() throws Exception {
        Method shouldNotRun = BeforeHooks.class.getMethod("shouldNotRun");
        Method failFirst = BeforeHooks.class.getMethod("failFirst");

        BeforeHooks.calls.clear();
        List<IssueResult> issues = GameTestBatchRunner
            .invokeHooks(Arrays.asList(shouldNotRun, failFirst), HookPhase.BEFORE, "", true, 2);

        assertEquals(1, issues.size());
        IssueResult rootIssue = issues.get(0);
        assertTrue(
            rootIssue.id()
                .startsWith("batchHook:before:default:"));
        assertEquals("BEFORE_BATCH_ERROR", rootIssue.kind());
        assertTrue(
            rootIssue.details()
                .contains("affectedTests=2"));
        assertEquals(Collections.emptyList(), BeforeHooks.calls);

        List<CaseResult> skipped = GameTestBatchRunner.skippedCasesForBeforeFailure(
            Arrays.asList(definition("mod:Suite.required", true), definition("mod:Suite.optional", false)),
            rootIssue);

        assertEquals(2, skipped.size());
        assertEquals(
            CaseResult.Status.NOT_STARTED,
            skipped.get(0)
                .status());
        assertEquals(
            rootIssue.id(),
            skipped.get(0)
                .blockedByIssueId());
        assertTrue(
            skipped.get(0)
                .required());
        assertEquals(
            rootIssue.id(),
            skipped.get(1)
                .blockedByIssueId());
        assertFalse(
            skipped.get(1)
                .required());
    }

    @Test
    public void afterHookFailuresCreateIssuesAndContinueInOrder() throws Exception {
        Method secondFailure = AfterHooks.class.getMethod("secondFailure");
        Method recordsCall = AfterHooks.class.getMethod("recordsCall");
        Method firstFailure = AfterHooks.class.getMethod("firstFailure");

        AfterHooks.calls.clear();
        List<IssueResult> issues = GameTestBatchRunner
            .invokeHooks(Arrays.asList(secondFailure, recordsCall, firstFailure), HookPhase.AFTER, "cleanup", false, 0);

        assertEquals(2, issues.size());
        assertEquals(
            "AFTER_BATCH_ERROR",
            issues.get(0)
                .kind());
        assertTrue(
            issues.get(0)
                .id()
                .contains("#firstFailure"));
        assertTrue(
            issues.get(1)
                .id()
                .contains("#secondFailure"));
        assertEquals(Collections.singletonList("recordsCall"), AfterHooks.calls);
    }

    private static GameTestDefinition definition(String id, boolean required) throws Exception {
        return new GameTestDefinition(
            id,
            TestDefinitions.class.getMethod("test", GameTestHelper.class),
            "",
            100,
            "",
            required,
            0);
    }

    public static final class AlphaHooks {

        public static void alpha() {}

        public static void zeta() {}
    }

    public static final class BetaHooks {

        public static void beta() {}
    }

    public static final class BeforeHooks {

        static final List<String> calls = new ArrayList<>();

        public static void failFirst() {
            throw new IllegalStateException("setup broke");
        }

        public static void shouldNotRun() {
            calls.add("shouldNotRun");
        }
    }

    public static final class AfterHooks {

        static final List<String> calls = new ArrayList<>();

        public static void firstFailure() {
            throw new IllegalStateException("first cleanup broke");
        }

        public static void recordsCall() {
            calls.add("recordsCall");
        }

        public static void secondFailure() {
            throw new IllegalStateException("second cleanup broke");
        }
    }

    public static final class TestDefinitions {

        public static void test(GameTestHelper helper) {}
    }
}
