package com.gtnewhorizons.horizonqa.report;

import com.github.bsideup.jabel.Desugar;

/** Time occupied by one sequence step, including queueing and retries. This is not CPU time. */
@Desugar
public record StepResult(int index, String label, String kind, String phase, String status, int attempts,
    long simulationTicks, int requestedMultiplier, ElapsedTime elapsed, String source, String operation,
    String executionSide) {}
