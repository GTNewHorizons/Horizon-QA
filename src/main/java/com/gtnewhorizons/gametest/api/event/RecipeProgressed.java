package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

@Experimental
@Desugar
public record RecipeProgressed(int tick, TestPos controller, int progressTime, int maxProgressTime, int percent)
    implements TestEvent {

    @Override
    public String category() {
        return Category.RECIPE;
    }

    @Override
    public String summary() {
        return "Recipe at " + controller + " " + percent + "% (" + progressTime + "/" + maxProgressTime + ")";
    }
}
