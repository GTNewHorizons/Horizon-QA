package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record WarpStarted(int tick, int maxTicks, int watchedControllers) implements TestEvent {

    @Override
    public String category() {
        return Category.LIFECYCLE;
    }

    @Override
    public String summary() {
        return "Time-warp started (maxTicks=" + maxTicks + ", watching " + watchedControllers + " controller(s))";
    }
}
