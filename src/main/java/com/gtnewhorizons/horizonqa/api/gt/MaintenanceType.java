package com.gtnewhorizons.horizonqa.api.gt;

import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Experimental
public enum MaintenanceType {

    WRENCH,
    SCREWDRIVER,
    SOFT_MALLET,
    HARD_HAMMER,
    SOLDERING_TOOL,
    CROWBAR;

    boolean isOk(MTEMultiBlockBase multi) {
        return switch (this) {
            case WRENCH -> multi.mWrench;
            case SCREWDRIVER -> multi.mScrewdriver;
            case SOFT_MALLET -> multi.mSoftMallet;
            case HARD_HAMMER -> multi.mHardHammer;
            case SOLDERING_TOOL -> multi.mSolderingTool;
            case CROWBAR -> multi.mCrowbar;
        };
    }
}
