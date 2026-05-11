package com.gtnewhorizons.gametest.api.gt;

import java.util.Collections;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;
import com.gtnewhorizons.gametest.api.event.WarpFinished;
import com.gtnewhorizons.gametest.api.event.WarpStarted;
import com.gtnewhorizons.gametest.api.gt.adapter.GTAdapter;
import com.gtnewhorizons.gametest.core.TestEventRecorder;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

@Experimental
public class TimeWarpHandler {

    public static int fastForward(WorldServer world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
        int maxTicks, VirtualEUDynamo dynamo, StopCondition stopCondition) {
        return fastForward(
            world,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            maxTicks,
            dynamo,
            stopCondition,
            null,
            null,
            Collections.emptyList());
    }

    public static int fastForward(WorldServer world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
        int maxTicks, VirtualEUDynamo dynamo, StopCondition stopCondition, TestEventRecorder recorder,
        GTAdapter adapter, List<TestPos> watchedControllers) {

        WarpDiffer differ = null;
        if (recorder != null && adapter != null && watchedControllers != null && !watchedControllers.isEmpty()) {
            differ = new WarpDiffer(world, recorder, adapter, watchedControllers);
            differ.primeBeforeWarp();
        }
        if (recorder != null) {
            final int watched = watchedControllers == null ? 0 : watchedControllers.size();
            recorder.record(
                () -> new WarpStarted(
                    recorder.clock()
                        .tick(),
                    maxTicks,
                    watched));
        }

        int simulated = 0;
        String stopReason = "completed";
        for (int t = 0; t < maxTicks; t++) {
            if (recorder != null) recorder.clock()
                .advance();
            if (dynamo != null) dynamo.tick();
            tickGTRegion(world, minX, minY, minZ, maxX, maxY, maxZ);
            simulated++;
            if (differ != null) differ.onTickEnd();
            if (stopCondition != null && stopCondition.shouldStop()) {
                stopReason = "predicate";
                break;
            }
        }
        if (simulated == maxTicks && stopCondition != null) {
            stopReason = "timeout";
        }
        if (recorder != null) {
            final int s = simulated;
            final String reason = stopReason;
            recorder.record(
                () -> new WarpFinished(
                    recorder.clock()
                        .tick(),
                    s,
                    reason));
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
