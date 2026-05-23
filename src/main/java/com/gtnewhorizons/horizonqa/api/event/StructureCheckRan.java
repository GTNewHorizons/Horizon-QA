package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

@Experimental
@Desugar
public record StructureCheckRan(int tick, TestPos controller, boolean forceReset, boolean resultFormed)
    implements TestEvent {

    @Override
    public String category() {
        return Category.STRUCTURE;
    }

    @Override
    public String summary() {
        return "checkStructure(forceReset=" + forceReset
            + ") at "
            + controller
            + " → "
            + (resultFormed ? "formed" : "still unformed");
    }
}
