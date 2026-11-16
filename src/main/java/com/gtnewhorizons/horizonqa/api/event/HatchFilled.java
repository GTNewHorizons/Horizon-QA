package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.TestPos;

@Desugar
public record HatchFilled(int tick, TestPos hatch, String fluidName, int amountMb, int accepted) implements TestEvent {

    @Override
    public String category() {
        return Category.RESOURCE;
    }

    @Override
    public String summary() {
        return "Filled " + accepted + "/" + amountMb + " mB of '" + fluidName + "' into " + hatch;
    }
}
