package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;
import com.gtnewhorizons.horizonqa.api.event.state.DeformedCause;

@Experimental
@Desugar
public record MachineDeformed(int tick, TestPos controller, DeformedCause cause) implements TestEvent {

    @Override
    public String category() {
        return Category.STRUCTURE;
    }

    @Override
    public String summary() {
        return "Multiblock deformed at " + controller + " (" + cause + ")";
    }
}
