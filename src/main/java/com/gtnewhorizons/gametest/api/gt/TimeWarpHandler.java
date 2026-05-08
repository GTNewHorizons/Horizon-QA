package com.gtnewhorizons.gametest.api.gt;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.gametest.api.annotation.Experimental;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

@Experimental
public class TimeWarpHandler {

    public static int fastForward(WorldServer world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
        int maxTicks, VirtualEUDynamo dynamo, StopCondition stopCondition) {

        int simulated = 0;
        for (int t = 0; t < maxTicks; t++) {
            if (dynamo != null) dynamo.tick();
            tickGTRegion(world, minX, minY, minZ, maxX, maxY, maxZ);
            simulated++;
            if (stopCondition != null && stopCondition.shouldStop()) break;
        }
        return simulated;
    }

    private static void tickGTRegion(WorldServer world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    TileEntity te = world.getTileEntity(x, y, z);
                    if (te instanceof IGregTechTileEntity) {
                        te.updateEntity();
                    }
                }
            }
        }
    }

    @FunctionalInterface
    public interface StopCondition {

        boolean shouldStop();
    }
}
