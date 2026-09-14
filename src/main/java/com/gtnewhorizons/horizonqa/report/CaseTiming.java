package com.gtnewhorizons.horizonqa.report;

import com.github.bsideup.jabel.Desugar;

/** Test execution begins at invocation, after fixture placement. Cleanup uses the existing teardown lifecycle. */
@Desugar
public record CaseTiming(ElapsedTime total, ElapsedTime execution, ElapsedTime cleanup) {

    public static CaseTiming unavailable() {
        return new CaseTiming(ElapsedTime.unavailable(), ElapsedTime.unavailable(), ElapsedTime.unavailable());
    }
}
