package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

@Experimental
@Desugar
public record RecipeAborted(int tick, TestPos controller, int progressAtAbort, int maxProgress, String reason)
    implements TestEvent {

    @Override
    public String category() {
        return Category.RECIPE;
    }

    @Override
    public String summary() {
        return "Recipe aborted at " + controller
            + " ("
            + progressAtAbort
            + "/"
            + maxProgress
            + "t, reason="
            + reason
            + ")";
    }
}
