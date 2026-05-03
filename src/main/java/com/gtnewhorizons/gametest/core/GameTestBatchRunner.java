package com.gtnewhorizons.gametest.core;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;

import com.gtnewhorizons.gametest.GameTestJvmFlags;
import com.gtnewhorizons.gametest.GameTestMod;
import com.gtnewhorizons.gametest.report.ConsoleReporter;
import com.gtnewhorizons.gametest.report.JUnitXmlReporter;
import com.gtnewhorizons.gametest.structure.HybridStructureLoader;
import com.gtnewhorizons.gametest.structure.HybridStructureTemplate;
import com.gtnewhorizons.gametest.structure.StructurePlacer;

/**
 * Orchestrates sequential batch execution: for each batch, calls {@code @BeforeBatch} methods,
 * loads and places any structure templates, runs all tests in the batch via {@link GameTestRunner},
 * then calls {@code @AfterBatch} methods.
 *
 * <p>
 * The {@link GameTestRunner} tick loop is registered once in {@link #start()} and unregistered
 * when the final batch completes. Chunk tickets for all active cells are released at that point via
 * {@link GameTestChunkLoader#releaseAll()}.
 */
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

    /** Register the tick loop and begin executing the first batch. */
    public void start() {
        runner.register();
        if (batches.isEmpty()) {
            onAllBatchesDone();
            return;
        }
        runBatch(0);
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

        List<GameTestInstance> batchInstances = new ArrayList<>();
        for (GameTestDefinition def : batch.tests) {
            GameTestInstance inst = allocateAndSpawn(def, world);
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
        GameTestMod.CHUNK_LOADER.releaseAll();

        ConsoleReporter.report(allInstances);

        File reportFile = new File(".", "TEST-gametest.xml");
        try {
            JUnitXmlReporter.write(allInstances, reportFile);
            LOG.info("JUnit XML report written to {}", reportFile.getAbsolutePath());
        } catch (IOException e) {
            LOG.error("Failed to write JUnit XML report: {}", e.getMessage());
        }

        if (GameTestJvmFlags.isEnabled()) {
            long requiredFailures = countRequiredFailures();
            // Cap at 127 so the value is a valid Unix exit code.
            int exitCode = (int) Math.min(requiredFailures, 127);
            LOG.info("CI mode: exiting with code {} ({} required test(s) failed).", exitCode, requiredFailures);
            FMLCommonHandler.instance().exitJava(exitCode, false);
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

    /**
     * Load template, allocate a grid cell, force-load chunks, place the structure, and start
     * the test. Returns the running instance.
     */
    private GameTestInstance allocateAndSpawn(GameTestDefinition def, WorldServer world) {
        HybridStructureTemplate template = loadTemplate(def);

        int sizeX = template != null ? template.getSizeX() : 0;
        int sizeY = template != null ? template.getSizeY() : 0;
        int sizeZ = template != null ? template.getSizeZ() : 0;
        int[] origin = grid.allocateOrigin(sizeX, sizeZ);

        int chunkSizeX = Math.max(sizeX, GameTestGridLayout.DEFAULT_CELL_SIZE);
        int chunkSizeZ = Math.max(sizeZ, GameTestGridLayout.DEFAULT_CELL_SIZE);
        GameTestMod.CHUNK_LOADER.forceChunks(
            world,
            origin[0], origin[1], origin[2],
            origin[0] + chunkSizeX - 1,
            origin[1] + Math.max(sizeY, 1) - 1,
            origin[2] + chunkSizeZ - 1);

        if (template != null) {
            StructurePlacer.place(template, world, origin[0], origin[1], origin[2]);
        }

        GameTestInstance inst = new GameTestInstance(def, origin[0], origin[1], origin[2]);
        inst.start(world);
        return inst;
    }

    private static HybridStructureTemplate loadTemplate(GameTestDefinition def) {
        if (def.getTemplateName().isEmpty()) return null;
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

    // -------------------------------------------------------------------------
    // Batch construction
    // -------------------------------------------------------------------------

    private static List<Batch> buildBatches(List<GameTestDefinition> tests, Map<String, List<Method>> beforeMethods,
        Map<String, List<Method>> afterMethods) {

        // Group tests by batch name while preserving insertion order
        Map<String, List<GameTestDefinition>> groups = new LinkedHashMap<>();
        for (GameTestDefinition def : tests) {
            groups.computeIfAbsent(def.getBatch(), k -> new ArrayList<>())
                .add(def);
        }

        List<Batch> result = new ArrayList<>();
        for (Map.Entry<String, List<GameTestDefinition>> entry : groups.entrySet()) {
            String name = entry.getKey();
            List<Method> before = beforeMethods.getOrDefault(name, new ArrayList<>());
            List<Method> after = afterMethods.getOrDefault(name, new ArrayList<>());
            result.add(new Batch(name, entry.getValue(), before, after));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Inner type
    // -------------------------------------------------------------------------

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
}
