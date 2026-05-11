package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

@Experimental
@Desugar
public record RecipeNotFound(int tick, TestPos controller, String checkRecipeResultId) implements TestEvent {

    @Override
    public String category() {
        return Category.RECIPE;
    }

    @Override
    public String summary() {
        String reason = (checkRecipeResultId == null || checkRecipeResultId.isEmpty()) ? "unknown"
            : checkRecipeResultId;
        return "No recipe ran at " + controller + " (last check result: " + reason + ")";
    }
}
