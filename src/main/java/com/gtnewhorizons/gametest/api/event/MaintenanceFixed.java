package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

@Experimental
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
