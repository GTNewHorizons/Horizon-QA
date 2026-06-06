package com.gtnewhorizons.horizonqa.internal;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.event.StructurePlaced;
import com.gtnewhorizons.horizonqa.internal.InvalidBatchHook.HookPhase;
import com.gtnewhorizons.horizonqa.report.CaseResult;
import com.gtnewhorizons.horizonqa.report.ConsoleReporter;
import com.gtnewhorizons.horizonqa.report.IssueResult;
import com.gtnewhorizons.horizonqa.report.JUnitXmlReporter;
import com.gtnewhorizons.horizonqa.report.RunResult;
import com.gtnewhorizons.horizonqa.report.StatusJsonReporter;
import com.gtnewhorizons.horizonqa.structure.HybridStructureLoader;
import com.gtnewhorizons.horizonqa.structure.HybridStructureTemplate;
import com.gtnewhorizons.horizonqa.structure.StructurePlacer;
import com.gtnewhorizons.horizonqa.structure.TemplateException;

import cpw.mods.fml.common.FMLCommonHandler;

public class GameTestBatchRunner {

    private static final Logger LOG = LogManager.getLogger("GameTest");
    private static final Comparator<Method> METHOD_ORDER = Comparator.comparing(
        (Method m) -> m.getDeclaringClass()
            .getName())
        .thenComparing(Method::getName);

    private final List<Batch> batches;
    private final GameTestRunner runner;
    private final GameTestGridLayout grid;
    private final List<ResultEntry> resultEntries = new ArrayList<>();
    private final List<IssueResult> issues = new ArrayList<>();

    public GameTestBatchRunner(List<GameTestDefinition> tests, Map<String, List<Method>> beforeBatchMethods,
        Map<String, List<Method>> afterBatchMethods) {
        this(tests, beforeBatchMethods, afterBatchMethods, Collections.emptyList());
    }

    public GameTestBatchRunner(List<GameTestDefinition> tests, Map<String, List<Method>> beforeBatchMethods,
        Map<String, List<Method>> afterBatchMethods, List<IssueResult> issues) {
        runner = new GameTestRunner();
        grid = new GameTestGridLayout();
        batches = buildBatches(tests, beforeBatchMethods, afterBatchMethods);
        if (issues != null) {
            this.issues.addAll(issues);
        }
    }

    public void start() {
        runner.register();
        if (batches.isEmpty()) {
            onAllBatchesDone();
            return;
        }
        // Placement and getTileEntity are unreliable during FMLServerStartingEvent (before the first server
        // tick). Defer until the world has ticked once, matching /gametest runAll during normal gameplay.
        runner.scheduleOnFirstTick(() -> runBatch(0));
    }

    private void runBatch(int idx) {
        Batch batch = batches.get(idx);
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
            onAllBatchesDone();
            return;
        }

        List<PlannedTest> planned = new ArrayList<>(batch.tests.size());
        for (GameTestDefinition def : batch.tests) {
            planned.add(plan(def, world));
        }

        for (PlannedTest p : planned) {
            if (p.hasTemplateError()) {
                continue;
            }
            TestCellScanner
                .preClearWithMargin(world, p.cellMinX, p.cellMinY, p.cellMinZ, p.cellMaxX, p.cellMaxY, p.cellMaxZ);
        }

        for (int i = 0; i < planned.size(); i++) {
            PlannedTest p = planned.get(i);
            if (p.hasTemplateError()) {
                continue;
            }
            if (p.template != null) {
                try {
                    StructurePlacer
                        .placeStrict(p.def.getTemplateName(), p.template, world, p.originX, p.originY, p.originZ);
                } catch (TemplateException e) {
                    planned.set(i, p.withTemplateError(templateErrorCase(p.def, e)));
                }
            }
        }

        List<GameTestInstance> batchInstances = new ArrayList<>(planned.size());
        for (PlannedTest p : planned) {
            if (p.hasTemplateError()) {
                resultEntries.add(ResultEntry.result(p.templateError));
                continue;
            }
            GameTestInstance inst = new GameTestInstance(p.def, p.originX, p.originY, p.originZ);
            if (p.template != null) {
                final String templateName = p.def.getTemplateName();
                final int sx = p.tmplSizeX, sy = p.tmplSizeY, sz = p.tmplSizeZ;
                final TestPos origin = new TestPos(p.originX, p.originY, p.originZ);
                TestEventRecorder rec = inst.getRecorder();
                rec.record(
                    () -> new StructurePlaced(
                        rec.clock()
                            .tick(),
                        templateName,
                        origin,
                        sx,
                        sy,
                        sz));
            }

            int tmplMaxX = p.tmplSizeX > 0 ? p.originX + p.tmplSizeX - 1 : -1;
            int tmplMaxY = p.tmplSizeY > 0 ? p.originY + p.tmplSizeY - 1 : -1;
            int tmplMaxZ = p.tmplSizeZ > 0 ? p.originZ + p.tmplSizeZ - 1 : -1;
            TestCellScanner.registerIsolationCheck(
                inst,
                world,
                p.cellMinX,
                p.cellMinY,
                p.cellMinZ,
                p.cellMaxX,
                p.cellMaxY,
                p.cellMaxZ,
                p.originX,
                p.originY,
                p.originZ,
                tmplMaxX,
                tmplMaxY,
                tmplMaxZ,
                p.template != null);

            inst.start(world);
            batchInstances.add(inst);
            resultEntries.add(ResultEntry.instance(inst));
        }

        runner.run(batchInstances, () -> {
            issues.addAll(invokeHooks(batch.afterMethods, HookPhase.AFTER, batch.name, false, 0));
            runNextBatchOrFinish(idx);
        });
    }

    private void onAllBatchesDone() {
        runner.unregister();
        HorizonQAMod.CHUNK_LOADER.releaseAll();

        File reportFile = HorizonQAProperties.junitReportFile();
        RunResult result = RunResult
            .completedCases(HorizonQAProperties.modeName(), collectCaseResults(), issues, reportFile.getPath());

        try {
            JUnitXmlReporter.write(result, reportFile);
            LOG.info("JUnit XML report written to {}", reportFile.getAbsolutePath());
        } catch (IOException e) {
            LOG.error("Failed to write JUnit XML report: {}", e.getMessage());
            result = result.withAdditionalIssue(IssueResult.reporting("junit", reportFile.getAbsolutePath(), e));
        }

        File statusFile = HorizonQAProperties.statusReportFile();
        try {
            StatusJsonReporter.write(result, statusFile);
            LOG.info("Status JSON report written to {}", statusFile.getAbsolutePath());
        } catch (IOException e) {
            LOG.error("Failed to write status JSON report: {}", e.getMessage());
            result = result.withAdditionalIssue(IssueResult.reporting("status", statusFile.getAbsolutePath(), e));
        }

        if (HorizonQAProperties.isCi()) {
            LOG.info(
                "CI mode: exiting with code {} ({} required test failure/timeout(s), {} incomplete test(s), {} infrastructure issue(s)).",
                result.exitCode(),
                result.requiredFailures(),
                result.incomplete(),
                result.infrastructureErrors());
        }
        ConsoleReporter.report(result);
        if (HorizonQAProperties.isCi()) {
            FMLCommonHandler.instance()
                .exitJava(result.exitCode(), false);
        }
    }

    private void runNextBatchOrFinish(int idx) {
        int next = idx + 1;
        if (next < batches.size()) {
            runBatch(next);
        } else {
            onAllBatchesDone();
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

    private PlannedTest plan(GameTestDefinition def, WorldServer world) {
        HybridStructureTemplate template;
        try {
            template = loadTemplate(def);
        } catch (IOException e) {
            return PlannedTest.templateError(def, templateErrorCase(def, e));
        }

        int sizeX = template != null ? template.getSizeX() : 0;
        int sizeY = template != null ? template.getSizeY() : 0;
        int sizeZ = template != null ? template.getSizeZ() : 0;
        int[] origin = grid.allocateOrigin(sizeX, sizeZ);

        int cellSizeX = Math.max(sizeX, GameTestGridLayout.DEFAULT_CELL_SIZE);
        int cellSizeY = Math.max(sizeY, 1);
        int cellSizeZ = Math.max(sizeZ, GameTestGridLayout.DEFAULT_CELL_SIZE);

        int cellMinX = origin[0];
        int cellMinY = origin[1];
        int cellMinZ = origin[2];
        int cellMaxX = origin[0] + cellSizeX - 1;
        int cellMaxY = origin[1] + cellSizeY - 1;
        int cellMaxZ = origin[2] + cellSizeZ - 1;

        try {
            HorizonQAMod.CHUNK_LOADER
                .forceChunksStrict(world, cellMinX, cellMinY, cellMinZ, cellMaxX, cellMaxY, cellMaxZ);
        } catch (TemplateException e) {
            return PlannedTest.templateError(def, templateErrorCase(def, e));
        }

        return new PlannedTest(
            def,
            template,
            origin[0],
            origin[1],
            origin[2],
            sizeX,
            sizeY,
            sizeZ,
            cellMinX,
            cellMinY,
            cellMinZ,
            cellMaxX,
            cellMaxY,
            cellMaxZ,
            null);
    }

    private static HybridStructureTemplate loadTemplate(GameTestDefinition def) throws IOException {
        if (def.getTemplateName()
            .isEmpty()) return null;
        return HybridStructureLoader.load(def.getTemplateName());
    }

    private static CaseResult templateErrorCase(GameTestDefinition def, Throwable error) {
        String message = errorMessage(error);
        LOG.error("Template setup failed for test '{}': {}", def.getTestId(), message, error);
        return CaseResult.templateError(def, message, error);
    }

    private static List<Batch> buildBatches(List<GameTestDefinition> tests, Map<String, List<Method>> beforeMethods,
        Map<String, List<Method>> afterMethods) {

        Map<String, List<GameTestDefinition>> testsByBatch = new TreeMap<>();
        for (GameTestDefinition def : tests) {
            testsByBatch.computeIfAbsent(def.getBatch(), k -> new ArrayList<>())
                .add(def);
        }

        List<Batch> result = new ArrayList<>();
        for (Map.Entry<String, List<GameTestDefinition>> entry : testsByBatch.entrySet()) {
            entry.getValue()
                .sort(Comparator.comparing(GameTestDefinition::getTestId));
            String name = entry.getKey();
            List<Method> before = sortedHookMethods(beforeMethods == null ? null : beforeMethods.get(name));
            List<Method> after = sortedHookMethods(afterMethods == null ? null : afterMethods.get(name));
            result.add(new Batch(name, entry.getValue(), before, after));
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
    }

    @Desugar
    private record PlannedTest(GameTestDefinition def, HybridStructureTemplate template, int originX, int originY,
        int originZ, int tmplSizeX, int tmplSizeY, int tmplSizeZ, int cellMinX, int cellMinY, int cellMinZ,
        int cellMaxX, int cellMaxY, int cellMaxZ, CaseResult templateError) {

        static PlannedTest templateError(GameTestDefinition def, CaseResult result) {
            return new PlannedTest(def, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, result);
        }

        boolean hasTemplateError() {
            return templateError != null;
        }

        PlannedTest withTemplateError(CaseResult result) {
            return new PlannedTest(
                def,
                template,
                originX,
                originY,
                originZ,
                tmplSizeX,
                tmplSizeY,
                tmplSizeZ,
                cellMinX,
                cellMinY,
                cellMinZ,
                cellMaxX,
                cellMaxY,
                cellMaxZ,
                result);
        }

    }
}
