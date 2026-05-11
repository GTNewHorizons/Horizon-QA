package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

@Experimental
@Desugar
public record MachineDisabled(int tick, TestPos controller, String reason) implements TestEvent {

    @Override
    public String category() {
        return Category.STRUCTURE;
    }

    @Override
    public String summary() {
        return "Machine disabled at " + controller + " (" + reason + ")";
    }
}
