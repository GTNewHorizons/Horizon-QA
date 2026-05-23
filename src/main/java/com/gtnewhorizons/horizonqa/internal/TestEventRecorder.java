package com.gtnewhorizons.horizonqa.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.gtnewhorizons.horizonqa.api.event.EventLog;
import com.gtnewhorizons.horizonqa.api.event.EventOverflow;
import com.gtnewhorizons.horizonqa.api.event.TestEvent;

/**
 * Per-test ordered log of {@link TestEvent}s. One instance lives on every {@link GameTestInstance}.
 *
 * <p>
 * Recording is globally toggled by the {@code horizonqa.events} system property: passing
 * {@code -Dhorizonqa.events=off} makes {@link #record} an unconditional no-op that does not even invoke
 * the supplier — no record allocation, no payload computation.
 *
 * <p>
 * Single-thread by construction: a test instance ticks on the server thread, and time-warp re-entry
 * happens on the same thread. No synchronization.
 */
public final class TestEventRecorder implements EventLog {

    private static final boolean ENABLED = !"off".equalsIgnoreCase(System.getProperty("horizonqa.events", "on"));
    private static final int MAX_EVENTS = 10_000;

    private final List<TestEvent> events;
    private final TestClock clock = new TestClock();
    private boolean overflowed;

    public TestEventRecorder() {
        this.events = ENABLED ? new ArrayList<>(64) : Collections.emptyList();
    }

    /**
     * Emit an event. The supplier is only invoked when recording is enabled and the per-test cap is not
     * yet reached, so callers can build complex payloads inside a lambda without paying the cost when
     * the recorder is disabled.
     */
    public void record(Supplier<? extends TestEvent> factory) {
        if (!ENABLED || overflowed) return;
        if (events.size() >= MAX_EVENTS - 1) {
            overflowed = true;
            events.add(new EventOverflow(clock.tick(), MAX_EVENTS));
            return;
        }
        TestEvent event = factory.get();
        if (event != null) events.add(event);
    }

    /** Unmodifiable view of the recorded events in emit order. */
    public List<TestEvent> snapshot() {
        if (!ENABLED) return Collections.emptyList();
        return Collections.unmodifiableList(events);
    }

    public TestClock clock() {
        return clock;
    }

    public static boolean isEnabled() {
        return ENABLED;
    }
}
