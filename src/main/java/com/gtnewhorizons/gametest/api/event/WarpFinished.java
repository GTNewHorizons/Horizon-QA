package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

@Experimental
@Desugar
public record WarpFinished(int tick, int simulatedTicks, String stopReason) implements TestEvent {

    @Override
    public String category() {
        return Category.LIFECYCLE;
    }

    @Override
    public String summary() {
        return "Time-warp finished after " + simulatedTicks + " simulated tick(s) (" + stopReason + ")";
    }
}
