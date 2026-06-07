package com.gtnewhorizons.horizonqa.api.gt;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.horizonqa.api.GameTestAssertException;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;
import com.gtnewhorizons.horizonqa.api.event.MachineFormed;
import com.gtnewhorizons.horizonqa.api.event.MaintenanceFixed;
import com.gtnewhorizons.horizonqa.api.event.StructureCheckRan;
import com.gtnewhorizons.horizonqa.api.event.state.FormedCause;
import com.gtnewhorizons.horizonqa.internal.TestEventRecorder;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchInputDebug;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import gregtech.common.tileentities.machines.MTEHatchInputME;

/**
 * Facade for a multiblock controller at a fixed world position. Hatch and bus handles are read from the live controller
 * on each call (not cached), so they stay valid across structure rescans.
 *
 * <p>
 * Create with {@link GTNHGameTestHelper#multiblock(TestPos)}. Mod-specific controllers that keep hatches off the
 * standard
 * {@link MTEMultiBlockBase} lists are not covered.
 */
@Experimental
public final class Multiblock {

    private static final int DEFAULT_RUN_TICKS = 1500;

    private final WorldServer world;
    private final TestPos absPos;
    private final GTNHGameTestHelper helper;

    Multiblock(GTNHGameTestHelper helper, WorldServer world, TestPos absPos) {
        this.helper = helper;
        this.world = world;
        this.absPos = absPos;
    }

    /**
     * Asserts the controller is fully formed. Runs {@link MTEMultiBlockBase#checkStructure(boolean, IGregTechTileEntity)} with
     * {@code forceReset = true} once if the structure is not yet valid, then fails if still unformed.
     *
     * <p>
     * Sets {@code mStartUpCheck = -1} on success, disabling the controller's periodic structure-re-check loop for
     * the remainder of the test. This is intentional: a mid-test re-check could silently un-form the structure and
     * invalidate subsequent assertions.
     *
     * @apiNote On TecTech multiblocks the {@code checkStructure(true)} fallback triggers the standard GT
     *          structure-check path, which does <em>not</em> call {@code clearHatches_EM()} (TecTech's method
     *          is not an {@code @Override}). This may leave stale TecTech-specific hatch state if the structure
     *          was previously formed. Acceptable for v0.1; prefer not to call this on TecTech multis that have
     *          already been formed once.
     */
    public void assertFormed() {
        MTEMultiBlockBase multi = resolveController();
        boolean wasFormed = multi.mMachine;
        boolean ranCheck = false;
        if (!multi.mMachine) {
            multi.checkStructure(true, multi.getBaseMetaTileEntity());
            ranCheck = true;
            final boolean nowFormed = multi.mMachine;
            TestEventRecorder rec = helper.recorder();
            rec.record(
                () -> new StructureCheckRan(
                    rec.clock()
                        .tick(),
                    absPos,
                    true,
                    nowFormed));
        }
        if (!multi.mMachine) {
            throw error(
                "Multiblock at " + absPos
                    + " is not formed (mMachine=false). Verify the template is placed correctly.");
        }
        multi.mStartUpCheck = -1;
        FormedCause cause = ranCheck ? FormedCause.FORCED_BY_ASSERTION
            : (wasFormed ? FormedCause.OBSERVED_ON_FIRST_POLL : FormedCause.FORMED_DURING_WARP);
        String mteClass = multi.getClass()
            .getSimpleName();
        TestEventRecorder rec = helper.recorder();
        rec.record(
            () -> new MachineFormed(
                rec.clock()
                    .tick(),
                absPos,
                mteClass,
                cause,
                helper.adapter()
                    .snapshotHatches(multi)));
    }

    /** Whether the controller reports a formed structure. */
    public boolean isFormed() {
        return helper.adapter()
            .isStructureFormed(resolveController());
    }

    /** {@link MTEMultiBlockBase#fixAllIssues()} then {@link MTEMultiBlockBase#enableWorking()}. */
    public void fixMaintenance() {
        MTEMultiBlockBase multi = resolveController();
        helper.adapter()
            .fixAllMaintenanceIssues(multi);
        multi.enableWorking();
        TestEventRecorder rec = helper.recorder();
        rec.record(
            () -> new MaintenanceFixed(
                rec.clock()
                    .tick(),
                absPos,
                "ALL"));
    }

    /**
     * Input bus by index in {@link MTEMultiBlockBase#mInputBusses}.
     *
     * @throws IndexOutOfBoundsException if {@code index} is not in range
     * @throws GameTestAssertException   if the resolved hatch is an {@link MTEHatchCraftingInputME} (ME crafting
     *                                   buffer); use {@link #inputs()} to iterate non-ME input buses instead
     * @apiNote Steam multiblocks ({@code MTESteamMultiBlockBase}) do not populate {@code mInputBusses}; this
     *          method will throw {@link IndexOutOfBoundsException} on them. Steam multis are not supported in v0.1.
     */
    public Bus inputBus(int index) {
        MTEMultiBlockBase multi = resolveController();
        MTEHatchInputBus hatch = multi.mInputBusses.get(index);
        if (hatch == null) {
            throw error(
                "inputBus[" + index + "] at " + absPos + " is null — hatch list may have been cleared by a re-form");
        }
        if (hatch instanceof MTEHatchCraftingInputME) {
            throw error(
                "inputBus[" + index
                    + "] at "
                    + absPos
                    + " is an ME crafting bus — inserting into it is not supported; use inputs() to iterate non-ME input buses");
        }
        return new Bus(hatch.getBaseMetaTileEntity(), "inputBus[" + index + "] at " + absPos, helper.recorder());
    }

    /**
     * Output bus by index in {@link MTEMultiBlockBase#mOutputBusses}.
     *
     * @throws IndexOutOfBoundsException if {@code index} is not in range
     * @apiNote Steam multiblocks ({@code MTESteamMultiBlockBase}) do not populate {@code mOutputBusses}; this
     *          method will throw {@link IndexOutOfBoundsException} on them. Steam multis are not supported in v0.1.
     */
    public Bus outputBus(int index) {
        MTEMultiBlockBase multi = resolveController();
        MTEHatchOutputBus hatch = multi.mOutputBusses.get(index);
        if (hatch == null) {
            throw error(
                "outputBus[" + index + "] at " + absPos + " is null — hatch list may have been cleared by a re-form");
        }
        return new Bus(hatch.getBaseMetaTileEntity(), "outputBus[" + index + "] at " + absPos, helper.recorder());
    }

    /**
     * All input buses from {@link MTEMultiBlockBase#mInputBusses}, skipping invalid tiles and
     * {@link MTEHatchCraftingInputME}.
     *
     * @apiNote Does not include {@code mDualInputHatches} (ME crafting buffers) or steam input buses
     *          ({@code mSteamInputs}). ME buffers and steam multis are not supported in v0.1.
     */
    public BusGroup inputs() {
        MTEMultiBlockBase multi = resolveController();
        BusGroup group = new BusGroup("inputs() at " + absPos, absPos);
        for (MTEHatchInputBus hatch : multi.mInputBusses) {
            if (hatch == null || !hatch.isValid()) continue;
            if (hatch instanceof MTEHatchCraftingInputME) continue;
            group.add(new Bus(hatch.getBaseMetaTileEntity(), "inputBus at " + absPos, helper.recorder()));
        }
        return group;
    }

    /**
     * All output buses from {@link MTEMultiBlockBase#mOutputBusses} that are valid.
     *
     * @apiNote Does not include steam output buses ({@code mSteamOutputs}). Steam multis are not supported in v0.1.
     */
    public BusGroup outputs() {
        MTEMultiBlockBase multi = resolveController();
        BusGroup group = new BusGroup("outputs() at " + absPos, absPos);
        for (MTEHatchOutputBus hatch : multi.mOutputBusses) {
            if (hatch == null || !hatch.isValid()) continue;
            group.add(new Bus(hatch.getBaseMetaTileEntity(), "outputBus at " + absPos, helper.recorder()));
        }
        return group;
    }

    /**
     * Energy hatch by index in {@link MTEMultiBlockBase#mEnergyHatches}. Controllers that route power only through
     * other hatch lists need {@link GTNHGameTestHelper#supplyEU} at known coordinates instead.
     *
     * @throws IndexOutOfBoundsException if {@code index} is not in range
     * @apiNote Indexes {@code mEnergyHatches} only (standard GT energy). TecTech multis route power through
     *          {@code eEnergyMulti}, GT++ through {@code mAllEnergyHatches}, and exotic/dynamo hatches go into
     *          {@code mExoticEnergyHatches} — none of those are covered here. For exotic-energy multiblocks use
     *          {@link GTNHGameTestHelper#supplyEU} at a known coordinate instead.
     */
    public Hatch energyHatch(int index) {
        MTEMultiBlockBase multi = resolveController();
        MTEHatchEnergy hatch = multi.mEnergyHatches.get(index);
        if (hatch == null) {
            throw error(
                "energyHatch[" + index + "] at " + absPos + " is null — hatch list may have been cleared by a re-form");
        }
        return new Hatch(hatch.getBaseMetaTileEntity(), "energyHatch[" + index + "] at " + absPos, helper);
    }

    /**
     * Fluid input hatch by index in {@link MTEMultiBlockBase#mInputHatches}. Supports
     * {@link gregtech.api.metatileentity.implementations.MTEHatchMultiInput} (multi-slot hatches).
     *
     * @throws IndexOutOfBoundsException if {@code index} is not in range
     * @throws GameTestAssertException   if the resolved hatch is an {@link MTEHatchInputME} (virtual ME fluid
     *                                   hatch) or {@link MTEHatchInputDebug}; neither is fillable in the normal
     *                                   sense. ME fluid hatches are not supported in v0.1.
     */
    public Hatch inputHatch(int index) {
        MTEMultiBlockBase multi = resolveController();
        MTEHatchInput hatch = multi.mInputHatches.get(index);
        if (hatch == null) {
            throw error(
                "inputHatch[" + index + "] at " + absPos + " is null — hatch list may have been cleared by a re-form");
        }
        if (hatch instanceof MTEHatchInputME) {
            throw error(
                "inputHatch[" + index
                    + "] at "
                    + absPos
                    + " is an ME fluid hatch — filling it is not supported in v0.1");
        }
        if (hatch instanceof MTEHatchInputDebug) {
            throw error("inputHatch[" + index + "] at " + absPos + " is a debug hatch — it is not fillable");
        }
        return new Hatch(
            hatch.getBaseMetaTileEntity(),
            "inputHatch[" + index + "] at " + absPos,
            null,
            helper.recorder());
    }

    /**
     * Fluid output hatch by index in the multiblock's canonical output-hatch ordering.
     * For most multiblocks this is {@link MTEMultiBlockBase#mOutputHatches}; the Distillation Tower
     * routes through its per-layer list instead (the {@code i}-th call returns a hatch on layer {@code i},
     * matching recipe output slot order). Detection happens in the adapter.
     *
     * @throws GameTestAssertException if {@code index} is out of range or the hatch slot is empty
     */
    public Hatch outputHatch(int index) {
        MTEMultiBlockBase multi = resolveController();
        IGregTechTileEntity te = helper.adapter()
            .getOutputHatchTE(multi, index);
        if (te == null) {
            throw error(
                "outputHatch[" + index
                    + "] at "
                    + absPos
                    + " is null or out of range — hatch list may have been cleared by a re-form");
        }
        return new Hatch(te, "outputHatch[" + index + "] at " + absPos, null, helper.recorder());
    }

    /**
     * {@link MTEMultiBlockBase#enableWorking()} then time-warps until the machine starts processing
     * and becomes idle again, with a default tick bound.
     */
    public void runRecipe() {
        runRecipe(DEFAULT_RUN_TICKS);
    }

    /**
     * {@link MTEMultiBlockBase#enableWorking()} then time-warps until the machine starts processing
     * and becomes idle again, or until {@code maxTicks} simulated ticks have elapsed.
     *
     * <p>
     * Uses a two-phase stop condition: first waits for the machine to become active (recipe found
     * and started), then waits for it to return to idle (recipe completed). This avoids the false-idle
     * problem where the machine hasn't yet picked up a recipe on the first tick.
     *
     * @throws GameTestAssertException if the machine never starts processing within {@code maxTicks},
     *                                 or if it is still active at timeout
     */
    public void runRecipe(int maxTicks) {
        MTEMultiBlockBase multi = resolveController();
        multi.enableWorking();
        boolean[] sawActive = { false };
        TestPos abs = absPos;
        int simulated = TimeWarpHandler.fastForward(
            world,
            helper.originX(),
            helper.originY(),
            helper.originZ(),
            helper.originX() + helper.warpRange(),
            helper.originY() + helper.warpRange(),
            helper.originZ() + helper.warpRange(),
            maxTicks,
            helper.dynamo(),
            () -> {
                TileEntity te = world.getTileEntity(abs.x(), abs.y(), abs.z());
                if (!(te instanceof IGregTechTileEntity igte)) return true;
                if (helper.adapter()
                    .isActive(igte.getMetaTileEntity())) {
                    sawActive[0] = true;
                    return false;
                }
                return sawActive[0];
            },
            helper.recorder(),
            helper.adapter(),
            java.util.Collections.singletonList(abs));

        if (!sawActive[0]) {
            throw error(
                "Machine at " + absPos
                    + " never started processing within "
                    + simulated
                    + " ticks (maxTicks="
                    + maxTicks
                    + "). Check: recipe inputs present? energy supplied? maintenance fixed?");
        }
    }

    /** Fails if the controller block is no longer a GregTech tile entity. */
    public void assertNoExplosion() {
        TileEntity te = world.getTileEntity(absPos.x(), absPos.y(), absPos.z());
        if (!(te instanceof IGregTechTileEntity)) {
            throw error("Machine at " + absPos + " has exploded (GT TE no longer present)");
        }
    }

    /** Current progress ticks for the active recipe, or zero when idle. */
    public int progress() {
        return helper.adapter()
            .getProgressTime(resolveController());
    }

    /** Whether the controller is in the middle of a recipe cycle. */
    public boolean isProcessing() {
        return helper.adapter()
            .isActive(resolveController());
    }

    /** @return Current cleanness of this cleanroom. Max at 10,000 */
    public int getEfficiency() {
        return helper.adapter()
            .getEfficiency(resolveController());
    }

    RecipeMap<?> resolveRecipeMap() {
        MTEMultiBlockBase ctrl = resolveController();
        RecipeMap<?> map = ctrl.getRecipeMap();
        if (map == null) {
            throw new GameTestAssertException(
                "Controller at " + absPos
                    + " does not expose a RecipeMap; withTestRecipe is unsupported for this multi in v0.1",
                absPos);
        }
        return map;
    }

    WorldServer worldServer() {
        return world;
    }

    TestPos controllerAbsPos() {
        return absPos;
    }

    private MTEMultiBlockBase resolveController() {
        TileEntity te = world.getTileEntity(absPos.x(), absPos.y(), absPos.z());
        if (!(te instanceof IGregTechTileEntity igte)) {
            throw error("No GT tile entity at controller position " + absPos);
        }
        if (!(igte.getMetaTileEntity() instanceof MTEMultiBlockBase multi)) {
            throw error(
                "TE at " + absPos
                    + " is not an MTEMultiBlockBase (found: "
                    + igte.getMetaTileEntity()
                        .getClass()
                        .getSimpleName()
                    + ")");
        }
        return multi;
    }

    private TestPos relPos() {
        return helper.absoluteToRelative(absPos);
    }

    private GameTestAssertException error(String message) {
        return new GameTestAssertException(message, absPos);
    }
}
