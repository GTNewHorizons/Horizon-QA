package com.gtnewhorizons.horizonqa.internal;

import com.gtnewhorizons.horizonqa.api.GameTestAssertException;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.internal.GameTestSequence.SequenceStepSnapshot;

public final class SequenceStepTimeoutException extends GameTestAssertException {

    private final SequenceStepSnapshot step;
    private final GameTestAssertException positionedCause;

    SequenceStepTimeoutException(SequenceStepSnapshot step, AssertionError lastAssertion) {
        super(message(step, lastAssertion), 0, 0, 0);
        this.step = step;
        this.positionedCause = lastAssertion instanceof GameTestAssertException assertion && assertion.hasPosition()
            ? assertion
            : null;
        initCause(lastAssertion);
    }

    public SequenceStepSnapshot step() {
        return step;
    }

    @Override
    public boolean hasPosition() {
        return positionedCause != null;
    }

    @Override
    public int getX() {
        return positionedCause != null ? positionedCause.getX() : super.getX();
    }

    @Override
    public int getY() {
        return positionedCause != null ? positionedCause.getY() : super.getY();
    }

    @Override
    public int getZ() {
        return positionedCause != null ? positionedCause.getZ() : super.getZ();
    }

    @Override
    public TestPos getPos() {
        return positionedCause != null ? positionedCause.getPos() : super.getPos();
    }

    private static String message(SequenceStepSnapshot step, AssertionError lastAssertion) {
        StringBuilder message = new StringBuilder("Sequence ");
        message.append(step.describe());
        message.append(" did not complete within ");
        message.append(step.maxTicks());
        message.append(" tick(s) after ")
            .append(step.attempts())
            .append(" attempt(s)");
        if (lastAssertion != null) {
            String detail = lastAssertion.getMessage();
            message.append(". Last assertion: ");
            message.append(
                detail == null || detail.isEmpty() ? lastAssertion.getClass()
                    .getName() : detail);
        }
        return message.toString();
    }
}
