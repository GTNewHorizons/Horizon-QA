package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;
import com.gtnewhorizons.horizonqa.api.event.state.FormedCause;
import com.gtnewhorizons.horizonqa.api.event.state.HatchTopology;

@Experimental
@Desugar
public record MachineFormed(int tick, TestPos controller, String mteClass, FormedCause cause, HatchTopology topology)
    implements TestEvent {

    @Override
    public String category() {
        return Category.STRUCTURE;
    }

    @Override
    public String summary() {
        return mteClass + " formed at " + controller + " (" + cause + ", " + topology.compact() + ")";
    }
}
