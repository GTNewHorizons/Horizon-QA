package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

@Experimental
@Desugar
public record HatchVoltageMismatch(int tick, TestPos hatch, long suppliedVoltage, long hatchMaxVoltage)
    implements TestEvent {

    @Override
    public String category() {
        return Category.ENERGY;
    }

    @Override
    public String summary() {
        return "Hatch voltage mismatch at " + hatch
            + ": supplied "
            + suppliedVoltage
            + " EU/t > hatch max "
            + hatchMaxVoltage
            + " EU/t";
    }
}
