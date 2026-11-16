package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record BeforeBatchRan(int tick, String batch, String methodName) implements TestEvent {

    @Override
    public String category() {
        return Category.LIFECYCLE;
    }

    @Override
    public String summary() {
        return "@BeforeBatch '" + methodName + "' ran for batch '" + batch + "'";
    }
}
