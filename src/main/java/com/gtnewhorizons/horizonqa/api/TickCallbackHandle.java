package com.gtnewhorizons.horizonqa.api;

/**
 * Controls a callback registered through {@link GameTestHelper#onEachTick(String, Runnable)} or
 * {@link GameTestHelper#onEachTickDisabled(String, Runnable)}.
 *
 * <p>
 * A handle starts in the state selected by its registration method. Disabling is reversible; removal
 * is permanent. All state changes are idempotent, and enabling or disabling a removed handle has no
 * effect. Actual state changes are recorded in the test event log. Changes take effect before the next
 * attempt to invoke the callback, including a callback later in the current END-phase pass.
 */
public interface TickCallbackHandle {

    /** Enable this callback if it has not been removed. */
    void enable();

    /** Disable this callback without removing it. */
    void disable();

    /** Permanently remove this callback from the test. */
    void remove();

    /** Return whether this callback is currently enabled and has not been removed. */
    boolean isEnabled();

    /** Return whether this callback has been permanently removed. */
    boolean isRemoved();
}
