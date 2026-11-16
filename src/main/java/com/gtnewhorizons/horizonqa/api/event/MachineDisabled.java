package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.TestPos;

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
