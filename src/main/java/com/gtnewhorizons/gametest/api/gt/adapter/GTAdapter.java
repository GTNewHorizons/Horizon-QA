package com.gtnewhorizons.gametest.api.gt.adapter;

import net.minecraft.world.chunk.Chunk;

import com.gtnewhorizons.gametest.api.annotation.Experimental;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/** GT-version-specific operations used by GTNH gametest helpers. */
@Experimental
public interface GTAdapter {

    /** Pollution units accumulated in {@code chunk}. */
    long getPollution(Chunk chunk);

    /** Cleanroom controller efficiency in the 0–10000 range (0.00 %–100.00 %). */
    int getEfficiency(IMetaTileEntity mte);
}
