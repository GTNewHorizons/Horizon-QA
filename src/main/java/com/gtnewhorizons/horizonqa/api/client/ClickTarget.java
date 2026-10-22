package com.gtnewhorizons.horizonqa.api.client;

import java.awt.Rectangle;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * One observation of a real GUI element for clicking or scrolling. Discovery and hit testing belong to the GUI
 * integration.
 * Return a fresh observation from the resolver passed to
 * {@link ClientTest#click(int, String, java.util.function.Function)}.
 */
public final class ClickTarget {

    final Object identity;
    final Rectangle bounds;
    final BooleanSupplier ready;

    /**
     * @param identity      stable identity compared with {@link Objects#equals(Object, Object)}, such as a live widget
     *                      or a domain key. Do not allocate a new identity on every observation.
     * @param visibleBounds current visible hit area in active {@code GuiScreen} coordinates, copied on construction.
     *                      Apply parent clipping before constructing the target. Horizon also clips to the screen.
     * @param ready         checks that the GUI's actual hit test accepts the positioned pointer for this element.
     *                      Called on the client thread after a completed frame. It must not perform the click.
     */
    public ClickTarget(Object identity, Rectangle visibleBounds, BooleanSupplier ready) {
        this.identity = Objects.requireNonNull(identity, "identity");
        bounds = new Rectangle(Objects.requireNonNull(visibleBounds, "visibleBounds"));
        this.ready = Objects.requireNonNull(ready, "ready");
    }
}
