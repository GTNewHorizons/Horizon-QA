package com.gtnewhorizons.horizonqa.internal;

public enum GameTestStatus {

    NOT_STARTED,
    RUNNING,
    PASSED,
    FAILED,
    ERROR,
    TIMED_OUT;

    public boolean isDone() {
        return this == PASSED || this == FAILED || this == ERROR || this == TIMED_OUT;
    }
}
