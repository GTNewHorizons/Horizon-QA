package com.gtnewhorizons.horizonqa.api.client;

import java.util.Objects;
import java.util.function.Function;

/** A reusable description and live lookup. Creating a target never queries or captures the current GUI. */
public final class ClientTarget {

    final String description;
    final Function<ClientTest, ClickTarget> resolver;

    private ClientTarget(String description, Function<ClientTest, ClickTarget> resolver) {
        this.description = Objects.requireNonNull(description, "description");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * Describes a target whose resolver runs on the client thread during execution.
     * Return null while unavailable, or throw when ambiguous. Each observation retains the existing
     * {@link ClickTarget} identity, visible-bounds and hit-test contract.
     */
    public static ClientTarget of(String description, Function<ClientTest, ClickTarget> resolver) {
        return new ClientTarget(description, resolver);
    }
}
