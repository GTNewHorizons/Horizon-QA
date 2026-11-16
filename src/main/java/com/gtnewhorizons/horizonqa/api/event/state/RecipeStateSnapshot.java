package com.gtnewhorizons.horizonqa.api.event.state;

import com.github.bsideup.jabel.Desugar;

/**
 * Immutable snapshot of a multiblock controller's recipe state at a single tick. Produced by
 * {@link com.gtnewhorizons.horizonqa.api.gt.adapter.GTAdapter#snapshotRecipeState GTAdapter.snapshotRecipeState} so
 * that the warp differ in {@link com.gtnewhorizons.horizonqa.api.gt.TimeWarpHandler TimeWarpHandler} can compare across
 * ticks without referencing GT types.
 */
@Desugar
public record RecipeStateSnapshot(boolean formed, int progressTime, int maxProgressTime, long eut, int efficiency,
    String checkRecipeResultId, int parallels) {

    public static final RecipeStateSnapshot EMPTY = new RecipeStateSnapshot(false, 0, 0, 0L, 0, "", 0);

    public boolean isActive() {
        return maxProgressTime > 0;
    }
}
