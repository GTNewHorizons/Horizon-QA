package com.gtnewhorizons.gametest.internal;

/**
 * Monotonic logical tick counter shared by a single {@link TestEventRecorder}. Advanced once per server
 * tick by {@link GameTestInstance#tick()} and once per simulated tick by the time-warp handler, so the
 * tick on a {@link com.gtnewhorizons.gametest.api.event.TestEvent TestEvent} reflects ticks of simulated
 * machine time, not wall-clock server ticks.
 */
public final class TestClock {

    private int tick;

    public int tick() {
        return tick;
    }

    public void advance() {
        tick++;
    }
}
