package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.TestPos;

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
