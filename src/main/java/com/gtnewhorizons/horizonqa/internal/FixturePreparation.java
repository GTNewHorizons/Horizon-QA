package com.gtnewhorizons.horizonqa.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.event.StructurePlaced;
import com.gtnewhorizons.horizonqa.api.gt.GTNHGameTestHelper;
import com.gtnewhorizons.horizonqa.report.CaseResult;
import com.gtnewhorizons.horizonqa.structure.HybridStructureLoader;
import com.gtnewhorizons.horizonqa.structure.HybridStructureTemplate;
import com.gtnewhorizons.horizonqa.structure.StructurePlacer;
import com.gtnewhorizons.horizonqa.structure.TemplateException;

final class FixturePreparation {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private final GameTestGridLayout grid = new GameTestGridLayout();

    List<Result> prepare(WorldServer world, List<GameTestDefinition> definitions) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(definitions, "definitions");
        if (definitions.isEmpty()) return Collections.emptyList();

        List<Plan> plans = new ArrayList<>(definitions.size());
        for (GameTestDefinition definition : definitions) {
            plans.add(planAllocated(definition));
        }
        return preparePlans(world, plans);
    }

    Result prepareAt(WorldServer world, GameTestDefinition definition, TestPos origin) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(origin, "origin");
        return preparePlans(world, Collections.singletonList(planAt(definition, origin))).get(0);
    }

    private static HybridStructureTemplate loadTemplate(GameTestDefinition definition) throws IOException {
        if (definition.getTemplateName()
            .isEmpty()) return null;
        return HybridStructureLoader.load(definition.getTemplateName());
    }

    private Plan planAllocated(GameTestDefinition definition) {
        requireRunnable(definition);
        HybridStructureTemplate template;
        try {
            template = loadTemplate(definition);
        } catch (IOException e) {
            int[] origin = grid.allocateOrigin();
            return failedPlan(definition, TestPos.at(origin[0], origin[1], origin[2]), e);
        }

        int sizeX = placedSizeX(definition, template);
        int sizeZ = placedSizeZ(definition, template);
        int[] origin = grid.allocateOrigin(sizeX, sizeZ);
        return planLoaded(definition, TestPos.at(origin[0], origin[1], origin[2]), template, sizeX, sizeZ);
    }

    private static Plan planAt(GameTestDefinition definition, TestPos origin) {
        requireRunnable(definition);
        HybridStructureTemplate template;
        try {
            template = loadTemplate(definition);
        } catch (IOException e) {
            return failedPlan(definition, origin, e);
        }
        return planLoaded(
            definition,
            origin,
            template,
            placedSizeX(definition, template),
            placedSizeZ(definition, template));
    }

    private static Plan planLoaded(GameTestDefinition definition, TestPos origin, HybridStructureTemplate template,
        int sizeX, int sizeZ) {
        int sizeY = template != null ? template.getSizeY() : 0;
        TestCell cell = cell(definition, origin, sizeX, sizeY, sizeZ);
        if (template != null) {
            try {
                StructurePlacer.validateVerticalBounds(definition.getTemplateName(), origin.y(), sizeY);
            } catch (TemplateException e) {
                return Plan.failed(definition, cell, e);
            }
        }
        return Plan.ready(definition, template, cell, sizeX, sizeY, sizeZ);
    }

    private static Plan failedPlan(GameTestDefinition definition, TestPos origin, Throwable error) {
        return Plan.failed(definition, cell(definition, origin, 0, 0, 0), error);
    }

    private static TestCell cell(GameTestDefinition definition, TestPos origin, int sizeX, int sizeY, int sizeZ) {
        int cellSizeX = Math.max(sizeX, GameTestGridLayout.DEFAULT_CELL_SIZE);
        int cellSizeY = Math.max(sizeY, GameTestGridLayout.DEFAULT_CELL_SIZE);
        int cellSizeZ = Math.max(sizeZ, GameTestGridLayout.DEFAULT_CELL_SIZE);
        return new TestCell(
            definition.getTestId(),
            origin.x(),
            origin.y(),
            origin.z(),
            origin.x(),
            origin.y(),
            origin.z(),
            origin.x() + cellSizeX - 1,
            origin.y() + cellSizeY - 1,
            origin.z() + cellSizeZ - 1);
    }

    private static int placedSizeX(GameTestDefinition definition, HybridStructureTemplate template) {
        return template != null ? StructurePlacer.placedSizeX(template, definition.getRotation()) : 0;
    }

    private static int placedSizeZ(GameTestDefinition definition, HybridStructureTemplate template) {
        return template != null ? StructurePlacer.placedSizeZ(template, definition.getRotation()) : 0;
    }

    private static void requireRunnable(GameTestDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definition.isSkippedAtDiscovery()) {
            throw new IllegalArgumentException("Discovery-skipped tests cannot be prepared: " + definition.getTestId());
        }
    }

    private static List<Result> preparePlans(WorldServer world, List<Plan> plans) {
        forceArea(world, plans);
        for (Plan plan : plans) {
            if (plan.failure == null) {
                clear(world, plan.cell);
            }
        }

        List<Result> results = new ArrayList<>(plans.size());
        for (Plan plan : plans) {
            if (plan.failure != null) {
                results.add(Result.failed(plan.definition, plan.cell, templateFailure(plan.definition, plan.failure)));
                continue;
            }
            try {
                results.add(prepare(world, plan));
            } catch (RuntimeException | Error e) {
                clearAfterUnexpectedFailure(world, plan.cell, e);
                throw e;
            }
        }
        return Collections.unmodifiableList(results);
    }

    private static Result prepare(WorldServer world, Plan plan) {
        if (plan.template != null) {
            try {
                StructurePlacer.placeStrict(
                    plan.definition.getTemplateName(),
                    plan.template,
                    world,
                    plan.cell.originX(),
                    plan.cell.originY(),
                    plan.cell.originZ(),
                    plan.definition.getRotation(),
                    GTNHGameTestHelper::rotateStructureTileNbt);
            } catch (TemplateException e) {
                clear(world, plan.cell);
                return Result.failed(plan.definition, plan.cell, templateFailure(plan.definition, e));
            }
        }

        GameTestInstance instance = new GameTestInstance(
            plan.definition,
            plan.cell.originX(),
            plan.cell.originY(),
            plan.cell.originZ(),
            plan.template);
        if (plan.template != null) {
            TestEventRecorder recorder = instance.getRecorder();
            recorder.record(
                () -> new StructurePlaced(
                    recorder.clock()
                        .tick(),
                    plan.definition.getTemplateName(),
                    TestPos.at(plan.cell.originX(), plan.cell.originY(), plan.cell.originZ()),
                    plan.sizeX,
                    plan.sizeY,
                    plan.sizeZ));
        }

        int templateMaxX = plan.sizeX > 0 ? plan.cell.originX() + plan.sizeX - 1 : -1;
        int templateMaxY = plan.sizeY > 0 ? plan.cell.originY() + plan.sizeY - 1 : -1;
        int templateMaxZ = plan.sizeZ > 0 ? plan.cell.originZ() + plan.sizeZ - 1 : -1;
        TestCellScanner.registerIsolationCheck(
            instance,
            world,
            plan.cell.minX(),
            plan.cell.minY(),
            plan.cell.minZ(),
            plan.cell.maxX(),
            plan.cell.maxY(),
            plan.cell.maxZ(),
            plan.cell.originX(),
            plan.cell.originY(),
            plan.cell.originZ(),
            templateMaxX,
            templateMaxY,
            templateMaxZ,
            plan.template != null);
        return Result.ready(plan.definition, plan.cell, instance);
    }

    private static void forceArea(WorldServer world, List<Plan> plans) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int ready = 0;
        for (Plan plan : plans) {
            if (plan.failure != null) continue;
            ready++;
            minX = Math.min(minX, plan.cell.minX() - GameTestGridLayout.INTER_CELL_GAP);
            minY = Math.min(minY, Math.max(0, plan.cell.minY() - GameTestGridLayout.INTER_CELL_GAP));
            minZ = Math.min(minZ, plan.cell.minZ() - GameTestGridLayout.INTER_CELL_GAP);
            maxX = Math.max(maxX, plan.cell.maxX() + GameTestGridLayout.INTER_CELL_GAP);
            maxY = Math.max(maxY, plan.cell.maxY() + GameTestGridLayout.INTER_CELL_GAP);
            maxZ = Math.max(maxZ, plan.cell.maxZ() + GameTestGridLayout.INTER_CELL_GAP);
        }
        if (ready == 0) return;

        try {
            HorizonQAMod.CHUNK_LOADER.forceChunksStrict(world, minX, minY, minZ, maxX, maxY, maxZ);
        } catch (TemplateException e) {
            throw templateFailure(null, e);
        }
        LOG.info(
            "[GameTest] Loaded fixture preparation area ({}, {}, {}) -> ({}, {}, {}) for {} fixture(s).",
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            ready);
    }

    private static void clear(WorldServer world, TestCell cell) {
        TestCellScanner
            .preClearWithMargin(world, cell.minX(), cell.minY(), cell.minZ(), cell.maxX(), cell.maxY(), cell.maxZ());
    }

    private static void clearAfterUnexpectedFailure(WorldServer world, TestCell cell, Throwable failure) {
        try {
            clear(world, cell);
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static GameTestInfrastructureException templateFailure(GameTestDefinition definition, Throwable cause) {
        String message = cause != null ? cause.getMessage() : null;
        if (message == null || message.isEmpty()) {
            message = "Template setup failed";
        }
        if (definition != null) {
            LOG.error("Template setup failed for test '{}': {}", definition.getTestId(), message, cause);
        } else {
            LOG.error("Fixture preparation failed: {}", message, cause);
        }
        GameTestInfrastructureException failure = new GameTestInfrastructureException(
            CaseResult.TEMPLATE_ERROR,
            message);
        if (cause != null) failure.initCause(cause);
        return failure;
    }

    @Desugar
    record Result(GameTestDefinition definition, TestCell cell, GameTestInstance instance,
        GameTestInfrastructureException failure) {

        Result {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(cell, "cell");
            if ((instance == null) == (failure == null)) {
                throw new IllegalArgumentException("Exactly one of instance and failure must be present");
            }
        }

        static Result ready(GameTestDefinition definition, TestCell cell, GameTestInstance instance) {
            return new Result(definition, cell, instance, null);
        }

        static Result failed(GameTestDefinition definition, TestCell cell, GameTestInfrastructureException failure) {
            return new Result(definition, cell, null, failure);
        }

        boolean isReady() {
            return instance != null;
        }
    }

    @Desugar
    private record Plan(GameTestDefinition definition, HybridStructureTemplate template, TestCell cell, int sizeX,
        int sizeY, int sizeZ, Throwable failure) {

        static Plan ready(GameTestDefinition definition, HybridStructureTemplate template, TestCell cell, int sizeX,
            int sizeY, int sizeZ) {
            return new Plan(definition, template, cell, sizeX, sizeY, sizeZ, null);
        }

        static Plan failed(GameTestDefinition definition, TestCell cell, Throwable failure) {
            return new Plan(definition, null, cell, 0, 0, 0, failure);
        }
    }
}
