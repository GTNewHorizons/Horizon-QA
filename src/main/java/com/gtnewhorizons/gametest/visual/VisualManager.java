package com.gtnewhorizons.gametest.visual;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.gtnewhorizons.gametest.GameTestJvmFlags;
import com.gtnewhorizons.gametest.visual.drawables.GhostBlockDiff;

/**
 * Central server-safe API for submitting additional rendering requests to
 * {@link GameTestOverlayRenderer}. All storage uses {@link CopyOnWriteArrayList}
 * so server-thread writes and render-thread reads are safely concurrent.
 *
 * <p>All methods are no-ops in headless CI mode ({@link GameTestJvmFlags#isEnabled()}).
 *
 * <p><b>Note:</b> Per-cell status visuals (beacons, bounding boxes, floating text)
 * are derived directly from {@code InteractiveTestSession} by the renderer on each frame
 * and do not need to be submitted here. This class handles supplemental overlays that
 * test code or helpers may add explicitly — currently ghost-block diffs.
 */
public final class VisualManager {

    private static final List<GhostBlockDiff> GHOSTS = new CopyOnWriteArrayList<>();

    private VisualManager() {}

    /**
     * Add a red ghost block at the given world-space block coordinates to mark an
     * assertion-failure position. Intended to be called from {@code GameTestHelper}
     * when a block assertion throws.
     *
     * @param label short failure message shown as a floating label; may be {@code null}
     */
    public static void addFailureGhost(int x, int y, int z, String label) {
        if (GameTestJvmFlags.isEnabled()) return;
        GHOSTS.add(new GhostBlockDiff(x, y, z, 1.00f, 0.15f, 0.15f, label));
    }

    /**
     * Add a green ghost block at the given world-space coordinates to visualise an
     * expected position (e.g. "I expected this block here").
     *
     * @param label short label; may be {@code null}
     */
    public static void addExpectedGhost(int x, int y, int z, String label) {
        if (GameTestJvmFlags.isEnabled()) return;
        GHOSTS.add(new GhostBlockDiff(x, y, z, 0.15f, 1.00f, 0.35f, label));
    }

    /** Remove all ghost-block overlays. Called by {@code InteractiveTestSession.clearAll()}. */
    public static void clearAll() {
        GHOSTS.clear();
    }

    /**
     * Returns a live but thread-safe view of all registered ghost blocks.
     * Iteration on the render thread is safe against concurrent adds.
     */
    static List<GhostBlockDiff> getGhosts() {
        return Collections.unmodifiableList(GHOSTS);
    }
}
