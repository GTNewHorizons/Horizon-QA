package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

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
