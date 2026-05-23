package com.gtnewhorizons.horizonqa.api.event.state;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

/**
 * Bitmask of the six standard GT maintenance flags. A set bit means the corresponding issue is currently
 * <em>present</em> (the tool flag is {@code false} on the controller). When {@code mask == 0} the machine is fully
 * maintained.
 */
@Experimental
@Desugar
public record MaintenanceSnapshot(int mask) {

    public static final int WRENCH = 1;
    public static final int SCREWDRIVER = 1 << 1;
    public static final int SOFT_MALLET = 1 << 2;
    public static final int HARD_HAMMER = 1 << 3;
    public static final int SOLDERING_TOOL = 1 << 4;
    public static final int CROWBAR = 1 << 5;

    public static final MaintenanceSnapshot OK = new MaintenanceSnapshot(0);

    public boolean has(int flag) {
        return (mask & flag) != 0;
    }

    public int newlySetSince(MaintenanceSnapshot prior) {
        return mask & ~prior.mask;
    }

    public static String nameOf(int singleFlag) {
        return switch (singleFlag) {
            case WRENCH -> "WRENCH";
            case SCREWDRIVER -> "SCREWDRIVER";
            case SOFT_MALLET -> "SOFT_MALLET";
            case HARD_HAMMER -> "HARD_HAMMER";
            case SOLDERING_TOOL -> "SOLDERING_TOOL";
            case CROWBAR -> "CROWBAR";
            default -> "UNKNOWN(" + singleFlag + ")";
        };
    }
}
