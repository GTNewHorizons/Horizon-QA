package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

@Experimental
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
