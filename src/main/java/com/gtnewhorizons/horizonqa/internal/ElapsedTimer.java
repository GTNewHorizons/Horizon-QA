package com.gtnewhorizons.horizonqa.internal;

import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.gtnewhorizons.horizonqa.report.ElapsedTime;

/** Server-thread-owned interval whose immutable observations can be published to reporters or the client. */
final class ElapsedTimer {

    private final Stopwatch stopwatch;
    private ElapsedTime.State state = ElapsedTime.State.UNAVAILABLE;

    ElapsedTimer(Ticker ticker) {
        stopwatch = Stopwatch.createUnstarted(ticker);
    }

    void start() {
        if (state != ElapsedTime.State.UNAVAILABLE) return;
        stopwatch.start();
        state = ElapsedTime.State.RUNNING;
    }

    void finish(boolean partial) {
        if (state != ElapsedTime.State.RUNNING) return;
        stopwatch.stop();
        state = partial ? ElapsedTime.State.PARTIAL : ElapsedTime.State.COMPLETE;
    }

    ElapsedTime snapshot() {
        return snapshot(false);
    }

    ElapsedTime snapshot(boolean interrupted) {
        ElapsedTime.State observed = interrupted && state == ElapsedTime.State.RUNNING ? ElapsedTime.State.PARTIAL
            : state;
        return new ElapsedTime(stopwatch.elapsed(TimeUnit.NANOSECONDS) / 1_000_000_000.0, observed);
    }
}
