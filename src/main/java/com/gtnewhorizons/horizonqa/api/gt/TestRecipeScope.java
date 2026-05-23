package com.gtnewhorizons.horizonqa.api.gt;

import java.lang.reflect.Field;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.event.TestRecipeInjected;
import com.gtnewhorizons.horizonqa.api.event.TestRecipeRemoved;
import com.gtnewhorizons.horizonqa.internal.TestEventRecorder;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.util.GTRecipe;

/**
 * Internal handle to an active injection of a synthetic {@link GTRecipe} into a multiblock's recipemap.
 * Created by {@link GTNHGameTestHelper#withTestRecipe}, which registers {@link #cleanup()} to run at end
 * of test. The recipe (and its backend caches) is removed when cleanup runs.
 *
 * @apiNote Only multis where {@code getRecipeMap() != null} are supported. Multis that override
 *          {@code checkProcessing()} directly bypass the recipemap and will not see the injected recipe.
 */
final class TestRecipeScope {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private static final Field CACHE_MAP_FIELD;
    private static final Field PROCESSING_LOGIC_FIELD;
    private static final Field LAST_RECIPE_FIELD;

    static {
        Field cacheMap;
        Field processingLogic;
        Field lastRecipe;
        try {
            cacheMap = RecipeMapBackend.class.getDeclaredField("cacheMap");
            cacheMap.setAccessible(true);
            processingLogic = MTEMultiBlockBase.class.getDeclaredField("processingLogic");
            processingLogic.setAccessible(true);
            lastRecipe = ProcessingLogic.class.getDeclaredField("lastRecipe");
            lastRecipe.setAccessible(true);
        } catch (NoSuchFieldException e) {
            LOG.warn(
                "TestRecipeScope: GT field(s) not found — synthetic recipe cache/lastRecipe cleanup disabled: {}",
                e.getMessage());
            cacheMap = null;
            processingLogic = null;
            lastRecipe = null;
        }
        CACHE_MAP_FIELD = cacheMap;
        PROCESSING_LOGIC_FIELD = processingLogic;
        LAST_RECIPE_FIELD = lastRecipe;
    }

    private final RecipeMap<?> recipeMap;
    private final GTRecipe recipe;
    private final WorldServer world;
    private final TestPos controllerAbsPos;
    private final TestEventRecorder recorder;
    private boolean closed = false;

    TestRecipeScope(RecipeMap<?> recipeMap, GTRecipe recipe, WorldServer world, TestPos controllerAbsPos,
        TestEventRecorder recorder) {
        this.recipeMap = recipeMap;
        this.recipe = recipe;
        this.world = world;
        this.controllerAbsPos = controllerAbsPos;
        this.recorder = recorder;
        recipeMap.add(recipe);
        if (recorder != null) {
            String mapName = recipeMap.unlocalizedName;
            int eut = recipe.mEUt;
            int dur = recipe.mDuration;
            recorder.record(
                () -> new TestRecipeInjected(
                    recorder.clock()
                        .tick(),
                    controllerAbsPos,
                    mapName,
                    eut,
                    dur));
        }
    }

    void cleanup() {
        if (closed) return;
        closed = true;

        RecipeMapBackend backend = recipeMap.getBackend();
        backend.removeRecipe(recipe);
        if (recorder != null) {
            String mapName = recipeMap.unlocalizedName;
            recorder.record(
                () -> new TestRecipeRemoved(
                    recorder.clock()
                        .tick(),
                    controllerAbsPos,
                    mapName));
        }

        if (CACHE_MAP_FIELD != null) try {
            GTRecipe[] cache = (GTRecipe[]) CACHE_MAP_FIELD.get(backend);
            for (int i = 0; i < cache.length; i++) {
                if (cache[i] == recipe) cache[i] = null;
            }
        } catch (IllegalAccessException e) {
            LOG.warn("TestRecipeScope: could not clear RecipeMapBackend.cacheMap: {}", e.getMessage());
        }

        TileEntity te = world.getTileEntity(controllerAbsPos.x(), controllerAbsPos.y(), controllerAbsPos.z());
        if (!(te instanceof IGregTechTileEntity igte)) return;
        if (!(igte.getMetaTileEntity() instanceof MTEMultiBlockBase multi)) return;

        if (PROCESSING_LOGIC_FIELD != null && LAST_RECIPE_FIELD != null) try {
            ProcessingLogic pl = (ProcessingLogic) PROCESSING_LOGIC_FIELD.get(multi);
            if (pl == null) return;
            GTRecipe last = (GTRecipe) LAST_RECIPE_FIELD.get(pl);
            if (last == recipe) {
                LOG.debug("TestRecipeScope: synthetic recipe was consumed at controller {}", controllerAbsPos);
                LAST_RECIPE_FIELD.set(pl, null);
            }
        } catch (IllegalAccessException e) {
            LOG.warn("TestRecipeScope: could not clear ProcessingLogic.lastRecipe: {}", e.getMessage());
        }
    }
}
