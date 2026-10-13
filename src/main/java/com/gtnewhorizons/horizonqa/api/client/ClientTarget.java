package com.gtnewhorizons.horizonqa.api.client;

import java.util.Objects;
import java.util.function.Function;

/** A named, reusable resolver for a live GUI target. Creating it never reads client state. */
public final class ClientTarget {

    final String description;
    final Function<ClientTest, ClickTarget> resolver;

    private ClientTarget(String description, Function<ClientTest, ClickTarget> resolver) {
        this.description = Objects.requireNonNull(description, "description");
        if (description.trim()
            .isEmpty()) throw new IllegalArgumentException("Target description must not be empty");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * Creates a target whose resolver runs on the client thread during interaction readiness checks.
     * Return {@code null} while absent; throw on ambiguity. Resolve current bounds and readiness each time,
     * rather than retaining a screen or widget from an earlier step. GUI toolkit lookup belongs to the consumer.
     *
     * @param description target name used in sequence and input diagnostics
     * @param resolver    consumer-owned live target lookup
     */
    public static ClientTarget of(String description, Function<ClientTest, ClickTarget> resolver) {
        return new ClientTarget(description, resolver);
    }
}
