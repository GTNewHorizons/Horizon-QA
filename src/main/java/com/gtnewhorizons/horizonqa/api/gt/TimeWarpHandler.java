package com.gtnewhorizons.horizonqa.api.gt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;
import com.gtnewhorizons.horizonqa.api.event.WarpFinished;
import com.gtnewhorizons.horizonqa.api.event.WarpStarted;
import com.gtnewhorizons.horizonqa.api.gt.adapter.GTAdapter;
import com.gtnewhorizons.horizonqa.internal.TestEventRecorder;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

@Experimental
class TimeWarpHandler {

    static int fastForward(WorldServer world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int maxTicks,
        VirtualEUDynamo dynamo, StopCondition stopCondition, TestEventRecorder recorder, GTAdapter adapter,
        List<TestPos> watchedControllers) {

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

    private static List<TileEntity> collectGTTileEntities(WorldServer world, int minX, int minY, int minZ, int maxX,
        int maxY, int maxZ) {

        List<TileEntity> result = new ArrayList<>();
        int chunkMinX = minX >> 4;
        int chunkMaxX = maxX >> 4;
        int chunkMinZ = minZ >> 4;
        int chunkMaxZ = maxZ >> 4;

        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
                for (TileEntity te : chunk.chunkTileEntityMap.values()) {
                    if (te == null) continue;
                    if (te.xCoord >= minX && te.xCoord <= maxX
                        && te.yCoord >= minY
                        && te.yCoord <= maxY
                        && te.zCoord >= minZ
                        && te.zCoord <= maxZ
                        && te instanceof IGregTechTileEntity) {
                        result.add(te);
                    }
                }
            }
        }
        result.sort(
            Comparator.comparingInt((TileEntity te) -> te.xCoord)
                .thenComparingInt(te -> te.yCoord)
                .thenComparingInt(te -> te.zCoord));

        return result;
    }

    private static void tickGTRegion(WorldServer world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        List<TileEntity> gtTileEntities = collectGTTileEntities(world, minX, minY, minZ, maxX, maxY, maxZ);

        for (TileEntity te : gtTileEntities) {
            if (!te.isInvalid()) {
                te.updateEntity();
            }
        }
    }

    @FunctionalInterface
    interface StopCondition {

        boolean shouldStop();
    }
}
