package com.gtnewhorizons.gametest.api.event;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

@Experimental
@Desugar
public record AssertionFailed(int tick, String message, String throwableType, TestPos failPos) implements TestEvent {

    @Override
    public String category() {
        return Category.FAILURE;
    }

    @Override
    public String summary() {
        String loc = failPos != null ? " at " + failPos : "";
        return "Assertion failed" + loc + ": " + message;
    }
}
