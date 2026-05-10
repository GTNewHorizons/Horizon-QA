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

    public void launchTest(GameTestDefinition def) {
        launchTests(Collections.singletonList(def));
    }

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
            LOG.info("[GameTest] Launched '{}' at ({}, {}, {}).", def.getTestId(), origin[0], origin[1], origin[2]);
        }
        LOG.info("[GameTest] Launched {} test(s) total.", defs.size());
    }

    public void relaunchAtCell(GameTestDefinition def) {
        WorldServer world = getOverworld();
        if (world == null) return;

        CellRecord existing = knownCells.get(def.getTestId());
        if (existing == null) {
            launchTest(def);
            return;
        }

        ensureRunnerRegistered();
        HybridStructureTemplate template = loadTemplate(def);
        GameTestInstance inst = spawnTestAt(def, world, existing.originX, existing.originY, existing.originZ, template);
        runner.addInstance(inst);
        LOG.info(
            "[GameTest] Re-launched '{}' in-place at ({}, {}, {}).",
            def.getTestId(),
            existing.originX,
            existing.originY,
            existing.originZ);
    }

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

    private GameTestInstance spawnTestAt(GameTestDefinition def, WorldServer world, int originX, int originY,
        int originZ, HybridStructureTemplate template) {

        int sizeX = template != null ? template.getSizeX() : 0;
        int sizeY = template != null ? template.getSizeY() : 0;
        int sizeZ = template != null ? template.getSizeZ() : 0;

        int cellSizeX = sizeX > 0 ? sizeX : GameTestGridLayout.DEFAULT_CELL_SIZE;
        int cellSizeY = sizeY > 0 ? sizeY : GameTestGridLayout.DEFAULT_CELL_SIZE;
        int cellSizeZ = sizeZ > 0 ? sizeZ : GameTestGridLayout.DEFAULT_CELL_SIZE;

        int cellMinX = originX;
        int cellMinY = originY;
        int cellMinZ = originZ;
        int cellMaxX = originX + cellSizeX - 1;
        int cellMaxY = originY + cellSizeY - 1;
        int cellMaxZ = originZ + cellSizeZ - 1;

        GameTestMod.CHUNK_LOADER.forceChunks(world, cellMinX, cellMinY, cellMinZ, cellMaxX, cellMaxY, cellMaxZ);

        TestCellScanner.preClear(world, cellMinX, cellMinY, cellMinZ, cellMaxX, cellMaxY, cellMaxZ);

        if (template != null) {
            StructurePlacer.place(template, world, originX, originY, originZ);
        }

        CellRecord cell = new CellRecord(
            def.getTestId(),
            originX,
            originY,
            originZ,
            cellMinX,
            cellMinY,
            cellMinZ,
            cellMaxX,
            cellMaxY,
            cellMaxZ);
        knownCells.put(def.getTestId(), cell);

        GameTestInstance inst = new GameTestInstance(def, originX, originY, originZ);

        int tmplMaxX = sizeX > 0 ? originX + sizeX - 1 : -1;
        int tmplMaxY = sizeY > 0 ? originY + sizeY - 1 : -1;
        int tmplMaxZ = sizeZ > 0 ? originZ + sizeZ - 1 : -1;
        TestCellScanner.registerIsolationCheck(
            inst,
            world,
            cellMinX,
            cellMinY,
            cellMinZ,
            cellMaxX,
            cellMaxY,
            cellMaxZ,
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
