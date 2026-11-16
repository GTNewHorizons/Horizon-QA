package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;

/**
 * A sequence step reached its first execution attempt.
 *
 * @param tick          logical event-log tick
 * @param index         one-based step index
 * @param totalSteps    number of declared sequence steps
 * @param label         author label, or declaration source when unlabeled
 * @param kind          sequence step kind
 * @param phase         server tick phase
 * @param scheduledTick effective outer test tick scheduled by the sequence
 * @param source        declaration source location
 */
@Desugar
public record SequenceStepStarted(int tick, int index, int totalSteps, String label, String kind, String phase,
    long scheduledTick, String source) implements TestEvent {

    @Override
    public String category() {
        return Category.SEQUENCE;
    }

    @Override
    public String summary() {
        return "Started sequence step " + index
            + '/'
            + totalSteps
            + " '"
            + label
            + "' ("
            + kind
            + ' '
            + phase
            + ", scheduled t="
            + scheduledTick
            + ", declared at "
            + source
            + ')';
    }
}
