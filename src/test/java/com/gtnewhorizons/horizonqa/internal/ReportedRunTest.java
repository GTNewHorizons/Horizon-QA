package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.internal.InvalidBatchHook.HookPhase;
import com.gtnewhorizons.horizonqa.report.CaseResult;
import com.gtnewhorizons.horizonqa.report.IssueResult;
import com.gtnewhorizons.horizonqa.report.RunResult;

public class ReportedRunTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String originalUserDir;

    @Before
    public void useTemporaryReportDirectory() {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty(
            "user.dir",
            temporaryFolder.getRoot()
                .getAbsolutePath());
    }

    @After
    public void resetExecution() {
        ReportedRun.shutdown();
        GameTestRunner.shutdown();
        ReportedRun.clearLastResult();
        if (originalUserDir == null) {
            System.clearProperty("user.dir");
        } else {
            System.setProperty("user.dir", originalUserDir);
        }
        LifecycleHooks.calls.clear();
        LifecycleHooks.mutated = false;
    }

    @Test
    public void batchLaunchCannotReplaceInteractiveOwner() {
        int[] interactiveStarts = new int[1];
        int[] configurationChecks = new int[1];
        GameTestRunner interactive = new GameTestRunner();
        assertTrue(
            interactive.tryStart(
                GameTestRunner.Kind.INTERACTIVE,
                () -> interactive.scheduleOnFirstTick(() -> interactiveStarts[0]++)));
        ReportedRun run = new ReportedRun(
            catalog(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap()),
            Collections.emptyList(),
            Collections.emptyList(),
            () -> {
                configurationChecks[0]++;
                return Collections.emptyList();
            });

        ReportedRun.StartStatus status = run.start();
        GameTestRunner.handleTickStart();

        assertEquals(ReportedRun.StartStatus.ALREADY_ACTIVE, status);
        assertEquals(1, interactiveStarts[0]);
        assertEquals(0, configurationChecks[0]);
    }

    @Test
    public void configurationIssuesAreEvaluatedAfterClaimAndBlockExecution() {
        int[] configurationChecks = new int[1];
        IssueResult issue = new IssueResult(
            "config:test",
            "CONFIG_ERROR",
            "horizonqa.configuration",
            "test configuration",
            "invalid configuration",
            "",
            true);
        ReportedRun run = new ReportedRun(
            catalog(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap()),
            Collections.emptyList(),
            Collections.emptyList(),
            () -> {
                configurationChecks[0]++;
                return Collections.singletonList(issue);
            });

        assertEquals(ReportedRun.StartStatus.COMPLETED, run.start());

        assertEquals(1, configurationChecks[0]);
        assertEquals(
            "config:test",
            ReportedRun.lastResult()
                .issues()
                .get(0)
                .id());
        assertEquals(
            2,
            ReportedRun.lastResult()
                .exitCode());
        assertFalse(GameTestRunner.isBatchActive());
    }

    @Test
    public void emptyRunCompletesSynchronouslyAndPublishesReports() {
        ReportedRun run = new ReportedRun(
            catalog(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap()),
            Collections.emptyList(),
            Collections.emptyList());

        ReportedRun.StartStatus status = run.start();

        assertEquals(ReportedRun.StartStatus.COMPLETED, status);
        RunResult result = ReportedRun.lastResult();
        assertNotNull(result);
        assertEquals(0, result.exitCode());
        assertTrue(new File(temporaryFolder.getRoot(), "TEST-horizonqa.xml").isFile());
        assertTrue(new File(temporaryFolder.getRoot(), "horizonqa-result.json").isFile());
        assertFalse(GameTestRunner.isBatchActive());
    }

    @Test
    public void shutdownPublishesAbortedAndUnstartedCasesExactlyOnce() throws Exception {
        List<GameTestDefinition> tests = Collections.singletonList(definition("mod:Suite.pending", true));
        ReportedRun run = new ReportedRun(
            catalog(tests, Collections.emptyMap(), Collections.emptyMap()),
            tests,
            Collections.emptyList());
        assertEquals(ReportedRun.StartStatus.STARTED, run.start());

        assertTrue(ReportedRun.shutdown());
        RunResult result = ReportedRun.lastResult();
        assertFalse(ReportedRun.shutdown());

        assertSame(result, ReportedRun.lastResult());
        assertNotNull(result);
        assertEquals(2, result.exitCode());
        assertEquals(
            1,
            result.cases()
                .size());
        assertEquals(
            CaseResult.Status.NOT_STARTED,
            result.cases()
                .get(0)
                .status());
        assertEquals(
            "EXECUTION_ABORTED",
            result.cases()
                .get(0)
                .failureType());
        assertEquals(
            "runner:executionAborted",
            result.cases()
                .get(0)
                .blockedByIssueId());
        assertEquals(
            "EXECUTION_ABORTED",
            result.issues()
                .get(0)
                .kind());
        assertFalse(GameTestRunner.isBatchActive());
    }

    @Test
    public void beforeFailureStillRunsOwedAfterHooksExactlyOnce() throws Exception {
        Method mutate = LifecycleHooks.class.getMethod("aMutate");
        Method fail = LifecycleHooks.class.getMethod("bFail");
        Method restore = LifecycleHooks.class.getMethod("restore");
        Map<String, List<Method>> before = Collections.singletonMap("cleanup", Arrays.asList(fail, mutate));
        Map<String, List<Method>> after = Collections.singletonMap("cleanup", Collections.singletonList(restore));
        List<GameTestDefinition> tests = Collections.singletonList(definition("mod:Suite.blocked", true, "cleanup"));
        ReportedRun run = new ReportedRun(catalog(tests, before, after), tests, Collections.emptyList());

        assertEquals(ReportedRun.StartStatus.STARTED, run.start());
        GameTestRunner.handleTickStart();

        assertEquals(Arrays.asList("mutate", "fail", "restore"), LifecycleHooks.calls);
        assertFalse(LifecycleHooks.mutated);
        assertEquals(
            2,
            ReportedRun.lastResult()
                .exitCode());
        assertFalse(GameTestRunner.isBatchActive());
    }

    @Test
    public void linkageErrorFromHookIsRethrown() throws Exception {
        Method fatal = FatalHooks.class.getMethod("linkage");

        assertThrows(
            LinkageError.class,
            () -> ReportedRun.invokeHooks(Collections.singletonList(fatal), HookPhase.BEFORE, "fatal", true, 1));
    }

    @Test
    public void sortsHookMethodsByDeclaringClassThenMethodName() throws Exception {
        Method beta = BetaHooks.class.getMethod("beta");
        Method zeta = AlphaHooks.class.getMethod("zeta");
        Method alpha = AlphaHooks.class.getMethod("alpha");

        List<Method> sorted = ReportedRun.sortedHookMethods(Arrays.asList(beta, zeta, alpha));

        assertEquals(Arrays.asList(alpha, zeta, beta), sorted);
    }

    @Test
    public void batchBuilderPreservesParameterizedEncounterOrder() throws Exception {
        Method testMethod = ParameterizedDefinitions.class.getMethod("test", GameTestHelper.class, int.class);
        String baseTestId = "matrix:EncounterOrderTests.inSourceOrder";
        List<GameTestDefinition> definitions = Arrays.asList(
            parameterized(baseTestId, "zeta", 0, testMethod),
            parameterized(baseTestId, "alpha", 1, testMethod),
            parameterized(baseTestId, "middle", 2, testMethod));
        GameTestCatalog catalog = catalog(definitions, Collections.emptyMap(), Collections.emptyMap());

        Method buildBatches = ReportedRun.class.getDeclaredMethod("buildBatches", List.class, GameTestCatalog.class);
        buildBatches.setAccessible(true);
        List<?> batches = (List<?>) buildBatches.invoke(null, definitions, catalog);
        Field tests = batches.get(0)
            .getClass()
            .getDeclaredField("tests");
        tests.setAccessible(true);

        assertEquals(definitions, tests.get(batches.get(0)));
    }

    @Test
    public void batchNamesNormalizeNullAndEmptyToDefault() {
        assertEquals("default", ReportedRun.batchName(null));
        assertEquals("default", ReportedRun.batchName(""));
        assertEquals("assembler", ReportedRun.batchName("assembler"));
        assertEquals("default", ReportedRun.batchId(""));
    }

    @Test
    public void beforeHookFailureCreatesOneRootIssueAndSkippedCases() throws Exception {
        Method shouldNotRun = BeforeHooks.class.getMethod("shouldNotRun");
        Method failFirst = BeforeHooks.class.getMethod("failFirst");
        Method secondFailure = BeforeHooks.class.getMethod("secondFailure");

        BeforeHooks.calls.clear();
        List<Method> hooks = ReportedRun.sortedHookMethods(Arrays.asList(shouldNotRun, secondFailure, failFirst));
        List<IssueResult> issues = ReportedRun.invokeHooks(hooks, HookPhase.BEFORE, "", true, 2);

        assertEquals(1, issues.size());
        IssueResult rootIssue = issues.get(0);
        assertTrue(
            rootIssue.id()
                .startsWith("batchHook:before:default:"));
        assertTrue(
            rootIssue.id()
                .contains("#failFirst"));
        assertEquals("BEFORE_BATCH_ERROR", rootIssue.kind());
        assertTrue(
            rootIssue.details()
                .contains("affectedTests=2"));
        assertEquals(Collections.emptyList(), BeforeHooks.calls);

        List<CaseResult> skipped = ReportedRun.skippedCasesForBeforeFailure(
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
    public void skippedCasesCanUseNonHookInfrastructureFailureTypes() throws Exception {
        IssueResult rootIssue = new IssueResult(
            "runner:worldUnavailable:dimension0",
            "WORLD_UNAVAILABLE",
            "horizonqa.infrastructure",
            "world:dimension0",
            "World dimension 0 is null",
            "issue.id=runner:worldUnavailable:dimension0\n",
            true);

        List<CaseResult> skipped = ReportedRun.skippedCasesForIssue(
            Collections.singletonList(definition("mod:Suite.blocked", true)),
            rootIssue,
            "WORLD_UNAVAILABLE");

        assertEquals(1, skipped.size());
        assertEquals(
            "WORLD_UNAVAILABLE",
            skipped.get(0)
                .failureType());
        assertEquals(
            rootIssue.id(),
            skipped.get(0)
                .blockedByIssueId());
    }

    @Test
    public void afterHookFailuresCreateIssuesAndContinueInOrder() throws Exception {
        Method secondFailure = AfterHooks.class.getMethod("secondFailure");
        Method recordsCall = AfterHooks.class.getMethod("recordsCall");
        Method firstFailure = AfterHooks.class.getMethod("firstFailure");

        AfterHooks.calls.clear();
        List<Method> hooks = ReportedRun.sortedHookMethods(Arrays.asList(secondFailure, recordsCall, firstFailure));
        List<IssueResult> issues = ReportedRun.invokeHooks(hooks, HookPhase.AFTER, "cleanup", false, 0);

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
        return definition(id, required, "");
    }

    private static GameTestDefinition definition(String id, boolean required, String batch) throws Exception {
        return new GameTestDefinition(
            id,
            TestDefinitions.class.getMethod("test", GameTestHelper.class),
            "",
            100,
            batch,
            required,
            0);
    }

    private static GameTestDefinition parameterized(String baseTestId, String caseName, int caseOrdinal,
        Method method) {
        return GameTestDefinition.parameterized(
            baseTestId,
            caseName,
            caseOrdinal,
            method,
            "",
            20,
            "matrix",
            true,
            0,
            new Object[] { caseOrdinal });
    }

    private static GameTestCatalog catalog(List<GameTestDefinition> tests, Map<String, List<Method>> before,
        Map<String, List<Method>> after) {
        return new GameTestCatalog(
            tests,
            before,
            after,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
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

        public static void secondFailure() {
            throw new IllegalStateException("second setup broke");
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

    public static final class LifecycleHooks {

        static final List<String> calls = new ArrayList<>();
        static boolean mutated;

        public static void aMutate() {
            mutated = true;
            calls.add("mutate");
        }

        public static void bFail() {
            calls.add("fail");
            throw new AssertionError("setup broke");
        }

        public static void restore() {
            calls.add("restore");
            mutated = false;
        }
    }

    public static final class FatalHooks {

        public static void linkage() {
            throw new LinkageError("fatal linkage");
        }
    }

    public static final class TestDefinitions {

        public static void test(GameTestHelper helper) {}
    }

    public static final class ParameterizedDefinitions {

        @SuppressWarnings("unused")
        public static void test(GameTestHelper helper, int value) {}
    }
}
