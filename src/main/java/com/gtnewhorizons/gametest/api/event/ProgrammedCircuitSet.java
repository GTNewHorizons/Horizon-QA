package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

@Experimental
@Desugar
public record ProgrammedCircuitSet(int tick, TestPos bus, int config) implements TestEvent {

    @Override
    public String category() {
        return Category.RESOURCE;
    }

    @Override
    public String summary() {
        return "Programmed circuit set to " + config + " in " + bus;
    }
}
