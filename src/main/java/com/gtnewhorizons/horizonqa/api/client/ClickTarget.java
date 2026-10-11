package com.gtnewhorizons.horizonqa.api.client;

import java.awt.Rectangle;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** A consumer-owned live GUI target in scaled screen coordinates, resolved only on the client thread. */
public final class ClickTarget {

    public final Object identity;
    public final Rectangle bounds;
    public final BooleanSupplier ready;

    /**
     * @param identity stable widget identity, used to reject a replacement during pointer positioning
     * @param bounds   visible, clickable bounds in the current screen's coordinate system
     * @param ready    consumer's actual hit-test readiness after a rendered frame at the pointer
     */
    public ClickTarget(Object identity, Rectangle bounds, BooleanSupplier ready) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.bounds = new Rectangle(Objects.requireNonNull(bounds, "bounds"));
        this.ready = Objects.requireNonNull(ready, "ready");
    }
}
