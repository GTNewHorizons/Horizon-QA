package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;

/**
 * Emitted after test cleanup has run and the final status is known.
 *
 * @param status one of {@code passed}, {@code skipped}, {@code failed}, {@code timed out}, or
 *               {@code error}
 */
@Desugar
public record TestFinished(int tick, String testId, String status, int simulatedTicks) implements TestEvent {

    @Override
    public String category() {
        return Category.LIFECYCLE;
    }

    @Override
    public String summary() {
        return "Test " + testId + " " + status + " after " + simulatedTicks + " simulated tick(s)";
    }
}
