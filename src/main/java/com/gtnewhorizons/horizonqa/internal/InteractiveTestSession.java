package com.gtnewhorizons.horizonqa.internal;

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
import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;
import com.gtnewhorizons.horizonqa.api.TestPos;

public class InteractiveTestSession {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private static InteractiveTestSession CURRENT;

    public static Runnable onClearAllCallback;

    private final GameTestRunner runner;
    private FixturePreparation fixturePreparation;

    private final Map<String, TestCell> knownCells = new ConcurrentHashMap<>();
    private final Map<String, GameTestInstance> lastInstances = new ConcurrentHashMap<>();
    private final Set<String> failedIds = ConcurrentHashMap.newKeySet();
    private final Set<String> worldOwnedCellIds = ConcurrentHashMap.newKeySet();

    private InteractiveTestSession() {
        runner = new GameTestRunner();
        fixturePreparation = new FixturePreparation();
    }

    public static InteractiveTestSession get() {
        if (CURRENT == null) {
            CURRENT = new InteractiveTestSession();
        }
        return CURRENT;
    }

    public static void reset() {
        if (CURRENT != null) {
            CURRENT.runner.abortIfActive("Interactive test session was reset before test completion");
        }
        CURRENT = null;
    }

    public int launchTest(GameTestDefinition definition) {
        return launchTests(Collections.singletonList(definition));
    }

    public int launchTests(List<GameTestDefinition> definitions) {
        if (definitions.isEmpty() || isBatchRunnerActive()) return 0;
        WorldServer world = getOverworld();
        if (world == null) return 0;

        List<GameTestDefinition> runnable = runnableDefinitions(definitions);
        if (runnable.isEmpty()) return 0;
        for (GameTestDefinition definition : runnable) {
            clearRetainedFixture(world, definition.getTestId());
        }

        List<FixturePreparation.Result> results;
        try {
            results = fixturePreparation.prepare(world, runnable);
        } catch (GameTestInfrastructureException e) {
            LOG.error("[GameTest] Could not prepare the interactive test area: {}", e.getMessage(), e);
            return 0;
        }

        int launched = launchPrepared(world, results);
        LOG.info("[GameTest] Launched {} test(s) total.", launched);
        return launched;
    }

    public boolean relaunchAtCell(GameTestDefinition definition) {
        if (isBatchRunnerActive()) return false;
        WorldServer world = getOverworld();
        if (world == null) return false;

        TestCell existing = knownCells.get(definition.getTestId());
        if (existing == null) {
            return launchTest(definition) > 0;
        }

        TestPos origin = TestPos.at(existing.originX(), existing.originY(), existing.originZ());
        clearRetainedFixture(world, definition.getTestId());

        FixturePreparation.Result result;
        try {
            result = fixturePreparation.prepareAt(world, definition, origin);
        } catch (GameTestInfrastructureException e) {
            LOG.error(
                "[GameTest] Could not prepare interactive test '{}': {}",
                definition.getTestId(),
                e.getMessage(),
                e);
            return false;
        }
        if (!result.isReady()) {
            recordPreparationFailure(result);
            return false;
        }

        if (launchPrepared(world, Collections.singletonList(result)) == 0) return false;
        LOG.info(
            "[GameTest] Re-launched '{}' in-place at ({}, {}, {}).",
            definition.getTestId(),
            existing.originX(),
            existing.originY(),
            existing.originZ());
        return true;
    }

    public void clearAll() {
        if (isBatchRunnerActive()) return;
        WorldServer world = getOverworld();
        int cleared = 0;
        if (world != null) {
            for (TestCell cell : knownCells.values()) {
                if (worldOwnedCellIds.contains(cell.testId())) {
                    clearCell(world, cell);
                    cleared++;
                }
            }
        }
        knownCells.clear();
        lastInstances.clear();
        failedIds.clear();
        worldOwnedCellIds.clear();
        HorizonQAMod.CHUNK_LOADER.releaseAll();
        fixturePreparation = new FixturePreparation();
        if (onClearAllCallback != null) onClearAllCallback.run();
        LOG.info("[GameTest] Cleared {} test cell(s).", cleared);
    }

    public void refreshFailedIds() {
        for (Map.Entry<String, GameTestInstance> entry : lastInstances.entrySet()) {
            GameTestInstance instance = entry.getValue();
            if (!instance.isDone()) continue;
            if (instance.getStatus() == GameTestStatus.PASSED || instance.getStatus() == GameTestStatus.SKIPPED) {
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

    public Collection<TestCell> getKnownCells() {
        return new ArrayList<>(knownCells.values());
    }

    public GameTestInstance getLastInstance(String testId) {
        return lastInstances.get(testId);
    }

    void recordPreparationFailure(FixturePreparation.Result result) {
        GameTestInstance marker = new GameTestInstance(
            result.definition(),
            result.cell()
                .originX(),
            result.cell()
                .originY(),
            result.cell()
                .originZ());
        marker.failSetup(result.failure());
        knownCells.put(
            result.definition()
                .getTestId(),
            result.cell());
        lastInstances.put(
            result.definition()
                .getTestId(),
            marker);
        failedIds.add(
            result.definition()
                .getTestId());
        worldOwnedCellIds.remove(
            result.definition()
                .getTestId());
    }

    private int launchPrepared(WorldServer world, List<FixturePreparation.Result> results) {
        List<FixturePreparation.Result> ready = new ArrayList<>(results.size());
        for (FixturePreparation.Result result : results) {
            if (!result.isReady()) {
                recordPreparationFailure(result);
                continue;
            }
            ready.add(result);
        }
        if (ready.isEmpty()) return 0;
        if (!runner.tryStart(GameTestRunner.Kind.INTERACTIVE, () -> startPrepared(world, ready))) {
            LOG.warn("[GameTest] Interactive test session is unavailable while another execution is active.");
            return 0;
        }
        return ready.size();
    }

    private void startPrepared(WorldServer world, List<FixturePreparation.Result> ready) {
        for (FixturePreparation.Result result : ready) {
            GameTestInstance instance = result.instance();
            TestCell cell = result.cell();
            knownCells.put(
                result.definition()
                    .getTestId(),
                cell);
            worldOwnedCellIds.add(
                result.definition()
                    .getTestId());
            failedIds.remove(
                result.definition()
                    .getTestId());
            instance.start(world);
            lastInstances.put(
                result.definition()
                    .getTestId(),
                instance);
            runner.addInstance(instance);
            LOG.info(
                "[GameTest] Launched '{}' at ({}, {}, {}).",
                result.definition()
                    .getTestId(),
                cell.originX(),
                cell.originY(),
                cell.originZ());
        }
    }

    private void clearRetainedFixture(WorldServer world, String testId) {
        TestCell previous = knownCells.remove(testId);
        if (previous != null && worldOwnedCellIds.remove(testId)) {
            clearCell(world, previous);
        }
        lastInstances.remove(testId);
        failedIds.remove(testId);
    }

    private static List<GameTestDefinition> runnableDefinitions(List<GameTestDefinition> definitions) {
        List<GameTestDefinition> runnable = new ArrayList<>(definitions.size());
        for (GameTestDefinition definition : definitions) {
            if (definition.isSkippedAtDiscovery()) {
                LOG.info("[GameTest] Skipped '{}': {}", definition.getTestId(), definition.getDiscoverySkipReason());
            } else {
                runnable.add(definition);
            }
        }
        return runnable;
    }

    private static void clearCell(WorldServer world, TestCell cell) {
        int margin = GameTestGridLayout.INTER_CELL_GAP;
        GridSweeper.clearAndNotify(
            world,
            cell.minX() - margin,
            cell.minY() - margin,
            cell.minZ() - margin,
            cell.maxX() + margin,
            cell.maxY() + margin,
            cell.maxZ() + margin);
    }

    private static boolean isBatchRunnerActive() {
        if (!GameTestRunner.isBatchActive()) {
            return false;
        }
        LOG.warn("[GameTest] Interactive test session is unavailable while a GameTest batch is running.");
        return true;
    }

    private static WorldServer getOverworld() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            LOG.error("[GameTest] MinecraftServer is null — cannot run tests.");
            return null;
        }
        WorldServer world = server.worldServerForDimension(0);
        if (world == null) {
            LOG.error("[GameTest] Overworld (dim 0) is null — cannot run tests.");
        }
        return world;
    }
}
