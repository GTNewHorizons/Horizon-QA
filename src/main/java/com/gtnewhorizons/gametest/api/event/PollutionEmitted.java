package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

@Experimental
@Desugar
public record PollutionEmitted(int tick, TestPos originChunk, long amount, long cumulativeSinceStart)
    implements TestEvent {

    @Override
    public String category() {
        return Category.DIAGNOSTIC;
    }

    @Override
    public String summary() {
        return "Pollution emitted at " + originChunk + ": " + amount + " (cumulative " + cumulativeSinceStart + ")";
    }
}
