package com.gtnewhorizons.horizonqa.api.event;

import java.util.List;

/**
 * Read-only view of the per-test event log. Obtain via
 * {@code helper.getRecorder()}.
 */
public interface EventLog {

    /** Unmodifiable view of all recorded events in emit order. */
    List<TestEvent> snapshot();
}
