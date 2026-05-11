package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

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
