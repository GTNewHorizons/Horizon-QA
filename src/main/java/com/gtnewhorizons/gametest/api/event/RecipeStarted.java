package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

@Experimental
@Desugar
public record RecipeStarted(int tick, TestPos controller, long eut, int durationTicks, int parallels)
    implements TestEvent {

    @Override
    public String category() {
        return Category.RECIPE;
    }

    @Override
    public String summary() {
        return "Recipe started at " + controller + " (" + eut + " EU/t × " + durationTicks + "t, " + parallels + "p)";
    }
}
