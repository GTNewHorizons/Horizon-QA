package com.gtnewhorizons.horizonqa.internal;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.horizonqa.GameTestJvmFlags;
import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.event.StructurePlaced;
import com.gtnewhorizons.horizonqa.report.ConsoleReporter;
import com.gtnewhorizons.horizonqa.report.JUnitXmlReporter;
import com.gtnewhorizons.horizonqa.structure.HybridStructureLoader;
import com.gtnewhorizons.horizonqa.structure.HybridStructureTemplate;
import com.gtnewhorizons.horizonqa.structure.StructurePlacer;

import cpw.mods.fml.common.FMLCommonHandler;

public class GameTestBatchRunner {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private final List<Batch> batches;
    private final GameTestRunner runner;
    private final GameTestGridLayout grid;
    private final List<GameTestInstance> allInstances = new ArrayList<>();

    public GameTestBatchRunner(List<GameTestDefinition> tests, Map<String, List<Method>> beforeBatchMethods,
        Map<String, List<Method>> afterBatchMethods) {
        runner = new GameTestRunner();
        grid = new GameTestGridLayout();
        batches = buildBatches(tests, beforeBatchMethods, afterBatchMethods);
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

        invokeMethods(batch.beforeMethods, "BeforeBatch");

        WorldServer world = MinecraftServer.getServer()
            .worldServerForDimension(0);
        if (world == null) {
            LOG.error("World dimension 0 is null - cannot start tests.");
            onAllBatchesDone();
            return;
        }

        List<PlannedTest> planned = new ArrayList<>(batch.tests.size());
        for (GameTestDefinition def : batch.tests) {
            planned.add(plan(def, world));
        }

        for (PlannedTest p : planned) {
            TestCellScanner
                .preClearWithMargin(world, p.cellMinX, p.cellMinY, p.cellMinZ, p.cellMaxX, p.cellMaxY, p.cellMaxZ);
        }

        for (PlannedTest p : planned) {
            if (p.template != null) {
                StructurePlacer.place(p.template, world, p.originX, p.originY, p.originZ);
            }
        }

        List<GameTestInstance> batchInstances = new ArrayList<>(planned.size());
        for (PlannedTest p : planned) {
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
            allInstances.add(inst);
        }

        runner.run(batchInstances, () -> {
            invokeMethods(batch.afterMethods, "AfterBatch");
            int next = idx + 1;
            if (next < batches.size()) {
                runBatch(next);
            } else {
                onAllBatchesDone();
            }
        });
    }

    private void onAllBatchesDone() {
        runner.unregister();
        HorizonQAMod.CHUNK_LOADER.releaseAll();

        ConsoleReporter.report(allInstances);

        File reportFile = new File("TEST-gametest.xml");
        try {
            JUnitXmlReporter.write(allInstances, reportFile);
            LOG.info("JUnit XML report written to {}", reportFile.getAbsolutePath());
        } catch (IOException e) {
            LOG.error("Failed to write JUnit XML report: {}", e.getMessage());
        }

        if (GameTestJvmFlags.isEnabled()) {
            long requiredFailures = countRequiredFailures();
            int exitCode = (int) Math.min(requiredFailures, 127);
            LOG.info("CI mode: exiting with code {} ({} required test(s) failed).", exitCode, requiredFailures);
            FMLCommonHandler.instance()
                .exitJava(exitCode, false);
        }
    }

    private long countRequiredFailures() {
        long count = 0;
        for (GameTestInstance inst : allInstances) {
            if (inst.getDefinition()
                .isRequired() && inst.getStatus() != GameTestStatus.PASSED) {
                count++;
            }
        }
        return count;
    }

    private static void invokeMethods(List<Method> methods, String phase) {
        for (Method m : methods) {
            try {
                m.invoke(null);
            } catch (InvocationTargetException e) {
                LOG.error(
                    "Exception in @{} method '{}': {}",
                    phase,
                    m.getName(),
                    e.getCause() != null ? e.getCause()
                        .getMessage() : e.getMessage());
            } catch (IllegalAccessException e) {
                LOG.error("Cannot access @{} method '{}': {}", phase, m.getName(), e.getMessage());
            }
        }
    }

    private PlannedTest plan(GameTestDefinition def, WorldServer world) {
        HybridStructureTemplate template = loadTemplate(def);

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

        HorizonQAMod.CHUNK_LOADER.forceChunks(world, cellMinX, cellMinY, cellMinZ, cellMaxX, cellMaxY, cellMaxZ);

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
            cellMaxZ);
    }

    private static HybridStructureTemplate loadTemplate(GameTestDefinition def) {
        if (def.getTemplateName()
            .isEmpty()) return null;
        try {
            return HybridStructureLoader.load(def.getTemplateName());
        } catch (IOException e) {
            LOG.error(
                "Failed to load template '{}' for test '{}' — test will run without a structure: {}",
                def.getTemplateName(),
                def.getTestId(),
                e.getMessage());
            return null;
        }
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
            List<Method> before = beforeMethods.getOrDefault(name, new ArrayList<>());
            List<Method> after = afterMethods.getOrDefault(name, new ArrayList<>());
            result.add(new Batch(name, entry.getValue(), before, after));
        }
        return result;
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

    private static final class PlannedTest {

        final GameTestDefinition def;
        final HybridStructureTemplate template;
        final int originX, originY, originZ;
        final int tmplSizeX, tmplSizeY, tmplSizeZ;
        final int cellMinX, cellMinY, cellMinZ;
        final int cellMaxX, cellMaxY, cellMaxZ;

        PlannedTest(GameTestDefinition def, HybridStructureTemplate template, int originX, int originY, int originZ,
            int tmplSizeX, int tmplSizeY, int tmplSizeZ, int cellMinX, int cellMinY, int cellMinZ, int cellMaxX,
            int cellMaxY, int cellMaxZ) {
            this.def = def;
            this.template = template;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.tmplSizeX = tmplSizeX;
            this.tmplSizeY = tmplSizeY;
            this.tmplSizeZ = tmplSizeZ;
            this.cellMinX = cellMinX;
            this.cellMinY = cellMinY;
            this.cellMinZ = cellMinZ;
            this.cellMaxX = cellMaxX;
            this.cellMaxY = cellMaxY;
            this.cellMaxZ = cellMaxZ;
        }
    }
}
