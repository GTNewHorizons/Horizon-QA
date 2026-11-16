package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;

/**
 * A sequence step completed or failed. The human-readable summary omits timing for an immediate step,
 * reports only elapsed ticks when attempts and ticks match, and otherwise reports both values.
 *
 * @param tick         logical event-log tick
 * @param index        one-based step index
 * @param totalSteps   number of declared sequence steps
 * @param label        author label, or declaration source when unlabeled
 * @param outcome      {@code completed} or {@code failed}
 * @param attempts     number of action or condition attempts
 * @param elapsedTicks inclusive outer-test tick count from first attempt to completion
 */
@Desugar
public record SequenceStepFinished(int tick, int index, int totalSteps, String label, String outcome, int attempts,
    long elapsedTicks) implements TestEvent {

    @Override
    public String category() {
        return Category.SEQUENCE;
    }

    @Override
    public String summary() {
        String summary = capitalize(outcome) + " sequence step " + index + '/' + totalSteps + " '" + label + "'";
        if (attempts == 0 && elapsedTicks == 0) return summary + " without starting";
        if (attempts == 1 && elapsedTicks == 1) return summary;
        if (attempts == elapsedTicks) return summary + " after " + elapsedTicks + " ticks";
        return summary + " after "
            + attempts
            + (attempts == 1 ? " attempt" : " attempts")
            + " over "
            + elapsedTicks
            + (elapsedTicks == 1 ? " tick" : " ticks");
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return "Finished";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
