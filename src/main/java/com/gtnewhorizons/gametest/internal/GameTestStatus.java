package com.gtnewhorizons.gametest.internal;

public enum GameTestStatus {

    NOT_STARTED,
    RUNNING,
    PASSED,
    FAILED,
    TIMED_OUT;

    public boolean isDone() {
        return this == PASSED || this == FAILED || this == TIMED_OUT;
    }
}
