package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;
import com.gtnewhorizons.gametest.api.event.state.ExplodedCause;

@Experimental
@Desugar
public record MachineExploded(int tick, TestPos controller, ExplodedCause cause) implements TestEvent {

    @Override
    public String category() {
        return Category.SAFETY;
    }

    @Override
    public String summary() {
        return "Machine exploded at " + controller + " (" + cause + ")";
    }
}
