package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.TestPos;

@Desugar
public record RecipeFinished(int tick, TestPos controller, int durationTicks) implements TestEvent {

    @Override
    public String category() {
        return Category.RECIPE;
    }

    @Override
    public String summary() {
        return "Recipe finished at " + controller + " (took " + durationTicks + "t)";
    }
}
