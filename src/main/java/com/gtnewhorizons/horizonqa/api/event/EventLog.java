package com.gtnewhorizons.horizonqa.api.event;

import java.util.List;

import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

/**
 * Read-only view of the per-test event log. Obtain via
 * {@code helper.getRecorder()}.
 */
@Experimental
public interface EventLog {

    /** Unmodifiable view of all recorded events in emit order. */
    List<TestEvent> snapshot();
}
