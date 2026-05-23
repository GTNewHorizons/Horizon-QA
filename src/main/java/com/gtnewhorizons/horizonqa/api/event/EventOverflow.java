package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

/** Replaces further appends once the recorder's per-test event cap is reached. */
@Experimental
@Desugar
public record EventOverflow(int tick, int cap) implements TestEvent {

    @Override
    public String category() {
        return Category.DIAGNOSTIC;
    }

    @Override
    public String summary() {
        return "Event log truncated at cap=" + cap + " — further events dropped";
    }
}
