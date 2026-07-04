package com.gtnewhorizons.horizonqa.internal;

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

import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.api.gt.GTNHGameTestHelper;
import com.gtnewhorizons.horizonqa.command.HorizonQACommandUtils.CellRecord;
import com.gtnewhorizons.horizonqa.structure.HybridStructureLoader;
import com.gtnewhorizons.horizonqa.structure.HybridStructureTemplate;
import com.gtnewhorizons.horizonqa.structure.StructurePlacer;
import com.gtnewhorizons.horizonqa.structure.TemplateException;

public class InteractiveTestSession {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private static InteractiveTestSession CURRENT;

    public static Runnable onClearAllCallback;

    private final GameTestRunner runner;
    private final GameTestGridLayout grid;
    private boolean runnerRegistered;

    private final Map<String, CellRecord> knownCells = new ConcurrentHashMap<>();
    private final Map<String, GameTestInstance> lastInstances = new ConcurrentHashMap<>();
    private final Set<String> failedIds = ConcurrentHashMap.newKeySet();

    private InteractiveTestSession() {
        runner = new GameTestRunner();
        grid = new GameTestGridLayout();
        runnerRegistered = false;
    }

    public static InteractiveTestSession get() {
        if (CURRENT == null) {
            CURRENT = new InteractiveTestSession();
        }
        return CURRENT;
    }

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

    public int launchTest(GameTestDefinition def) {
        return launchTests(Collections.singletonList(def));
    }

    public int launchTests(List<GameTestDefinition> defs) {
        if (defs.isEmpty()) return 0;
        if (isBatchRunnerActive()) return 0;
        WorldServer world = getOverworld();
        if (world == null) return 0;

        List<PlannedTest> planned = planTests(defs);
        if (planned.isEmpty()) {
            return 0;
        }
        if (!forcePlannedArea(world, planned)) {
            return 0;
        }

        ensureRunnerRegistered();
        for (PlannedTest plannedTest : planned) {
            GameTestInstance inst = spawnPlannedTest(plannedTest, world);
            runner.addInstance(inst);
            LOG.info(
                "[GameTest] Launched '{}' at ({}, {}, {}).",
                plannedTest.def.getTestId(),
                plannedTest.originX,
                plannedTest.originY,
                plannedTest.originZ);
        }
        LOG.info("[GameTest] Launched {} test(s) total.", planned.size());
        return planned.size();
    }

    public boolean relaunchAtCell(GameTestDefinition def) {
        if (isBatchRunnerActive()) return false;
        WorldServer world = getOverworld();
        if (world == null) return false;

        CellRecord existing = knownCells.get(def.getTestId());
        if (existing == null) {
            return launchTest(def) > 0;
        }

        PlannedTest plannedTest = planTestAt(def, existing.originX, existing.originY, existing.originZ);
        if (plannedTest == null) {
            return false;
        }
        if (!forcePlannedArea(world, Collections.singletonList(plannedTest))) {
            return false;
        }
        ensureRunnerRegistered();
        GameTestInstance inst = spawnPlannedTest(plannedTest, world);
        runner.addInstance(inst);
        LOG.info(
            "[GameTest] Re-launched '{}' in-place at ({}, {}, {}).",
            def.getTestId(),
            existing.originX,
            existing.originY,
            existing.originZ);
        return true;
    }

    public void clearAll() {
        if (isBatchRunnerActive()) return;
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
        failedIds.clear();
        HorizonQAMod.CHUNK_LOADER.releaseAll();
        grid.reset();
        if (onClearAllCallback != null) onClearAllCallback.run();
        LOG.info("[GameTest] Cleared {} test cell(s).", cleared);
    }

    private static boolean isBatchRunnerActive() {
        if (!GameTestBatchRunner.isBatchRunning()) {
            return false;
        }
        LOG.warn("[GameTest] Interactive test session is unavailable while a GameTest batch is running.");
        return true;
    }

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

    public Set<String> getFailedIds() {
        refreshFailedIds();
        return Collections.unmodifiableSet(failedIds);
    }

    public Collection<CellRecord> getKnownCells() {
        return new ArrayList<>(knownCells.values());
    }

    public GameTestInstance getLastInstance(String testId) {
        return lastInstances.get(testId);
    }

    private List<PlannedTest> planTests(List<GameTestDefinition> defs) {
        List<PlannedTest> planned = new ArrayList<>(defs.size());
        for (GameTestDefinition def : defs) {
            HybridStructureTemplate template = loadTemplate(def);
            int sizeX = template != null ? StructurePlacer.placedSizeX(template, def.getRotation()) : 0;
            int sizeZ = template != null ? StructurePlacer.placedSizeZ(template, def.getRotation()) : 0;
            int[] origin = grid.allocateOrigin(sizeX, sizeZ);
            PlannedTest plannedTest = planTestAt(def, origin[0], origin[1], origin[2], template);
            if (plannedTest != null) {
                planned.add(plannedTest);
            }
        }
        return planned;
    }

    private PlannedTest planTestAt(GameTestDefinition def, int originX, int originY, int originZ) {
        return planTestAt(def, originX, originY, originZ, loadTemplate(def));
    }

    private PlannedTest planTestAt(GameTestDefinition def, int originX, int originY, int originZ,
        HybridStructureTemplate template) {
        int sizeX = template != null ? StructurePlacer.placedSizeX(template, def.getRotation()) : 0;
        int sizeY = template != null ? template.getSizeY() : 0;
        int sizeZ = template != null ? StructurePlacer.placedSizeZ(template, def.getRotation()) : 0;

        int cellSizeX = sizeX > 0 ? sizeX : GameTestGridLayout.DEFAULT_CELL_SIZE;
        int cellSizeY = sizeY > 0 ? sizeY : GameTestGridLayout.DEFAULT_CELL_SIZE;
        int cellSizeZ = sizeZ > 0 ? sizeZ : GameTestGridLayout.DEFAULT_CELL_SIZE;

        int cellMinX = originX;
        int cellMinY = originY;
        int cellMinZ = originZ;
        int cellMaxX = originX + cellSizeX - 1;
        int cellMaxY = originY + cellSizeY - 1;
        int cellMaxZ = originZ + cellSizeZ - 1;

        if (template != null) {
            try {
                StructurePlacer.validateVerticalBounds(def.getTemplateName(), originY, sizeY);
            } catch (TemplateException e) {
                LOG.error(
                    "[GameTest] Cannot place interactive test '{}' at ({}, {}, {}): {}",
                    def.getTestId(),
                    originX,
                    originY,
                    originZ,
                    e.getMessage());
                return null;
            }
        }

        return new PlannedTest(
            def,
            template,
            originX,
            originY,
            originZ,
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

    private static boolean forcePlannedArea(WorldServer world, List<PlannedTest> planned) {
        if (planned.isEmpty()) return true;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (PlannedTest plannedTest : planned) {
            minX = Math.min(minX, plannedTest.cellMinX - GameTestGridLayout.INTER_CELL_GAP);
            minY = Math.min(minY, Math.max(0, plannedTest.cellMinY - GameTestGridLayout.INTER_CELL_GAP));
            minZ = Math.min(minZ, plannedTest.cellMinZ - GameTestGridLayout.INTER_CELL_GAP);
            maxX = Math.max(maxX, plannedTest.cellMaxX + GameTestGridLayout.INTER_CELL_GAP);
            maxY = Math.max(maxY, plannedTest.cellMaxY + GameTestGridLayout.INTER_CELL_GAP);
            maxZ = Math.max(maxZ, plannedTest.cellMaxZ + GameTestGridLayout.INTER_CELL_GAP);
        }

        try {
            HorizonQAMod.CHUNK_LOADER.forceChunksStrict(world, minX, minY, minZ, maxX, maxY, maxZ);
            LOG.info(
                "[GameTest] Loaded test area ({}, {}, {}) -> ({}, {}, {}) for {} test(s).",
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                planned.size());
            return true;
        } catch (TemplateException e) {
            LOG.error("[GameTest] Could not load the full interactive test area: {}", e.getMessage(), e);
            return false;
        }
    }

    private GameTestInstance spawnPlannedTest(PlannedTest plannedTest, WorldServer world) {
        GameTestDefinition def = plannedTest.def;
        HybridStructureTemplate template = plannedTest.template;
        int originX = plannedTest.originX;
        int originY = plannedTest.originY;
        int originZ = plannedTest.originZ;

        CellRecord previous = knownCells.remove(def.getTestId());
        if (previous != null) {
            clearCell(world, previous);
        }
        failedIds.remove(def.getTestId());

        TestCellScanner.preClearWithMargin(
            world,
            plannedTest.cellMinX,
            plannedTest.cellMinY,
            plannedTest.cellMinZ,
            plannedTest.cellMaxX,
            plannedTest.cellMaxY,
            plannedTest.cellMaxZ);

        if (template != null) {
            StructurePlacer.place(
                template,
                world,
                originX,
                originY,
                originZ,
                def.getRotation(),
                GTNHGameTestHelper::rotateStructureTileNbt);
        }

        CellRecord cell = new CellRecord(
            def.getTestId(),
            originX,
            originY,
            originZ,
            plannedTest.cellMinX,
            plannedTest.cellMinY,
            plannedTest.cellMinZ,
            plannedTest.cellMaxX,
            plannedTest.cellMaxY,
            plannedTest.cellMaxZ);
        knownCells.put(def.getTestId(), cell);

        GameTestInstance inst = new GameTestInstance(def, originX, originY, originZ, template);

        int tmplMaxX = plannedTest.sizeX > 0 ? originX + plannedTest.sizeX - 1 : -1;
        int tmplMaxY = plannedTest.sizeY > 0 ? originY + plannedTest.sizeY - 1 : -1;
        int tmplMaxZ = plannedTest.sizeZ > 0 ? originZ + plannedTest.sizeZ - 1 : -1;
        TestCellScanner.registerIsolationCheck(
            inst,
            world,
            plannedTest.cellMinX,
            plannedTest.cellMinY,
            plannedTest.cellMinZ,
            plannedTest.cellMaxX,
            plannedTest.cellMaxY,
            plannedTest.cellMaxZ,
            originX,
            originY,
            originZ,
            tmplMaxX,
            tmplMaxY,
            tmplMaxZ,
            template != null);

        inst.start(world);
        lastInstances.put(def.getTestId(), inst);
        return inst;
    }

    private static final class PlannedTest {

        final GameTestDefinition def;
        final HybridStructureTemplate template;
        final int originX;
        final int originY;
        final int originZ;
        final int sizeX;
        final int sizeY;
        final int sizeZ;
        final int cellMinX;
        final int cellMinY;
        final int cellMinZ;
        final int cellMaxX;
        final int cellMaxY;
        final int cellMaxZ;

        PlannedTest(GameTestDefinition def, HybridStructureTemplate template, int originX, int originY, int originZ,
            int sizeX, int sizeY, int sizeZ, int cellMinX, int cellMinY, int cellMinZ, int cellMaxX, int cellMaxY,
            int cellMaxZ) {
            this.def = def;
            this.template = template;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.cellMinX = cellMinX;
            this.cellMinY = cellMinY;
            this.cellMinZ = cellMinZ;
            this.cellMaxX = cellMaxX;
            this.cellMaxY = cellMaxY;
            this.cellMaxZ = cellMaxZ;
        }
    }

    private static void clearCell(WorldServer world, CellRecord cell) {
        GridSweeper.clearAndNotify(world, cell.minX, cell.minY, cell.minZ, cell.maxX, cell.maxY, cell.maxZ);
    }

    private static HybridStructureTemplate loadTemplate(GameTestDefinition def) {
        if (def.getTemplateName()
            .isEmpty()) return null;
        try {
            return HybridStructureLoader.load(def.getTemplateName());
        } catch (IOException e) {
            LOG.error(
                "[GameTest] Failed to load template '{}' for test '{}': {}",
                def.getTemplateName(),
                def.getTestId(),
                e.getMessage());
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
