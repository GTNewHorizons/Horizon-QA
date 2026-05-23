package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

@Experimental
@Desugar
public record TestRecipeInjected(int tick, TestPos controller, String recipeMap, int eut, int durationTicks)
    implements TestEvent {

    @Override
    public String category() {
        return Category.RECIPE;
    }

    @Override
    public String summary() {
        return "Test recipe injected into " + recipeMap
            + " for "
            + controller
            + " ("
            + eut
            + " EU/t × "
            + durationTicks
            + "t)";
    }
}
