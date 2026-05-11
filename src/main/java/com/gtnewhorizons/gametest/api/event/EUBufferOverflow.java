package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

@Experimental
@Desugar
public record EUBufferOverflow(int tick, TestPos hatch, long attempted, long accepted) implements TestEvent {

    @Override
    public String category() {
        return Category.ENERGY;
    }

    @Override
    public String summary() {
        return "EU supply rejected at " + hatch
            + ": buffer at capacity, "
            + attempted
            + " EU/t push dropped (further rejections for this job suppressed)";
    }
}
