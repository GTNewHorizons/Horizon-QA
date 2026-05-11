package com.gtnewhorizons.gametest.api.gt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;
import com.gtnewhorizons.gametest.api.event.MachineDeformed;
import com.gtnewhorizons.gametest.api.event.MachineExploded;
import com.gtnewhorizons.gametest.api.event.MachineFormed;
import com.gtnewhorizons.gametest.api.event.MaintenanceIssueAppeared;
import com.gtnewhorizons.gametest.api.event.RecipeAborted;
import com.gtnewhorizons.gametest.api.event.RecipeFinished;
import com.gtnewhorizons.gametest.api.event.RecipeProgressed;
import com.gtnewhorizons.gametest.api.event.RecipeStarted;
import com.gtnewhorizons.gametest.api.event.state.DeformedCause;
import com.gtnewhorizons.gametest.api.event.state.ExplodedCause;
import com.gtnewhorizons.gametest.api.event.state.FormedCause;
import com.gtnewhorizons.gametest.api.event.state.HatchTopology;
import com.gtnewhorizons.gametest.api.event.state.MaintenanceSnapshot;
import com.gtnewhorizons.gametest.api.event.state.RecipeStateSnapshot;
import com.gtnewhorizons.gametest.api.gt.adapter.GTAdapter;
import com.gtnewhorizons.gametest.core.TestEventRecorder;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Per-warp diff helper. Pre-snapshots watched controllers, then on every simulated tick compares fresh state
 * against the last snapshot and records {@link com.gtnewhorizons.gametest.api.event.TestEvent}s for the
 * transitions described in the implementation plan.
 *
 * <p>
 * One instance per call to {@link TimeWarpHandler#fastForward}. All snapshot reads are quarantined behind the
 * supplied {@link GTAdapter}.
 */
@Experimental
final class WarpDiffer {

    private final WorldServer world;
    private final TestEventRecorder recorder;
    private final GTAdapter adapter;
    private final List<TestPos> watched;
    private final Map<TestPos, RecipeStateSnapshot> lastState = new HashMap<>();
    private final Map<TestPos, MaintenanceSnapshot> lastMaintenance = new HashMap<>();
    private final Map<TestPos, Integer> lastProgressMilestone = new HashMap<>();
    private final List<TestPos> dropped = new ArrayList<>();

    WarpDiffer(WorldServer world, TestEventRecorder recorder, GTAdapter adapter, List<TestPos> watched) {
        this.world = world;
        this.recorder = recorder;
        this.adapter = adapter;
        this.watched = new ArrayList<>(watched);
    }

    /**
     * Take pre-warp snapshots and emit {@code MachineFormed(OBSERVED_ON_FIRST_POLL)} for already-formed controllers.
     */
    void primeBeforeWarp() {
        for (TestPos pos : watched) {
            IMetaTileEntity mte = mteAt(pos);
            if (mte == null) {
                dropped.add(pos);
                continue;
            }
            RecipeStateSnapshot snap = adapter.snapshotRecipeState(mte);
            lastState.put(pos, snap);
            lastMaintenance.put(pos, adapter.snapshotMaintenance(mte));
            lastProgressMilestone.put(pos, 0);
            if (snap.formed()) {
                String cls = mte.getClass()
                    .getSimpleName();
                HatchTopology topo = adapter.snapshotHatches(mte);
                recorder.record(
                    () -> new MachineFormed(
                        recorder.clock()
                            .tick(),
                        pos,
                        cls,
                        FormedCause.OBSERVED_ON_FIRST_POLL,
                        topo));
            }
        }
        watched.removeAll(dropped);
        dropped.clear();
    }

    /** Diff each watched controller against its prior snapshot, emitting transitions. */
    void onTickEnd() {
        for (TestPos pos : watched) {
            IMetaTileEntity mte = mteAt(pos);
            if (mte == null) {
                handleControllerGone(pos);
                continue;
            }

            RecipeStateSnapshot prev = lastState.getOrDefault(pos, RecipeStateSnapshot.EMPTY);
            RecipeStateSnapshot now = adapter.snapshotRecipeState(mte);

            diffFormation(pos, mte, prev, now);
            diffRecipe(pos, prev, now);
            diffMaintenance(pos, mte);

            lastState.put(pos, now);
        }
        watched.removeAll(dropped);
        dropped.clear();
    }

    private void diffFormation(TestPos pos, IMetaTileEntity mte, RecipeStateSnapshot prev, RecipeStateSnapshot now) {
        if (prev.formed() == now.formed()) return;
        if (now.formed()) {
            String cls = mte.getClass()
                .getSimpleName();
            HatchTopology topo = adapter.snapshotHatches(mte);
            recorder.record(
                () -> new MachineFormed(
                    recorder.clock()
                        .tick(),
                    pos,
                    cls,
                    FormedCause.FORMED_DURING_WARP,
                    topo));
        } else {
            recorder.record(
                () -> new MachineDeformed(
                    recorder.clock()
                        .tick(),
                    pos,
                    DeformedCause.BLOCK_CHANGED));
        }
    }

    private void diffRecipe(TestPos pos, RecipeStateSnapshot prev, RecipeStateSnapshot now) {
        boolean wasRunning = prev.maxProgressTime() > 0 && prev.progressTime() > 0;
        boolean nowRunning = now.maxProgressTime() > 0 && now.progressTime() > 0;

        if (!wasRunning && nowRunning) {
            recorder.record(
                () -> new RecipeStarted(
                    recorder.clock()
                        .tick(),
                    pos,
                    now.eut(),
                    now.maxProgressTime(),
                    1));
            lastProgressMilestone.put(pos, 0);
        }

        if (nowRunning && now.maxProgressTime() > 0) {
            int percent = (int) (100L * now.progressTime() / now.maxProgressTime());
            int milestone = percent / 25 * 25;
            int last = lastProgressMilestone.getOrDefault(pos, 0);
            if (milestone > last && milestone > 0 && milestone < 100) {
                final int m = milestone;
                final int pt = now.progressTime();
                final int max = now.maxProgressTime();
                recorder.record(
                    () -> new RecipeProgressed(
                        recorder.clock()
                            .tick(),
                        pos,
                        pt,
                        max,
                        m));
                lastProgressMilestone.put(pos, milestone);
            }
        }

        if (wasRunning && !nowRunning) {
            int progressAtStop = prev.progressTime();
            int maxProgress = prev.maxProgressTime();
            boolean finishedNaturally = progressAtStop >= maxProgress - 1;
            if (finishedNaturally) {
                recorder.record(
                    () -> new RecipeFinished(
                        recorder.clock()
                            .tick(),
                        pos,
                        progressAtStop));
            } else {
                String reason = now.checkRecipeResultId() == null || now.checkRecipeResultId()
                    .isEmpty() ? "unknown" : now.checkRecipeResultId();
                recorder.record(
                    () -> new RecipeAborted(
                        recorder.clock()
                            .tick(),
                        pos,
                        progressAtStop,
                        maxProgress,
                        reason));
            }
            lastProgressMilestone.put(pos, 0);
        }
    }

    private void diffMaintenance(TestPos pos, IMetaTileEntity mte) {
        MaintenanceSnapshot prev = lastMaintenance.getOrDefault(pos, MaintenanceSnapshot.OK);
        MaintenanceSnapshot now = adapter.snapshotMaintenance(mte);
        int newlySet = now.newlySetSince(prev);
        if (newlySet != 0) {
            for (int bit = 1; bit <= MaintenanceSnapshot.CROWBAR; bit <<= 1) {
                if ((newlySet & bit) != 0) {
                    final String name = MaintenanceSnapshot.nameOf(bit);
                    recorder.record(
                        () -> new MaintenanceIssueAppeared(
                            recorder.clock()
                                .tick(),
                            pos,
                            name));
                }
            }
        }
        lastMaintenance.put(pos, now);
    }

    private void handleControllerGone(TestPos pos) {
        RecipeStateSnapshot prev = lastState.get(pos);
        boolean wasFormed = prev != null && prev.formed();
        recorder.record(
            () -> new MachineExploded(
                recorder.clock()
                    .tick(),
                pos,
                ExplodedCause.UNKNOWN));
        if (wasFormed) {
            recorder.record(
                () -> new MachineDeformed(
                    recorder.clock()
                        .tick(),
                    pos,
                    DeformedCause.POST_EXPLOSION));
        }
        dropped.add(pos);
    }

    private IMetaTileEntity mteAt(TestPos pos) {
        TileEntity te = world.getTileEntity(pos.x(), pos.y(), pos.z());
        if (!(te instanceof IGregTechTileEntity igte)) return null;
        return igte.getMetaTileEntity();
    }
}
