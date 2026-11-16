package com.gtnewhorizons.horizonqa.api.event;

import com.github.bsideup.jabel.Desugar;

/**
 * A named per-tick callback was registered or changed state.
 *
 * @param tick  logical event-log tick
 * @param name  callback diagnostic name
 * @param state {@code registered-enabled}, {@code registered-disabled}, {@code enabled},
 *              {@code disabled}, or {@code removed}
 */
@Desugar
public record TickCallbackStateChanged(int tick, String name, String state) implements TestEvent {

    @Override
    public String category() {
        return Category.DIAGNOSTIC;
    }

    @Override
    public String summary() {
        return "Per-tick callback '" + name + "' " + state.replace('-', ' ');
    }
}
