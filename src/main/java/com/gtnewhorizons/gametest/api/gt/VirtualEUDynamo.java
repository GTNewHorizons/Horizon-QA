package com.gtnewhorizons.gametest.api.gt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;
import com.gtnewhorizons.gametest.api.event.EUBufferOverflow;
import com.gtnewhorizons.gametest.internal.TestEventRecorder;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

@Experimental
class VirtualEUDynamo {

    private final List<EUSupplyJob> jobs = new ArrayList<>();
    private final TestEventRecorder recorder;

    VirtualEUDynamo(TestEventRecorder recorder) {
        this.recorder = recorder;
    }

    void addJob(WorldServer world, int absX, int absY, int absZ, long voltage, long amperage, int durationTicks) {
        jobs.add(new EUSupplyJob(world, absX, absY, absZ, voltage, amperage, durationTicks));
    }

    void tick() {
        Iterator<EUSupplyJob> it = jobs.iterator();
        while (it.hasNext()) {
            EUSupplyJob job = it.next();
            if (job.remainingTicks <= 0) {
                it.remove();
                continue;
            }
            TileEntity te = job.world.getTileEntity(job.absX, job.absY, job.absZ);
            if (te instanceof IGregTechTileEntity igte) {
                long attempted = job.voltage * job.amperage;
                // GT's increaseStoredEnergyUnits is all-or-nothing: if the buffer is at or above capacity
                // when the call enters, the whole push is rejected; otherwise the full amount is credited
                // even if it spills past capacity. So overflow detection is just "buffer already full".
                // Emit at most once per supply job so a recipe that under-consumes by a small margin
                // (e.g. 2048 EU/t supply with a 1920 EU/t recipe) doesn't spam one event per cycle.
                if (recorder != null && !job.overflowEventEmitted) {
                    long stored = igte.getStoredEU();
                    long capacity = igte.getEUCapacity();
                    if (stored >= capacity) {
                        recorder.record(
                            () -> new EUBufferOverflow(
                                recorder.clock()
                                    .tick(),
                                new TestPos(job.absX, job.absY, job.absZ),
                                attempted,
                                0L));
                        job.overflowEventEmitted = true;
                    }
                }
                boolean doNotExceedCapacity = false;
                igte.increaseStoredEnergyUnits(attempted, doNotExceedCapacity);
            }
            job.remainingTicks--;
            if (job.remainingTicks <= 0) {
                it.remove();
            }
        }
    }

    boolean hasActiveJobs() {
        return !jobs.isEmpty();
    }

    private static final class EUSupplyJob {

        final WorldServer world;
        final int absX;
        final int absY;
        final int absZ;
        final long voltage;
        final long amperage;
        int remainingTicks;
        boolean overflowEventEmitted;

        EUSupplyJob(WorldServer world, int absX, int absY, int absZ, long voltage, long amperage, int durationTicks) {
            this.world = world;
            this.absX = absX;
            this.absY = absY;
            this.absZ = absZ;
            this.voltage = voltage;
            this.amperage = amperage;
            this.remainingTicks = durationTicks;
        }
    }
}
