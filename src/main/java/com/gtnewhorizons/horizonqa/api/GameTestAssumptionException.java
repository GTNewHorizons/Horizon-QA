package com.gtnewhorizons.horizonqa.api;

/**
 * Signals that a runtime precondition for a game test is not satisfied. Horizon-QA treats this as a
 * skipped test, not a test failure.
 */
public class GameTestAssumptionException extends RuntimeException {

    public GameTestAssumptionException(String reason) {
        super(reason == null || reason.isEmpty() ? "Runtime assumption was not satisfied" : reason);
    }
}
