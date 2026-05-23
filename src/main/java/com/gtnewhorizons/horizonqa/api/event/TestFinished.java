package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

@Experimental
@Desugar
public record TestFinished(int tick, String testId, String status, int simulatedTicks) implements TestEvent {

    @Override
    public String category() {
        return Category.LIFECYCLE;
    }

    @Override
    public String summary() {
        return "Test " + testId + " " + status + " after " + simulatedTicks + " simulated tick(s)";
    }
}
