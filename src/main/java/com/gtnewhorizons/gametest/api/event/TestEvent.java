package com.gtnewhorizons.gametest.api.event;

import com.gtnewhorizons.gametest.api.annotation.Experimental;

/**
 * Ordered, typed entry in a {@link com.gtnewhorizons.gametest.core.TestEventRecorder TestEventRecorder} log.
 * Records exposing {@code TestEvent} carry the structured payload as their own record components; this
 * interface only contributes the fields that every reporter needs: a monotonic logical {@code tick}, a
 * coarse {@code category} for filtering, and a one-line {@code summary} suitable for direct inclusion in
 * a JUnit {@code <system-out>} block or a console tail.
 */
@Experimental
public interface TestEvent {

    /** Logical tick when this event was recorded — ticks of simulated machine time since test start. */
    int tick();

    /** Coarse bucket; one of {@link Category}. */
    String category();

    /** Single-line human-readable description. Reporters print this verbatim. */
    String summary();

    /** Category constants. Strings (not an enum) so records can be added without touching this file. */
    final class Category {

        public static final String LIFECYCLE = "lifecycle";
        public static final String STRUCTURE = "structure";
        public static final String RECIPE = "recipe";
        public static final String RESOURCE = "resource";
        public static final String ENERGY = "energy";
        public static final String MAINTENANCE = "maintenance";
        public static final String SAFETY = "safety";
        public static final String FAILURE = "failure";
        public static final String DIAGNOSTIC = "diagnostic";

        private Category() {}
    }
}
