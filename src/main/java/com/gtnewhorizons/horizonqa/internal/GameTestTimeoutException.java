package com.gtnewhorizons.horizonqa.internal;

final class GameTestTimeoutException extends AssertionError {

    GameTestTimeoutException(String message, AssertionError lastAssertion) {
        super(message);
        if (lastAssertion != null) initCause(lastAssertion);
    }
}
