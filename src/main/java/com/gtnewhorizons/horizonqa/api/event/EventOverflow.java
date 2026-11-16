package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;

/** Replaces further appends once the recorder's per-test event cap is reached. */
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
