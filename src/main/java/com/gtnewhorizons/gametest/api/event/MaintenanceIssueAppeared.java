package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

@Experimental
@Desugar
public record MaintenanceIssueAppeared(int tick, TestPos controller, String issueType) implements TestEvent {

    @Override
    public String category() {
        return Category.MAINTENANCE;
    }

    @Override
    public String summary() {
        return "Maintenance issue '" + issueType + "' appeared at " + controller;
    }
}
