package com.gtnewhorizons.horizonqa.visual;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.gtnewhorizons.horizonqa.GameTestJvmFlags;
import com.gtnewhorizons.horizonqa.visual.drawables.GhostBlockDiff;

public final class VisualManager {

    private static final List<GhostBlockDiff> GHOSTS = new CopyOnWriteArrayList<>();

    private VisualManager() {}

    public static void addFailureGhost(int x, int y, int z, String label) {
        if (GameTestJvmFlags.isEnabled()) return;
        GHOSTS.add(new GhostBlockDiff(x, y, z, 1.00f, 0.15f, 0.15f, label));
    }

    public static void addExpectedGhost(int x, int y, int z, String label) {
        if (GameTestJvmFlags.isEnabled()) return;
        GHOSTS.add(new GhostBlockDiff(x, y, z, 0.15f, 1.00f, 0.35f, label));
    }

    public static void clearAll() {
        GHOSTS.clear();
    }

    static List<GhostBlockDiff> getGhosts() {
        return Collections.unmodifiableList(GHOSTS);
    }
}
