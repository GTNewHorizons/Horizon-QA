package com.gtnewhorizons.horizonqa.internal;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;
import com.gtnewhorizons.horizonqa.internal.GameTestCatalog.BatchHooks;
import com.gtnewhorizons.horizonqa.internal.InvalidBatchHook.HookPhase;
import com.gtnewhorizons.horizonqa.report.CaseResult;
import com.gtnewhorizons.horizonqa.report.IssueResult;
import com.gtnewhorizons.horizonqa.report.ReportPathPreflight;
import com.gtnewhorizons.horizonqa.report.RunReportWriter;
import com.gtnewhorizons.horizonqa.report.RunResult;

import cpw.mods.fml.common.FMLCommonHandler;

public final class ReportedRun {

    private static final Logger LOG = LogManager.getLogger("GameTest");
    private static final Comparator<Method> METHOD_ORDER = Comparator.comparing(
        (Method m) -> m.getDeclaringClass()
            .getName())
        .thenComparing(Method::getName);

    private static ReportedRun currentRun;
    private static RunResult lastResult;

    private final List<Batch> batches;
    private final List<GameTestDefinition> runnableTests;
    private final GameTestRunner runner;
    private final FixturePreparation fixturePreparation;
    private final Supplier<List<IssueResult>> configurationIssues;
    private final List<ResultEntry> resultEntries = new ArrayList<>();
    private final List<IssueResult> issues = new ArrayList<>();
    private Batch activeBatch;
    private boolean afterHooksOwed;
    private boolean finishing;
    private boolean finished;
    private File junitReportFile = new File("TEST-horizonqa.xml");
    private File statusReportFile = new File("horizonqa-result.json");
    private String mode = "";
    private boolean reportOutputsReady;
    private boolean exitWhenComplete;

    public ReportedRun(GameTestCatalog catalog, List<GameTestDefinition> tests, List<IssueResult> issues) {
        this(catalog, tests, issues, Collections::emptyList);
    }

    public ReportedRun(GameTestCatalog catalog, List<GameTestDefinition> tests, List<IssueResult> issues,
        Supplier<List<IssueResult>> configurationIssues) {
        runner = new GameTestRunner();
        fixturePreparation = new FixturePreparation();
        this.configurationIssues = Objects.requireNonNull(configurationIssues, "configurationIssues");
        runnableTests = new ArrayList<>();
        for (GameTestDefinition test : tests) {
            if (test.isSkippedAtDiscovery()) {
                resultEntries.add(
                    ResultEntry.result(
                        CaseResult.skipped(test, test.getDiscoverySkipReason(), CaseResult.MISSING_REQUIRED_MOD)));
            } else {
                runnableTests.add(test);
            }
        }
        batches = buildBatches(runnableTests, Objects.requireNonNull(catalog, "catalog"));
        if (issues != null) {
            this.issues.addAll(issues);
        }
    }

    private ReportedRun(Supplier<List<IssueResult>> configurationIssues) {
        runner = new GameTestRunner();
        fixturePreparation = new FixturePreparation();
        this.configurationIssues = Objects.requireNonNull(configurationIssues, "configurationIssues");
        runnableTests = Collections.emptyList();
        batches = Collections.emptyList();
    }

    public static ReportedRun configurationFailure(Supplier<List<IssueResult>> configurationIssues) {
        return new ReportedRun(configurationIssues);
    }

    public StartStatus start() {
        boolean acquired = runner.tryStart(GameTestRunner.Kind.BATCH, () -> {
            setCurrent(this);
            try {
                startClaimed();
            } catch (RuntimeException e) {
                abortAndFinish("Reported run failed during startup", e, true);
            } catch (Error e) {
                if (FatalErrors.isFatal(e)) {
                    cleanupAfterFatal("Reported run failed during startup", e);
                    throw e;
                }
                abortAndFinish("Reported run failed during startup", e, true);
            }
        });
        if (!acquired) return StartStatus.ALREADY_ACTIVE;
        return finished ? StartStatus.COMPLETED : StartStatus.STARTED;
    }

    public static boolean shutdown() {
        ReportedRun run = current();
        if (run == null) return false;
        run.abortAndFinish("Server stopped before reported run completion", null, false);
        return true;
    }

    public static synchronized RunResult lastResult() {
        return lastResult;
    }

    public static synchronized void clearLastResult() {
        lastResult = null;
    }

    private void startClaimed() {
        List<IssueResult> blockingIssues = Objects
            .requireNonNull(configurationIssues.get(), "configurationIssues.get()");
        if (!blockingIssues.isEmpty()) {
            issues.clear();
            issues.addAll(blockingIssues);
        }

        mode = HorizonQAProperties.modeName();
        junitReportFile = HorizonQAProperties.junitReportFile();
        statusReportFile = HorizonQAProperties.statusReportFile();
        exitWhenComplete = HorizonQAProperties.stopServerAfterRun() || HorizonQAProperties.hasModeError();

        List<IssueResult> pathIssues = ReportPathPreflight.check(junitReportFile, statusReportFile);
        if (!pathIssues.isEmpty()) {
            LOG.error("Report path preflight failed; reported run was not launched.");
            for (IssueResult issue : pathIssues) {
                LOG.error("Infrastructure issue [{}] {}: {}", issue.id(), issue.name(), issue.message());
            }
            issues.addAll(pathIssues);
            finish(false, exitWhenComplete);
            return;
        }
        reportOutputsReady = true;

        if (!blockingIssues.isEmpty() || batches.isEmpty()) {
            finish(true, exitWhenComplete);
            return;
        }

        // Batch setup runs in the first START phase, before the world tick. Instances created here receive their
        // first START callback in this phase; batches created from an END completion begin on the next START.
        runner.scheduleOnFirstTick(() -> runBatchSafely(0));
    }

    private void runBatchSafely(int idx) {
        try {
            runBatch(idx);
        } catch (RuntimeException e) {
            abortAndFinish("Reported run failed during batch execution", e, true);
        } catch (Error e) {
            if (FatalErrors.isFatal(e)) {
                cleanupAfterFatal("Reported run failed during batch execution", e);
                throw e;
            }
            abortAndFinish("Reported run failed during batch execution", e, true);
        }
    }

    private void runBatch(int idx) {
        Batch batch = batches.get(idx);
        activeBatch = batch;
        afterHooksOwed = true;
        LOG.info("--- Batch '{}' ({} test(s)) ---", batch.name, batch.tests.size());

        List<IssueResult> beforeIssues = invokeHooks(
            batch.beforeMethods,
            HookPhase.BEFORE,
            batch.name,
            true,
            batch.tests.size());
        if (!beforeIssues.isEmpty()) {
            IssueResult rootIssue = beforeIssues.get(0);
            issues.add(rootIssue);
            for (CaseResult skippedCase : skippedCasesForBeforeFailure(batch.tests, rootIssue)) {
                resultEntries.add(ResultEntry.result(skippedCase));
            }
            invokeOwedAfterHooks();
            runNextBatchOrFinish(idx);
            return;
        }

        WorldServer world = MinecraftServer.getServer()
            .worldServerForDimension(0);
        if (world == null) {
            IssueResult rootIssue = worldUnavailableIssue(batch.name, remainingTestCount(idx));
            LOG.error(rootIssue.message());
            issues.add(rootIssue);
            for (CaseResult skippedCase : skippedCasesForIssue(remainingTests(idx), rootIssue, "WORLD_UNAVAILABLE")) {
                resultEntries.add(ResultEntry.result(skippedCase));
            }
            invokeOwedAfterHooks();
            finish(true, exitWhenComplete);
            return;
        }

        List<FixturePreparation.Result> prepared;
        try {
            prepared = fixturePreparation.prepare(world, batch.tests);
        } catch (GameTestInfrastructureException e) {
            IssueResult rootIssue = fixturePreparationIssue(batch.name, batch.tests.size(), e);
            issues.add(rootIssue);
            for (CaseResult skippedCase : skippedCasesForIssue(batch.tests, rootIssue, CaseResult.TEMPLATE_ERROR)) {
                resultEntries.add(ResultEntry.result(skippedCase));
            }
            invokeOwedAfterHooks();
            runNextBatchOrFinish(idx);
            return;
        }

        List<GameTestInstance> batchInstances = new ArrayList<>(prepared.size());
        for (FixturePreparation.Result result : prepared) {
            if (!result.isReady()) {
                Throwable cause = result.failure()
                    .getCause();
                resultEntries.add(
                    ResultEntry.result(
                        CaseResult.templateError(
                            result.definition(),
                            result.failure()
                                .getMessage(),
                            cause != null ? cause : result.failure())));
                continue;
            }
            GameTestInstance inst = result.instance();
            batchInstances.add(inst);
        }

        runner.run(batchInstances, () -> {
            invokeOwedAfterHooks();
            runNextBatchOrFinish(idx);
        });
        for (GameTestInstance instance : batchInstances) {
            resultEntries.add(ResultEntry.instance(instance));
            instance.start(world);
        }
    }

    private void runNextBatchOrFinish(int idx) {
        int next = idx + 1;
        if (next < batches.size()) {
            runBatchSafely(next);
        } else {
            finish(true, exitWhenComplete);
        }
    }

    private void abortAndFinish(String message, Throwable cause, boolean allowExit) {
        if (finishing || finished) return;
        IssueResult rootIssue = executionAbortedIssue(message, cause);
        issues.add(rootIssue);
        try {
            runner.abortIfActive(message, cause);
        } catch (Error e) {
            if (FatalErrors.isFatal(e)) {
                cleanupAfterFatal(message, e);
                throw e;
            }
            issues.add(cleanupIssue("Execution cleanup failed: " + errorMessage(e), e));
        } catch (RuntimeException e) {
            issues.add(cleanupIssue("Execution cleanup failed: " + errorMessage(e), e));
        }
        invokeOwedAfterHooks();
        addUnstartedCases(rootIssue);
        finish(reportOutputsReady, allowExit && exitWhenComplete);
    }

    private void cleanupAfterFatal(String message, Error cause) {
        try {
            runner.abortIfActive(message, cause);
        } catch (Throwable ignored) {}
        try {
            invokeOwedAfterHooks();
        } catch (Throwable ignored) {}
        try {
            HorizonQAMod.CHUNK_LOADER.releaseAll();
        } catch (Throwable ignored) {}
        finishing = false;
        finished = true;
        clearCurrent(this);
    }

    private void invokeOwedAfterHooks() {
        if (!afterHooksOwed || activeBatch == null) return;
        Batch batch = activeBatch;
        afterHooksOwed = false;
        activeBatch = null;
        issues.addAll(invokeHooks(batch.afterMethods, HookPhase.AFTER, batch.name, false, 0));
    }

    private void addUnstartedCases(IssueResult rootIssue) {
        Set<String> represented = new LinkedHashSet<>();
        for (ResultEntry entry : resultEntries) {
            represented.add(entry.testId());
        }
        for (GameTestDefinition test : runnableTests) {
            if (represented.add(test.getTestId())) {
                resultEntries.add(
                    ResultEntry.result(
                        CaseResult.skippedByIssue(test, rootIssue.id(), rootIssue.message(), "EXECUTION_ABORTED")));
            }
        }
    }

    private void finish(boolean writeFiles, boolean exit) {
        if (finishing || finished) return;
        finishing = true;
        try {
            invokeOwedAfterHooks();
            releaseChunks();

            RunResult result = RunResult.completedCases(mode, collectCaseResults(), issues, junitReportFile.getPath());
            result = writeFiles ? RunReportWriter.write(result, junitReportFile, statusReportFile, LOG)
                : RunReportWriter.writeConsole(result, LOG);
            publish(result);
            finished = true;

            if (exit) {
                LOG.info(
                    "Stopping server with code {} ({} required test failure/timeout(s), {} incomplete test(s), {} infrastructure issue(s)).",
                    result.exitCode(),
                    result.requiredFailures(),
                    result.incomplete(),
                    result.infrastructureErrors());
                FMLCommonHandler.instance()
                    .exitJava(result.exitCode(), false);
            }
        } finally {
            finishing = false;
            clearCurrent(this);
        }
    }

    private List<CaseResult> collectCaseResults() {
        List<CaseResult> cases = new ArrayList<>(resultEntries.size());
        for (ResultEntry entry : resultEntries) {
            cases.add(entry.toCaseResult());
        }
        return cases;
    }

    static List<IssueResult> invokeHooks(List<Method> methods, HookPhase phase, String batch, boolean stopOnFailure,
        int affectedTests) {
        List<IssueResult> failures = new ArrayList<>();
        List<Method> orderedMethods = methods == null ? Collections.emptyList() : methods;
        for (Method m : orderedMethods) {
            try {
                m.invoke(null);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                FatalErrors.rethrow(cause);
                IssueResult issue = hookIssue(phase, batch, m, cause, affectedTests);
                logHookIssue(phase, batch, m, cause);
                failures.add(issue);
                if (stopOnFailure) {
                    return failures;
                }
            } catch (IllegalAccessException | IllegalArgumentException e) {
                IssueResult issue = hookIssue(phase, batch, m, e, affectedTests);
                logHookIssue(phase, batch, m, e);
                failures.add(issue);
                if (stopOnFailure) {
                    return failures;
                }
            }
        }
        return failures;
    }

    static List<CaseResult> skippedCasesForBeforeFailure(List<GameTestDefinition> tests, IssueResult rootIssue) {
        return skippedCasesForIssue(tests, rootIssue, "BATCH_HOOK_ERROR");
    }

    static List<CaseResult> skippedCasesForIssue(List<GameTestDefinition> tests, IssueResult rootIssue,
        String failureType) {
        List<CaseResult> skipped = new ArrayList<>();
        for (GameTestDefinition test : tests) {
            skipped.add(CaseResult.skippedByIssue(test, rootIssue.id(), rootIssue.message(), failureType));
        }
        return skipped;
    }

    static List<Method> sortedHookMethods(List<Method> methods) {
        if (methods == null || methods.isEmpty()) {
            return Collections.emptyList();
        }
        List<Method> sorted = new ArrayList<>(methods);
        sorted.sort(METHOD_ORDER);
        return sorted;
    }

    private int remainingTestCount(int batchIndex) {
        int count = 0;
        for (int i = batchIndex; i < batches.size(); i++) {
            count += batches.get(i).tests.size();
        }
        return count;
    }

    private List<GameTestDefinition> remainingTests(int batchIndex) {
        List<GameTestDefinition> tests = new ArrayList<>();
        for (int i = batchIndex; i < batches.size(); i++) {
            tests.addAll(batches.get(i).tests);
        }
        return tests;
    }

    private static List<Batch> buildBatches(List<GameTestDefinition> tests, GameTestCatalog catalog) {

        Map<String, List<GameTestDefinition>> testsByBatch = new TreeMap<>();
        for (GameTestDefinition def : tests) {
            testsByBatch.computeIfAbsent(def.getBatch(), k -> new ArrayList<>())
                .add(def);
        }

        List<Batch> result = new ArrayList<>();
        for (Map.Entry<String, List<GameTestDefinition>> entry : testsByBatch.entrySet()) {
            entry.getValue()
                .sort(GameTestDefinition.executionOrder());
            String name = entry.getKey();
            BatchHooks hooks = catalog.batchHooks(name);
            result.add(
                new Batch(
                    name,
                    entry.getValue(),
                    sortedHookMethods(hooks.beforeMethods()),
                    sortedHookMethods(hooks.afterMethods())));
        }
        return result;
    }

    private static IssueResult hookIssue(HookPhase phase, String batch, Method method, Throwable error,
        int affectedTests) {
        String phaseName = phaseName(phase);
        String methodRef = methodRef(method);
        String id = "batchHook:" + phase.name()
            .toLowerCase() + ":" + batchId(batch) + ":" + methodRef;
        String message = "@" + phaseName
            + " method '"
            + methodRef
            + "' failed for batch '"
            + batchName(batch)
            + "': "
            + errorMessage(error);
        StringBuilder details = new StringBuilder();
        details.append("issue.id=")
            .append(id)
            .append('\n');
        details.append("phase=")
            .append(phaseName)
            .append('\n');
        details.append("batch=")
            .append(batchName(batch))
            .append('\n');
        details.append("method=")
            .append(methodRef)
            .append('\n');
        if (phase == HookPhase.BEFORE) {
            details.append("affectedTests=")
                .append(affectedTests)
                .append('\n');
        }

        return new IssueResult(
            id,
            phase == HookPhase.BEFORE ? "BEFORE_BATCH_ERROR" : "AFTER_BATCH_ERROR",
            "horizonqa.infrastructure",
            "batch-hook:" + phase.name()
                .toLowerCase() + ":" + batchName(batch) + ":" + methodRef,
            message,
            details.toString(),
            true,
            stackTrace(error));
    }

    private static IssueResult worldUnavailableIssue(String batch, int affectedTests) {
        String id = "runner:worldUnavailable:dimension0";
        String message = "World dimension 0 is null; cannot start batch '" + batchName(batch) + "' or remaining tests.";
        String details = "issue.id=" + id
            + "\nkind=WORLD_UNAVAILABLE\nbatch="
            + batchName(batch)
            + "\ndimension=0\naffectedTests="
            + affectedTests
            + "\n";
        return new IssueResult(
            id,
            "WORLD_UNAVAILABLE",
            "horizonqa.infrastructure",
            "world:dimension0",
            message,
            details,
            true);
    }

    private static IssueResult fixturePreparationIssue(String batch, int affectedTests,
        GameTestInfrastructureException error) {
        String id = "runner:fixturePreparation:" + batchId(batch);
        String message = "Fixture preparation failed for batch '" + batchName(batch) + "': " + errorMessage(error);
        String details = "issue.id=" + id
            + "\nkind="
            + CaseResult.TEMPLATE_ERROR
            + "\nbatch="
            + batchName(batch)
            + "\naffectedTests="
            + affectedTests
            + "\n";
        return new IssueResult(
            id,
            CaseResult.TEMPLATE_ERROR,
            "horizonqa.infrastructure",
            "fixture-preparation:" + batchName(batch),
            message,
            details,
            true,
            stackTrace(error));
    }

    private static IssueResult executionAbortedIssue(String message, Throwable error) {
        String id = "runner:executionAborted";
        String details = "issue.id=" + id + "\nkind=EXECUTION_ABORTED\n";
        return new IssueResult(
            id,
            "EXECUTION_ABORTED",
            "horizonqa.infrastructure",
            "reported-run:execution",
            message,
            details,
            true,
            stackTrace(error));
    }

    private static IssueResult cleanupIssue(String message, Throwable error) {
        String id = "runner:cleanup";
        return new IssueResult(
            id,
            CaseResult.CLEANUP_ERROR,
            "horizonqa.infrastructure",
            "reported-run:cleanup",
            message,
            "issue.id=" + id + "\nkind=" + CaseResult.CLEANUP_ERROR + "\n",
            true,
            stackTrace(error));
    }

    private void releaseChunks() {
        try {
            HorizonQAMod.CHUNK_LOADER.releaseAll();
        } catch (RuntimeException e) {
            issues.add(cleanupIssue("Chunk release failed: " + errorMessage(e), e));
        } catch (Error e) {
            if (FatalErrors.isFatal(e)) throw e;
            issues.add(cleanupIssue("Chunk release failed: " + errorMessage(e), e));
        }
    }

    private static void logHookIssue(HookPhase phase, String batch, Method method, Throwable error) {
        LOG.error(
            "Exception in @{} method '{}' for batch '{}': {}",
            phaseName(phase),
            methodRef(method),
            batchName(batch),
            errorMessage(error),
            error);
    }

    private static String phaseName(HookPhase phase) {
        return phase == HookPhase.BEFORE ? "BeforeBatch" : "AfterBatch";
    }

    static String batchName(String batch) {
        return batch == null || batch.isEmpty() ? "default" : batch;
    }

    static String batchId(String batch) {
        return batchName(batch);
    }

    private static String methodRef(Method method) {
        return method.getDeclaringClass()
            .getName() + "#"
            + method.getName();
    }

    private static String errorMessage(Throwable error) {
        if (error == null) {
            return "unknown hook error";
        }
        String message = error.getMessage();
        if (message == null || message.isEmpty()) {
            return error.getClass()
                .getName();
        }
        return message;
    }

    private static String stackTrace(Throwable error) {
        if (error == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static synchronized ReportedRun current() {
        return currentRun;
    }

    private static synchronized void setCurrent(ReportedRun run) {
        currentRun = run;
    }

    private static synchronized void clearCurrent(ReportedRun run) {
        if (currentRun == run) currentRun = null;
    }

    private static synchronized void publish(RunResult result) {
        lastResult = result;
    }

    public enum StartStatus {
        STARTED,
        COMPLETED,
        ALREADY_ACTIVE
    }

    private static final class Batch {

        final String name;
        final List<GameTestDefinition> tests;
        final List<Method> beforeMethods;
        final List<Method> afterMethods;

        Batch(String name, List<GameTestDefinition> tests, List<Method> before, List<Method> after) {
            this.name = name;
            this.tests = tests;
            this.beforeMethods = before;
            this.afterMethods = after;
        }
    }

    @Desugar
    private record ResultEntry(GameTestInstance instance, CaseResult result) {

        static ResultEntry instance(GameTestInstance instance) {
            return new ResultEntry(instance, null);
        }

        static ResultEntry result(CaseResult result) {
            return new ResultEntry(null, result);
        }

        CaseResult toCaseResult() {
            return result != null ? result : CaseResult.from(instance);
        }

        String testId() {
            return result != null ? result.id()
                : instance.getDefinition()
                    .getTestId();
        }
    }

}
