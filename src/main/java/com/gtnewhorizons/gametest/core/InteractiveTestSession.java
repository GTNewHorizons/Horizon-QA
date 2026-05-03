package com.gtnewhorizons.gametest.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.gametest.GameTestMod;
import com.gtnewhorizons.gametest.command.GameTestCommandUtils.CellRecord;
import com.gtnewhorizons.gametest.structure.HybridStructureLoader;
import com.gtnewhorizons.gametest.structure.HybridStructureTemplate;
import com.gtnewhorizons.gametest.structure.StructurePlacer;

/**
 * Singleton session state for interactive {@code /gametest} usage.
 *
 * <p>Unlike {@link GameTestBatchRunner} (which runs headless CI suites and exits the server),
 * this class:
 * <ul>
 *   <li>Keeps test cells visible in the world after completion so the developer can inspect
 *       them.</li>
 *   <li>Tracks which tests have failed across multiple re-runs, enabling
 *       {@code /gametest runfailed}.</li>
 *   <li>Supports launching individual tests or full suites on demand from commands.</li>
 *   <li>Never exits the server.</li>
 * </ul>
 *
 * <p>The session is reset at the beginning of each {@code serverStarting} event via
 * {@link #reset()}, so a dev-environment server restart always starts fresh.
 */
public class InteractiveTestSession {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private static InteractiveTestSession CURRENT;

    /**
     * Optional callback invoked by {@link #clearAll()} to notify client-side systems
     * (e.g. VisualManager) that all visual state should be wiped.
     * Set from {@code ClientProxy} so the server core never directly imports client classes.
     */
    public static Runnable onClearAllCallback;

    private final GameTestRunner runner;
    private final GameTestGridLayout grid;
    private boolean runnerRegistered;

    /** Cell footprint for every test that has been placed this session. */
    private final Map<String, CellRecord> knownCells = new ConcurrentHashMap<>();
    /** Most-recently-created {@link GameTestInstance} per test ID. */
    private final Map<String, GameTestInstance> lastInstances = new ConcurrentHashMap<>();
    /**
     * Test IDs that failed or timed-out in at least one run this session and have not
     * subsequently passed. Refreshed lazily by {@link #refreshFailedIds()}.
     */
    private final Set<String> failedIds = ConcurrentHashMap.newKeySet();

    private InteractiveTestSession() {
        runner = new GameTestRunner();
        grid = new GameTestGridLayout();
        runnerRegistered = false;
    }

    /** Return (creating if necessary) the current interactive session. */
    public static InteractiveTestSession get() {
        if (CURRENT == null) {
            CURRENT = new InteractiveTestSession();
        }
        return CURRENT;
    }

    /**
     * Tear down the current session: unregister the tick loop and discard all state.
     * Called at the start of each {@code serverStarting} event.
     */
    public static void reset() {
        if (CURRENT != null) {
            if (CURRENT.runnerRegistered) {
                try {
                    CURRENT.runner.unregister();
                } catch (Exception ignored) {}
            }
            CURRENT = null;
        }
    }

    // -------------------------------------------------------------------------
    // Test launching
    // -------------------------------------------------------------------------

    /**
     * Launch {@code def} in a fresh grid cell.
     * Any prior cell for this test ID is left in the world; use
     * {@link #relaunchAtCell(GameTestDefinition)} to re-run in-place.
     */
    public void launchTest(GameTestDefinition def) {
        WorldServer world = getOverworld();
        if (world == null) return;
        ensureRunnerRegistered();
        HybridStructureTemplate template = loadTemplate(def);
        int sizeX = template != null ? template.getSizeX() : 0;
        int sizeZ = template != null ? template.getSizeZ() : 0;
        int[] origin = grid.allocateOrigin(sizeX, sizeZ);
        GameTestInstance inst = spawnTestAt(def, world, origin[0], origin[1], origin[2], template);
        runner.addInstance(inst);
        LOG.info("[GameTest] Launched '{}' at ({}, {}, {}).",
                def.getTestId(), origin[0], origin[1], origin[2]);
    }

    /**
     * Launch every test in {@code defs}, each in its own freshly-allocated grid cell.
     */
    public void launchTests(List<GameTestDefinition> defs) {
        if (defs.isEmpty()) return;
        WorldServer world = getOverworld();
        if (world == null) return;
        ensureRunnerRegistered();
        for (GameTestDefinition def : defs) {
            HybridStructureTemplate template = loadTemplate(def);
            int sizeX = template != null ? template.getSizeX() : 0;
            int sizeZ = template != null ? template.getSizeZ() : 0;
            int[] origin = grid.allocateOrigin(sizeX, sizeZ);
            GameTestInstance inst = spawnTestAt(def, world, origin[0], origin[1], origin[2], template);
            runner.addInstance(inst);
        }
        LOG.info("[GameTest] Launched {} test(s).", defs.size());
    }

    /**
     * Re-run {@code def} in-place: clears its existing cell, re-places the template,
     * and starts a fresh instance at the same origin.
     * If no cell is known for this test, falls back to {@link #launchTest(GameTestDefinition)}.
     */
    public void relaunchAtCell(GameTestDefinition def) {
        WorldServer world = getOverworld();
        if (world == null) return;

        CellRecord existing = knownCells.get(def.getTestId());
        if (existing == null) {
            launchTest(def);
            return;
        }

        ensureRunnerRegistered();
        clearCell(world, existing);
        HybridStructureTemplate template = loadTemplate(def);
        GameTestInstance inst = spawnTestAt(def, world,
                existing.originX, existing.originY, existing.originZ, template);
        runner.addInstance(inst);
        LOG.info("[GameTest] Re-launched '{}' in-place at ({}, {}, {}).",
                def.getTestId(), existing.originX, existing.originY, existing.originZ);
    }

    // -------------------------------------------------------------------------
    // Cell clearing
    // -------------------------------------------------------------------------

    /**
     * Fill all tracked test cells with air, release chunk tickets, and reset the grid
     * layout so future tests start at the origin again.
     */
    public void clearAll() {
        WorldServer world = getOverworld();
        int cleared = 0;
        if (world != null) {
            for (CellRecord cell : knownCells.values()) {
                clearCell(world, cell);
                cleared++;
            }
        }
        knownCells.clear();
        lastInstances.clear();
        GameTestMod.CHUNK_LOADER.releaseAll();
        grid.reset();
        if (onClearAllCallback != null) onClearAllCallback.run();
        LOG.info("[GameTest] Cleared {} test cell(s).", cleared);
    }

    // -------------------------------------------------------------------------
    // Failure tracking
    // -------------------------------------------------------------------------

    /**
     * Walk the most-recently-created instances and synchronise {@link #failedIds}:
     * test IDs that passed are removed; test IDs that failed or timed out are added.
     * Only done instances are considered.
     */
    public void refreshFailedIds() {
        for (Map.Entry<String, GameTestInstance> entry : lastInstances.entrySet()) {
            GameTestInstance inst = entry.getValue();
            if (!inst.isDone()) continue;
            if (inst.getStatus() == GameTestStatus.PASSED) {
                failedIds.remove(entry.getKey());
            } else {
                failedIds.add(entry.getKey());
            }
        }
    }

    /** Returns an unmodifiable snapshot of the current failed-test ID set. */
    public Set<String> getFailedIds() {
        refreshFailedIds();
        return Collections.unmodifiableSet(failedIds);
    }

    // -------------------------------------------------------------------------
    // Spatial queries
    // -------------------------------------------------------------------------

    /**
     * All cell records placed this session.
     * Returns a snapshot copy so the render thread can iterate safely
     * while the server thread may be adding new entries.
     */
    public Collection<CellRecord> getKnownCells() {
        return new ArrayList<>(knownCells.values());
    }

    /** The most-recently-created instance for a given test ID, or {@code null}. */
    public GameTestInstance getLastInstance(String testId) {
        return lastInstances.get(testId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private GameTestInstance spawnTestAt(GameTestDefinition def, WorldServer world,
            int originX, int originY, int originZ,
            HybridStructureTemplate template) {

        int sizeX = template != null ? template.getSizeX() : 0;
        int sizeY = template != null ? template.getSizeY() : 0;
        int sizeZ = template != null ? template.getSizeZ() : 0;

        int cellSizeX = sizeX > 0 ? sizeX : GameTestGridLayout.DEFAULT_CELL_SIZE;
        int cellSizeY = Math.max(sizeY, GameTestGridLayout.DEFAULT_CELL_SIZE);
        int cellSizeZ = sizeZ > 0 ? sizeZ : GameTestGridLayout.DEFAULT_CELL_SIZE;

        GameTestMod.CHUNK_LOADER.forceChunks(world,
                originX, originY, originZ,
                originX + cellSizeX - 1,
                originY + cellSizeY - 1,
                originZ + cellSizeZ - 1);

        if (template != null) {
            StructurePlacer.place(template, world, originX, originY, originZ);
        }

        // +16 Y headroom for visual markers that will be added in Phase 8.
        CellRecord cell = new CellRecord(def.getTestId(),
                originX, originY, originZ,
                originX, originY, originZ,
                originX + cellSizeX - 1,
                originY + cellSizeY - 1,
                originZ + cellSizeZ - 1);
        knownCells.put(def.getTestId(), cell);

        GameTestInstance inst = new GameTestInstance(def, originX, originY, originZ);
        inst.start(world);
        lastInstances.put(def.getTestId(), inst);
        return inst;
    }

    private static void clearCell(WorldServer world, CellRecord cell) {
        for (int x = cell.minX; x <= cell.maxX; x++) {
            for (int y = cell.minY; y <= cell.maxY; y++) {
                for (int z = cell.minZ; z <= cell.maxZ; z++) {
                    if (!world.isAirBlock(x, y, z)) {
                        world.setBlockToAir(x, y, z);
                    }
                }
            }
        }
    }

    private static HybridStructureTemplate loadTemplate(GameTestDefinition def) {
        if (def.getTemplateName().isEmpty()) return null;
        try {
            return HybridStructureLoader.load(def.getTemplateName());
        } catch (IOException e) {
            LOG.error("[GameTest] Failed to load template '{}' for test '{}': {}",
                    def.getTemplateName(), def.getTestId(), e.getMessage());
            return null;
        }
    }

    private void ensureRunnerRegistered() {
        if (!runnerRegistered) {
            runner.register();
            runnerRegistered = true;
        }
    }

    private static WorldServer getOverworld() {
        MinecraftServer srv = MinecraftServer.getServer();
        if (srv == null) {
            LOG.error("[GameTest] MinecraftServer is null — cannot run tests.");
            return null;
        }
        WorldServer world = srv.worldServerForDimension(0);
        if (world == null) {
            LOG.error("[GameTest] Overworld (dim 0) is null — cannot run tests.");
        }
        return world;
    }
}
