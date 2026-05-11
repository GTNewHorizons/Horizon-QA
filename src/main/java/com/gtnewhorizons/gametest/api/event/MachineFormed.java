package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;
import com.gtnewhorizons.gametest.api.event.state.FormedCause;
import com.gtnewhorizons.gametest.api.event.state.HatchTopology;

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
