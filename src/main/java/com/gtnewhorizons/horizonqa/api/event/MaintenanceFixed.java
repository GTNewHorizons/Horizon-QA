package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.TestPos;

@Desugar
public record MaintenanceFixed(int tick, TestPos controller, String typesFixed) implements TestEvent {

    @Override
    public String category() {
        return Category.MAINTENANCE;
    }

    @Override
    public String summary() {
        return "Maintenance fixed at " + controller + " (" + typesFixed + ")";
    }
}
