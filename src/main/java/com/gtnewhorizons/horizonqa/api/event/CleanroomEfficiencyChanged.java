package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

@Experimental
@Desugar
public record CleanroomEfficiencyChanged(int tick, TestPos controller, int efficiencyTenThousandths)
    implements TestEvent {

    @Override
    public String category() {
        return Category.DIAGNOSTIC;
    }

    @Override
    public String summary() {
        return String.format("Cleanroom efficiency at %s: %.2f %%", controller, efficiencyTenThousandths / 100.0);
    }
}
