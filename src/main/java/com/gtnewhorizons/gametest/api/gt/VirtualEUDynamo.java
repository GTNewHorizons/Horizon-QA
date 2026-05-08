package com.gtnewhorizons.gametest.api.gt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.gametest.api.annotation.Experimental;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

@Experimental
public class VirtualEUDynamo {

    private final List<EUSupplyJob> jobs = new ArrayList<>();

    public void addJob(WorldServer world, int absX, int absY, int absZ, long voltage, long amperage,
        int durationTicks) {
        jobs.add(new EUSupplyJob(world, absX, absY, absZ, voltage, amperage, durationTicks));
    }

    public void tick() {
        Iterator<EUSupplyJob> it = jobs.iterator();
        while (it.hasNext()) {
            EUSupplyJob job = it.next();
            if (job.remainingTicks <= 0) {
                it.remove();
                continue;
            }
            TileEntity te = job.world.getTileEntity(job.absX, job.absY, job.absZ);
            if (te instanceof IGregTechTileEntity igte) {
                boolean doNotExceedCapacity = false;
                igte.increaseStoredEnergyUnits(job.voltage * job.amperage, doNotExceedCapacity);
            }
            job.remainingTicks--;
            if (job.remainingTicks <= 0) {
                it.remove();
            }
        }
    }

    public boolean hasActiveJobs() {
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
