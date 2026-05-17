package com.gtnewhorizons.gametest.api.gt.adapter;

import net.minecraft.world.chunk.Chunk;

import com.gtnewhorizons.gametest.api.annotation.Experimental;
import com.gtnewhorizons.gametest.api.event.state.HatchTopology;
import com.gtnewhorizons.gametest.api.event.state.MaintenanceSnapshot;
import com.gtnewhorizons.gametest.api.event.state.RecipeStateSnapshot;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** GT-version-specific operations used by GTNH gametest helpers. */
@Experimental
@SuppressWarnings("unused")
public interface GTAdapter {

    /** Pollution units accumulated in {@code chunk}. */
    long getPollution(Chunk chunk);

    /** Whether the multi-block structure is fully formed. */
    boolean isStructureFormed(IMetaTileEntity mte);

    /**
     * Whether the multi-block is currently processing a recipe (i.e. {@code mMaxProgresstime > 0}).
     */
    boolean isActive(IMetaTileEntity mte);

    /** Current recipe progress in ticks. */
    int getProgressTime(IMetaTileEntity mte);

    /** Total recipe duration in ticks for the current/last recipe. */
    int getMaxProgressTime(IMetaTileEntity mte);

    /**
     * Energy consumed (or produced) per tick for the current recipe. Negative values indicate consumption, positive
     * values indicate generation. Returned as {@code long} because
     * {@link gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase}
     * subclasses (EBF, fusion, etc.) use a {@code long}-backed energy field that can exceed {@code int} range.
     */
    long getEUt(IMetaTileEntity mte);

    /** Cleanroom controller efficiency in the 0–10000 range (0.00 %–100.00 %). */
    int getEfficiency(IMetaTileEntity mte);

    /**
     * Number of maintenance issues that have been repaired, in the range
     * 0–6 (where 6 means fully maintained).
     */
    int getRepairStatus(IMetaTileEntity mte);

    /**
     * Fix all six maintenance issues on {@code mte} immediately. Useful in test set-up to skip the maintenance
     * requirement.
     */
    void fixAllMaintenanceIssues(IMetaTileEntity mte);

    /** Total number of recipes completed since the machine was placed. */
    long getRecipesDone(IMetaTileEntity mte);

    /**
     * The parallel count used during the last recipe check. Returns 0 when no recipe has been processed yet.
     */
    int getLastParallel(IMetaTileEntity mte);

    /**
     * The string identifier of the last {@code CheckRecipeResult} (e.g. {@code "success"}, {@code "no_recipe"}, …).
     * Never {@code null}.
     */
    String getCheckRecipeResultId(IMetaTileEntity mte);

    /** Bundled recipe-state read used by the warp differ. Single call to minimise per-tick polling cost. */
    RecipeStateSnapshot snapshotRecipeState(IMetaTileEntity mte);

    /** Bitmask of the six maintenance flags. A set bit means the issue is currently present. */
    MaintenanceSnapshot snapshotMaintenance(IMetaTileEntity mte);

    /** Sizes of the standard hatch lists. Used for {@code MachineFormed} event payloads. */
    HatchTopology snapshotHatches(IMetaTileEntity mte);

    /** N-th fluid output hatch in canonical multiblock ordering; {@code null} if out of range or empty. */
    IGregTechTileEntity getOutputHatchTE(IMetaTileEntity mte, int index);
}
