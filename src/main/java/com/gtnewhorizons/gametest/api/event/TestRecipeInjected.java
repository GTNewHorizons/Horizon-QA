package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

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
