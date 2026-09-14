package com.gtnewhorizons.horizonqa.report;

import com.github.bsideup.jabel.Desugar;

/** Monotonic elapsed time. State distinguishes an ended measurement from a partial observation or no measurement. */
@Desugar
public record ElapsedTime(double seconds, State state) {

    public static ElapsedTime unavailable() {
        return new ElapsedTime(0, State.UNAVAILABLE);
    }

    public enum State {
        UNAVAILABLE,
        RUNNING,
        COMPLETE,
        PARTIAL
    }
}
