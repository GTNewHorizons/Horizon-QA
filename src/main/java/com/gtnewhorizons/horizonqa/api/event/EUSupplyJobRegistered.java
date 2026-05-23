package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

@Experimental
@Desugar
public record EUSupplyJobRegistered(int tick, TestPos hatch, long voltage, long amperage, int durationTicks)
    implements TestEvent {

    @Override
    public String category() {
        return Category.ENERGY;
    }

    @Override
    public String summary() {
        return "EU supply job: " + voltage + " EU/t × " + amperage + " A for " + durationTicks + "t into " + hatch;
    }
}
