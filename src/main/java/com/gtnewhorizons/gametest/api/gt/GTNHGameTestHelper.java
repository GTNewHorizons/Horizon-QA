package com.gtnewhorizons.gametest.api.gt;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizons.gametest.api.GameTestAssertException;
import com.gtnewhorizons.gametest.api.GameTestHelper;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;
import com.gtnewhorizons.gametest.api.gt.adapter.GT5UnofficialAdapter;
import com.gtnewhorizons.gametest.api.gt.adapter.GTAdapter;

import gregtech.api.interfaces.IConfigurationCircuitSupport;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtility;

/*
 * ─────────────────────────────────────────────────────────────────────────────
 * PHASE 3 DESIGN NOTE — Hatch/bus resolution in MTEMultiBlockBase subclasses
 * (findings from adapter work, May 2026)
 * ─────────────────────────────────────────────────────────────────────────────
 * HOW HATCH/BUS DISCOVERY WORKS
 * ──────────────────────────────
 * Entry point: MTEMultiBlockBase.checkStructure(boolean, IGregTechTileEntity).
 * On the server, whenever mStructureChanged || forceReset:
 * 1. clearHatches() empties the typed hatch lists and calls setMufflers(false).
 * 2. checkMachine(te, stack, structureErrors) runs; subclasses walk structure
 * blocks via StructureLib element checks, which fire IGTHatchAdder callbacks
 * (e.g. addInputBusToMachineList, addEnergyInputToMachineList, …).
 * 3. mMachine is set from whether structureErrors is still empty.
 * 4. onStructureCheckFinished() may run validateStructure and flip mMachine.
 * TYPED HATCH LISTS ON MTEMultiBlockBase
 * ─────────────────────────────────────────
 * mInputHatches MTEHatchInput
 * mOutputHatches MTEHatchOutput
 * mInputBusses MTEHatchInputBus
 * mOutputBusses MTEHatchOutputBus
 * mDualInputHatches IDualInputHatch (ME / crafting buffer)
 * mSmartInputHatches ISmartInputHatch (fast-path recipe check)
 * mEnergyHatches MTEHatchEnergy
 * mDynamoHatches MTEHatchDynamo (≤4 A only; higher goes to exotic)
 * mMaintenanceHatches MTEHatchMaintenance
 * mMufflerHatches MTEHatchMuffler
 * mExoticEnergyHatches List<MTEHatch> (multi-amp / TecTech exotic inputs;
 * shared by both ExoticEnergy and
 * MultiAmpEnergy HatchElements)
 * mExoticDynamoHatches List<MTEHatch> (TecTech MTEHatchDynamoMulti)
 * There is NO getAllHatches() / getAvailableHatches() on the base class.
 * The only ad-hoc aggregation is Iterables.concat(...) inside explodeMultiblock,
 * which omits dual/smart/exotic lists — not safe as a general accessor.
 * KEY GOTCHAS FOR PHASE 2
 * ────────────────────────
 * 1. mExoticEnergyHatches / mExoticDynamoHatches are NOT cleared by the base
 * clearHatches(). Only MTEExtendedPowerMultiBlockBase clears
 * mExoticEnergyHatches; mExoticDynamoHatches is never cleared at the base
 * level. Stale entries survive rescans on machines that do not extend
 * MTEExtendedPowerMultiBlockBase. Any Phase 2 helper that reads these lists
 * must account for this or force a re-check first.
 * 2. No IMultiBlockController interface exists in this codebase. There is no
 * uniform interface to reach hatch lists polymorphically; code must cast to
 * MTEMultiBlockBase (or a subclass). Phase 2 queries against the controller
 * must use MTEMultiBlockBase directly — casting to a more derived type is
 * only safe if you know the concrete subclass.
 * 3. Subclass divergence — hatch lists differ per hierarchy branch:
 * • MTEExtendedPowerMultiBlockBase: adds getExoticAndNormalEnergyHatchList()
 * (merges mExoticEnergyHatches + mEnergyHatches via filterValidMTEs).
 * This is the right accessor for energy on extended-power multis.
 * • GTPPMultiBlockBase (GT++): adds mChargeHatches, mDischargeHatches,
 * mAirIntakes, mTecTechEnergyHatches, mTecTechDynamoHatches,
 * mAllEnergyHatches, mAllDynamoHatches. Air intakes are also inserted into
 * mInputHatches — double-counting risk if Phase 2 iterates both.
 * • MTESteamMultiBlockBase: completely separate steam lists (mSteamInputs,
 * mSteamOutputs, mSteamInputFluids); normal fluid/bus lists are largely
 * unused. fillHatch / assertFluidInHatch work on the tile directly so
 * they are safe, but any "get all input hatches of controller" API would
 * miss steam hatches.
 * • TTMultiblockBase (TecTech): checkMachine is final and wraps the EM
 * pipeline internally; it also calls onStructureCheckFinished() itself,
 * potentially duplicating post-check work when called from checkStructure.
 * Force-triggering checkStructure on a TecTech multi may produce side
 * effects from the double invocation.
 * 4. Smart input hatch asymmetry: addToMachineList only adds to
 * mSmartInputHatches when doFastRecipeCheck() == true, but the targeted
 * addInputHatchToMachineList / addInputBusToMachineList always add. Phase 2
 * should prefer iterating mInputHatches / mInputBusses for correctness.
 * 5. Steam buses are explicitly excluded from the normal bus adder methods
 * (MTEHatchSteamBusInput / MTEHatchSteamBusOutput return false). If Phase 2
 * adds "insert into any input bus on controller" logic it must handle these
 * separately.
 * 6. Dual input (ME / crafting buffer) gate: addInputBusToMachineList only
 * routes IDualInputHatch into mDualInputHatches when the controller returns
 * supportsCraftingMEBuffer() == true. GTPPMultiBlockBase has a separate
 * path that may not honour this gate — cross-check if ME buffer assertions
 * are added for GT++ multis.
 * IMPLICATIONS FOR PHASE 2 SCOPE
 * ────────────────────────────────
 * • "Get hatch by type from controller" helpers (e.g. getEnergyHatches,
 * getInputBuses) are safe only against plain MTEMultiBlockBase or
 * MTEEnhancedMultiBlockBase. For extended-power and GT++/TecTech subclasses,
 * separate accessors or instanceof branches will be required.
 * • Any helper that calls checkStructure() to force a rescan on a TecTech
 * multi should be treated as potentially unsafe until the double-invocation
 * issue is characterised more precisely.
 * • If Phase 2 adds controller-side hatch inventory access (e.g. to set up
 * inputs without knowing hatch positions), the field lists are public on
 * MTEMultiBlockBase and accessible after mMachine == true — but lists must
 * be non-empty, meaning assertMachineFormed must be called first.
 * • Energy supply via supplyEU currently targets the hatch tile directly by
 * position, which sidesteps all of the above. That approach is robust and
 * should be kept as the default; controller-side resolution is an optional
 * convenience layer.
 * ─────────────────────────────────────────────────────────────────────────────
 * PHASE 3 DESIGN NOTE — Machine/Bus/Hatch proxy layer: findings & scope
 * adjustments (audit completed May 2026)
 * ─────────────────────────────────────────────────────────────────────────────
 * The following documents what the adapter work exposed about how hatch and
 * bus resolution actually works across the multiblock class hierarchy, and
 * how Phase 3's Machine/Bus/Hatch/BusGroup types must adapt to it.
 * ═══════════════════════════════════════════════════════════════════
 * 1. THE CLASS HIERARCHY (four layers, not two)
 * ═══════════════════════════════════════════════════════════════════
 * MTEMultiBlockBase ← hatch lists live here
 * └─ MTETooltipMultiBlockBase ← tooltip mixin, no hatch changes
 * └─ MTEEnhancedMultiBlockBase<T> ← StructureLib alignment; overrides
 * │ clearHatches() only to reset
 * │ structureStatus
 * │ └─ MTEExtendedPowerMultiBlockBase<T>
 * │ ← overrides clearHatches() to also clear mExoticEnergyHatches
 * │ ← getExoticAndNormalEnergyHatchList() merges exotic + normal
 * │ ← drainEnergyInput() uses ExoticEnergyInputHelper
 * │ ← overrides getMaxInputVoltage/getAverageInputVoltage/getMaxInputAmps/getMaxInputEu
 * │ to incorporate exotic hatches
 * │
 * └─ TTMultiblockBase (TecTech)
 * ← has its OWN clearHatches_EM() (NOT an override of clearHatches)
 * ← manages eEnergyMulti, eDynamoMulti, eInputData, eOutputData
 * ← overrides addToMachineList to intercept TecTech hatch types
 * ← getExoticAndNormalEnergyHatchList() merges eEnergyMulti + mEnergyHatches
 * GTPPMultiBlockBase (GT++) ← extends MTEExtendedPowerMultiBlockBase
 * ← adds mAllEnergyHatches, mAllDynamoHatches, mChargeHatches,
 * mDischargeHatches, mAirIntakes
 * ← clearHatches() clears all of these plus TecTech lists
 * ← mAirIntakes are ALSO inserted into mInputHatches (double-counting)
 * MTESteamMultiBlockBase (GT++ steam) ← extends MTEEnhancedMultiBlockBase
 * ← completely separate lists: mSteamInputs, mSteamOutputs, mSteamInputFluids
 * ← overrides getStoredInputsForColor / getStoredFluidsForColor / getOutputBusses
 * ← normal mInputBusses/mOutputBusses lists are NOT populated
 * ← clearHatches() clears steam lists separately
 * KEY INSIGHT: Machine.inputBus(int) cannot just read mInputBusses[i] and
 * have it work for steam multis; and energyHatch(int) cannot just read
 * mEnergyHatches[i] and have it work for exotic/TecTech multis.
 * ═══════════════════════════════════════════════════════════════════
 * 2. HOW RECIPE INPUT RESOLUTION ACTUALLY WORKS (doCheckRecipe)
 * ═══════════════════════════════════════════════════════════════════
 * doCheckRecipe() in MTEMultiBlockBase follows this priority:
 * A. Dual input hatches (mDualInputHatches) — iterated first.
 * Each DualInputHatch has multiple inventories; each inventory provides
 * its own items + fluids. If any inventory produces a successful recipe,
 * it wins immediately. This is the ME crafting buffer / pattern path.
 * B. checkRecipeForCustomHatches(lastResult) — extension point for subclasses
 * (used by MTEExoFoundry, MTEMassSolidifier, MTEFluidShaper).
 * These iterate mInputHatches looking for specialised hatch subtypes
 * (e.g. MTEHatchSolidifier) and set their own input items/fluids.
 * C. Standard bus/hatch resolution with hatch color support:
 * - Fluids: getStoredFluidsForColor() walks mInputHatches, handling
 * MTEHatchMultiInput (multi-slot), MTEHatchInputME (deduped by fluid),
 * MTEHatchInputDebug (infinite amounts), and plain hatches.
 * - Items: getStoredInputsForColor() walks mInputBusses (excluding
 * MTEHatchCraftingInputME), reads all inventory slots.
 * ME buses are deduped by ItemId.
 * - When isInputSeparationEnabled(), each bus is checked individually
 * (bus-by-bus isolation). Otherwise all buses are merged.
 * There is NO getStoredOutputs() method. Outputs are pushed into output
 * buses/hatches directly by the processing logic after recipe completion
 * via addOutput / addFluidOutputs using VoidProtectionHelper + the
 * IOutputBus interface (getOutputBusses() wraps mOutputBusses).
 * WHAT THIS MEANS FOR Machine.runRecipe():
 * Machine.runRecipe() should NOT try to replicate doCheckRecipe() logic.
 * It should call the controller's existing checkProcessing() +
 * fastForwardTicks. The Bus.insert() / Hatch.fill() methods populate
 * the hatches *before* the recipe check runs, which is exactly how the
 * real game works.
 * ═══════════════════════════════════════════════════════════════════
 * 3. HATCH LIST STALENESS & clearHatches BUGS
 * ═══════════════════════════════════════════════════════════════════
 * a) mExoticEnergyHatches: cleared ONLY by MTEExtendedPowerMultiBlockBase
 * and a handful of individual machines (PlasmaForge, BlackHoleCompressor,
 * IndustrialElectromagneticSeparator, AdvAssLine, Synchrotron).
 * NOT cleared by base clearHatches(). Machines that extend
 * MTEEnhancedMultiBlockBase directly and accept exotic energy hatches
 * may have stale entries after re-form.
 * b) mExoticDynamoHatches: NEVER cleared anywhere in the entire codebase.
 * Only TecTech's clearHatches_EM() clears eDynamoMulti (a separate list).
 * This is a latent bug but unlikely to affect test scenarios.
 * c) TecTech's clearHatches_EM() is NOT an @Override of clearHatches();
 * it's a separate method called from TecTech's own onPostTick path.
 * When base checkStructure() calls clearHatches(), TecTech hatches
 * (eEnergyMulti, eDynamoMulti, eInputData, eOutputData) are NOT cleared.
 * TecTech handles this by having its own structure-check flow.
 * FOR Machine: re-resolving hatch lists at call time (as spec'd) is the right
 * call. But the resolution must read the *current* list contents and trust
 * that the controller's own lifecycle has kept them accurate. Do NOT call
 * clearHatches() + checkStructure() from Machine methods — it has side
 * effects and is unsafe on TecTech multis.
 * ═══════════════════════════════════════════════════════════════════
 * 4. RECOMMENDED SCOPE ADJUSTMENTS FOR PHASE 3
 * ═══════════════════════════════════════════════════════════════════
 * 4a. Machine.energyHatch(int) — DO NOT index into mEnergyHatches directly.
 * Instead: if the controller is instanceof MTEExtendedPowerMultiBlockBase,
 * call getExoticAndNormalEnergyHatchList() which merges exotic + normal.
 * For TTMultiblockBase, merge mEnergyHatches + eEnergyMulti (the field
 * is protected, so a reflection accessor or a TecTech-specific adapter
 * will be needed). For GTPPMultiBlockBase, use mAllEnergyHatches.
 * v0.1 SIMPLIFICATION: since the test already has supplyEU(pos, ...),
 * and energyHatch(int) is mostly sugar, defer the polymorphic resolver
 * and have energyHatch(int) simply index into mEnergyHatches with a
 * clear @apiNote that it doesn't cover exotic/TecTech hatches. Add an
 * allEnergyHatches() variant later when needed.
 * 4b. Machine.inputBus(int) / Machine.outputBus(int) — SAFE for the
 * standard hierarchy (MTEMultiBlockBase → MTEExtendedPowerMultiBlockBase).
 * UNSAFE for MTESteamMultiBlockBase where mInputBusses is empty and
 * mSteamInputs holds the actual buses. v0.1 should document this
 * limitation; a SteamMachine subclass or instanceof check can be added
 * later. ALSO: mInputBusses may contain MTEHatchCraftingInputME entries
 * which are skipped by getStoredInputs — Bus.insert() must not insert
 * into these; filter on !(bus instanceof MTEHatchCraftingInputME).
 * 4c. Bus.insert(ItemStack...) — should call setInventorySlotContents on the
 * underlying MTEHatchInputBus, same as baseHelper.insertItem does today.
 * NOT the controller's getStoredInputs aggregation.
 * 4d. Bus.assertContains(ItemMatcher) / BusGroup.assertContains(ItemMatcher)
 * — must iterate actual inventory slots of the underlying hatch, not the
 * aggregated getStoredInputs/getStoredOutputs list. For output buses,
 * there is no getStoredOutputs(); directly read output bus inventory.
 * 4e. Hatch (fluid) — current fillHatch / assertFluidInHatch work by
 * targeting the tile entity directly via IFluidHandler, bypassing the
 * controller entirely. This is the correct approach and is robust.
 * Hatch.fill(FluidStack) and Hatch.assertContains(FluidStack) should
 * continue this pattern. Resolving fluid hatches from the controller
 * (mInputHatches / mOutputHatches) for Hatch handles is safe for the
 * standard hierarchy but must handle MTEHatchMultiInput (has multiple
 * fluid slots) and MTEHatchInputME (virtual, no tile-entity fill).
 * v0.1: exclude ME hatches from Hatch resolution; they require
 * AE2-specific setup that's out of scope.
 * 4f. Machine.inputs() / Machine.outputs() (BusGroup) — for v0.1, these
 * should wrap mInputBusses / mOutputBusses respectively. Do NOT try to
 * merge in mDualInputHatches or mSteamInputs. Add a Machine.dualInputs()
 * later if needed for ME buffer testing.
 * 4g. Machine.runRecipe() / Machine.runRecipe(int maxTicks) — should be
 * implemented as: enableWorking() + fastForwardTicks(maxTicks), NOT by
 * calling checkProcessing() directly. The fast-forward loop already
 * triggers onPostTick which invokes the full recipe pipeline. Calling
 * checkProcessing() manually skips startRecipeProcessing/endRecipeProcessing
 * hooks that ME hatches and IRecipeProcessingAwareHatch depend on.
 * 4h. Machine.assertFormed() — current assertMachineFormed calls
 * checkStructure(true) as a fallback. This is safe for the standard
 * hierarchy but may double-invoke on TecTech multis (see §3c). Since
 * TecTech multis are not a v0.1 target, keep the current behavior with
 * a @apiNote warning.
 * 4i. Input separation awareness: when isInputSeparationEnabled() is true,
 * the recipe engine checks buses one-by-one. Bus.insert() puts items
 * into a specific bus, which is already the right granularity. But
 * BusGroup.assertContains() must walk all buses (which it does by spec).
 * No adjustment needed.
 * 4j. Color-coded hatches: doCheckRecipe uses getStoredFluidsForColor /
 * getStoredInputsForColor with Optional<Byte> color filtering. The v0.1
 * Machine facade does not need to expose color-coded access; the
 * per-bus/per-hatch insertion sidesteps the color aggregation entirely.
 * Document that color filtering is not exposed in v0.1.
 * ═══════════════════════════════════════════════════════════════════
 * 5. SUMMARY OF v0.1 SCOPE BOUNDARIES
 * ═══════════════════════════════════════════════════════════════════
 * IN SCOPE (safe to implement now):
 * - Machine wrapping MTEMultiBlockBase (standard + ExtendedPower hierarchy)
 * - Bus wrapping MTEHatchInputBus / MTEHatchOutputBus from mInputBusses / mOutputBusses
 * - Hatch wrapping MTEHatchInput / MTEHatchOutput from mInputHatches / mOutputHatches
 * - energyHatch(int) indexing into mEnergyHatches (standard energy only)
 * - runRecipe via fastForwardTicks (not direct checkProcessing calls)
 * - ItemMatcher for output assertions
 * OUT OF SCOPE (defer to v0.2+):
 * - Steam multiblock support (MTESteamMultiBlockBase) — separate bus types
 * - TecTech multiblock support (TTMultiblockBase) — separate hatch lifecycle
 * - GT++ multiblock specifics (GTPPMultiBlockBase) — mAllEnergyHatches
 * - Exotic energy hatch resolution across hierarchies
 * - ME hatch / dual input hatch manipulation
 * - Color-coded hatch filtering
 * - Custom hatch subtypes (solidifier hatches, air intakes, etc.)
 * ─────────────────────────────────────────────────────────────────────────────
 */

/**
 * GT-specific test helper returned by {@link GameTestHelper#gtnh()}.
 *
 * <p>
 * All {@link TestPos} arguments use <em>test-local (relative)</em> coordinates — the same
 * convention as the int-coordinate overloads on {@link GameTestHelper}. Internally they are
 * converted to world-absolute coordinates via {@link GameTestHelper#absolute}.
 *
 * <p>
 * Time-warping ({@link #fastForwardTicks}/{@link #runUntilMachineIdle}) is fully synchronous:
 * GT tile entities in the test region are force-ticked without advancing global server time, so
 * recipe completion tests finish in milliseconds of wall-clock time.
 */
@Experimental
public class GTNHGameTestHelper {

    private static final GTAdapter GT = new GT5UnofficialAdapter();

    /** Blocks in each axis from the test origin included in the fast-forward region. */
    private static final int DEFAULT_WARP_RANGE = 32;

    private final GameTestHelper base;
    private final WorldServer world;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final VirtualEUDynamo dynamo = new VirtualEUDynamo();

    private int warpRange = DEFAULT_WARP_RANGE;
    private long pollutionBefore;

    public GTNHGameTestHelper(GameTestHelper base, WorldServer world, int originX, int originY, int originZ) {
        this.base = base;
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.pollutionBefore = getPollutionAtOrigin();
    }

    /**
     * Override the default 32-block fast-forward region. Increase for very large multiblocks
     * (e.g. Fusion Reactors) whose hatches extend far from the controller.
     */
    public GTNHGameTestHelper withWarpRange(int blocks) {
        this.warpRange = blocks;
        return this;
    }

    /**
     * Assert that the multiblock controller at {@code relPos} reports a fully formed structure
     * ({@code mMachine == true}). If the flag is {@code false}, {@link MTEMultiBlockBase#checkStructure}
     * is called with {@code forceReset=true} before failing, to handle cases where the
     * structure placer did not trigger a block-update chain.
     */
    public void assertMachineFormed(TestPos relPos) {
        IGregTechTileEntity igte = requireGTTE(relPos);
        IMetaTileEntity mte = igte.getMetaTileEntity();
        if (!(mte instanceof MTEMultiBlockBase multi)) {
            throw error(
                "TE at " + relPos
                    + " is not an MTEMultiBlockBase (found: "
                    + mte.getClass()
                        .getSimpleName()
                    + ")",
                relPos);
        }
        if (!multi.mMachine) {
            multi.checkStructure(true);
        }
        if (!multi.mMachine) {
            throw error(
                "Multiblock at " + relPos
                    + " structure is not formed (mMachine=false). Verify the template is placed correctly.",
                relPos);
        }
        multi.mStartUpCheck = -1;
    }

    /**
     * Fix all maintenance issues on the multiblock at {@code relPos} by calling
     * {@link MTEMultiBlockBase#fixAllIssues()}. Equivalent to using every maintenance
     * tool on the machine, setting all six flags to {@code true}.
     */
    public void fixAllMaintenanceIssues(TestPos relPos) {
        MTEMultiBlockBase multi = requireMultiBlock(relPos);
        multi.fixAllIssues();
        multi.enableWorking();
    }

    /**
     * Assert that the multiblock at {@code relPos} currently has <em>all</em> of the given
     * maintenance issues active (i.e. the corresponding tool flag is {@code false}).
     */
    public void assertMachineHasIssues(TestPos relPos, MaintenanceType... expected) {
        MTEMultiBlockBase multi = requireMultiBlock(relPos);
        for (MaintenanceType type : expected) {
            if (type.isOk(multi)) {
                throw error("Multiblock at " + relPos + " does not have maintenance issue: " + type.name(), relPos);
            }
        }
    }

    /**
     * Force-tick every GT tile entity in the test region for the given number of simulated ticks
     * without advancing global server time. EU supply jobs (from {@link #supplyEU}) are processed
     * once per simulated tick before the GT TE pass.
     */
    public void fastForwardTicks(int ticks) {
        TimeWarpHandler.fastForward(
            world,
            originX,
            originY,
            originZ,
            originX + warpRange,
            originY + warpRange,
            originZ + warpRange,
            ticks,
            dynamo,
            null);
    }

    /**
     * Fast-forward the test region until the machine at {@code relPos} reports {@code isActive()
     * == false}, or until {@code timeoutTicks} simulated ticks have elapsed. Throws
     * {@link GameTestAssertException} if the machine is still active at timeout.
     */
    public void runUntilMachineIdle(TestPos relPos, int timeoutTicks) {
        TestPos abs = base.absolute(relPos.x(), relPos.y(), relPos.z());
        int simulated = TimeWarpHandler.fastForward(
            world,
            originX,
            originY,
            originZ,
            originX + warpRange,
            originY + warpRange,
            originZ + warpRange,
            timeoutTicks,
            dynamo,
            () -> {
                TileEntity te = world.getTileEntity(abs.x(), abs.y(), abs.z());
                return !(te instanceof IGregTechTileEntity igte) || !igte.isActive();
            });

        TileEntity te = world.getTileEntity(abs.x(), abs.y(), abs.z());
        if (te instanceof IGregTechTileEntity igte && igte.isActive()) {
            throw error(
                "Machine at " + relPos
                    + " is still active after "
                    + simulated
                    + " simulated ticks (timeout="
                    + timeoutTicks
                    + ")",
                relPos);
        }
    }

    /**
     * Register a virtual EU supply job. Starting from the next call to
     * {@link #fastForwardTicks}/{@link #runUntilMachineIdle}, the energy hatch at {@code relPos}
     * will receive {@code voltage × amperage} EU added to its buffer per simulated tick for
     * {@code durationTicks} ticks.
     *
     * @param relPos        test-local position of the energy hatch
     * @param voltage       EU per packet (e.g. 1920 for EV)
     * @param amperage      amps per tick
     * @param durationTicks number of simulated ticks to sustain the supply
     */
    public void supplyEU(TestPos relPos, long voltage, long amperage, int durationTicks) {
        TestPos abs = base.absolute(relPos.x(), relPos.y(), relPos.z());
        dynamo.addJob(world, abs.x(), abs.y(), abs.z(), voltage, amperage, durationTicks);
    }

    /**
     * Assert that the GT tile entity at {@code relPos} has at least {@code expectedEU} stored EU.
     */
    public void assertEUStored(TestPos relPos, long expectedEU) {
        IGregTechTileEntity igte = requireGTTE(relPos);
        long stored = igte.getStoredEU();
        if (stored < expectedEU) {
            throw error("Expected >= " + expectedEU + " EU stored at " + relPos + " but found " + stored, relPos);
        }
    }

    /**
     * Assert that the block at {@code relPos} is no longer a GT tile entity — i.e. the machine
     * exploded and was replaced by air or debris.
     */
    public void assertMachineExploded(TestPos relPos) {
        TestPos abs = base.absolute(relPos.x(), relPos.y(), relPos.z());
        TileEntity te = world.getTileEntity(abs.x(), abs.y(), abs.z());
        if (te instanceof IGregTechTileEntity) {
            throw error("Machine at " + relPos + " did not explode (GT TE still present)", relPos);
        }
    }

    /**
     * Fill the fluid hatch at {@code relPos} with {@code amount} mB of the named fluid.
     *
     * <p>
     * For GT tile entities the fill is applied directly on the {@link IMetaTileEntity} to
     * bypass the {@code mTickTimer > 5} guard in {@code BaseMetaTileEntity}, which would return
     * 0 when called before the hatch has been ticked (e.g. during test setup).
     *
     * @param relPos    test-local position of the fluid hatch
     * @param fluidName Forge registry fluid name (e.g. {@code "nitrogen"})
     * @param amount    mB to fill
     */
    public void fillHatch(TestPos relPos, String fluidName, int amount) {
        FluidStack fluid = FluidRegistry.getFluidStack(fluidName, amount);
        if (fluid == null) {
            throw new IllegalArgumentException("Unknown fluid registry name: " + fluidName);
        }
        fillHatch(relPos, fluid);
    }

    /**
     * Fill the fluid hatch at {@code relPos} with the given {@link FluidStack}.
     *
     * <p>
     * For GT tile entities the fill is applied directly on the {@link IMetaTileEntity} to
     * bypass the {@code mTickTimer > 5} guard in {@code BaseMetaTileEntity}.
     */
    public void fillHatch(TestPos relPos, FluidStack fluidStack) {
        TestPos abs = base.absolute(relPos.x(), relPos.y(), relPos.z());
        TileEntity te = world.getTileEntity(abs.x(), abs.y(), abs.z());

        IFluidHandler handler;
        if (te instanceof IGregTechTileEntity igte) {
            handler = igte.getMetaTileEntity();
        } else if (te instanceof IFluidHandler fh) {
            handler = fh;
        } else {
            throw error(
                "No IFluidHandler at " + relPos
                    + " (found: "
                    + (te != null ? te.getClass()
                        .getSimpleName() : "null")
                    + ")",
                relPos);
        }

        int filled = handler.fill(ForgeDirection.UNKNOWN, fluidStack, true);
        if (filled < fluidStack.amount) {
            throw error(
                "Could not fill " + fluidStack.amount
                    + " mB of '"
                    + fluidStack.getLocalizedName()
                    + "' into hatch at "
                    + relPos
                    + "; only "
                    + filled
                    + " mB accepted",
                relPos);
        }
    }

    /**
     * Assert that the fluid hatch at {@code relPos} contains at least {@code amount} mB of the
     * named fluid.
     *
     * <p>
     * For GT tile entities the drain-peek is applied directly on the {@link IMetaTileEntity}
     * to bypass the {@code mTickTimer > 5} guard in {@code BaseMetaTileEntity}.
     */
    public void assertFluidInHatch(TestPos relPos, String fluidName, int amount) {
        TestPos abs = base.absolute(relPos.x(), relPos.y(), relPos.z());
        TileEntity te = world.getTileEntity(abs.x(), abs.y(), abs.z());
        FluidStack expected = FluidRegistry.getFluidStack(fluidName, amount);
        if (expected == null) {
            throw new IllegalArgumentException("Unknown fluid registry name: " + fluidName);
        }
        IFluidHandler handler;
        if (te instanceof IGregTechTileEntity igte) {
            handler = igte.getMetaTileEntity();
        } else if (te instanceof IFluidHandler fh) {
            handler = fh;
        } else {
            throw error(
                "No IFluidHandler at " + relPos
                    + " (found: "
                    + (te != null ? te.getClass()
                        .getSimpleName() : "null")
                    + ")",
                relPos);
        }
        FluidStack drained = handler.drain(ForgeDirection.UNKNOWN, expected.copy(), false);
        if (drained == null || drained.getFluidID() != expected.getFluidID() || drained.amount < amount) {
            String actual = drained != null ? drained.amount + " mB " + drained.getLocalizedName() : "<empty>";
            throw error(
                "Expected " + amount + " mB of '" + fluidName + "' in hatch at " + relPos + " but found " + actual,
                relPos);
        }
    }

    /**
     * Configure the programmed circuit slot of the input bus at {@code relPos} to {@code config}
     * (1–24). The circuit is written directly into {@link IConfigurationCircuitSupport#getCircuitSlot()}
     * on the {@link IMetaTileEntity}, bypassing normal inventory insertion which explicitly skips
     * that slot ({@code isValidSlot} returns {@code false} for it).
     *
     * @throws IllegalArgumentException if {@code config} is out of range or the item is unavailable
     * @throws GameTestAssertException  if the tile at {@code relPos} is not a GT input bus
     */
    public void insertProgrammedCircuit(TestPos relPos, int config) {
        ItemStack circuit = GTUtility.getIntegratedCircuit(config);
        if (circuit == null) {
            throw new IllegalArgumentException("GTUtility.getIntegratedCircuit returned null for config " + config);
        }
        IGregTechTileEntity igte = requireGTTE(relPos);
        IMetaTileEntity mte = igte.getMetaTileEntity();
        if (!(mte instanceof IConfigurationCircuitSupport circuitSupport)) {
            throw error(
                "TE at " + relPos
                    + " does not support configuration circuits (found: "
                    + mte.getClass()
                        .getSimpleName()
                    + ")",
                relPos);
        }
        if (!circuitSupport.allowSelectCircuit()) {
            throw error("TE at " + relPos + " has circuit support disabled", relPos);
        }
        mte.setInventorySlotContents(circuitSupport.getCircuitSlot(), circuit);
    }

    /**
     * Assert that the output bus / inventory at {@code relPos} contains at least {@code count}
     * items of {@code registryName} at the given metadata value.
     *
     * @param relPos       test-local position of the output bus
     * @param registryName Forge registry name, e.g. {@code "gregtech:gt.metaitem.01"}
     * @param meta         item damage/meta value
     * @param count        minimum stack size to find
     */
    public void assertItemInBus(TestPos relPos, String registryName, int meta, int count) {
        Item item = (Item) Item.itemRegistry.getObject(registryName);
        if (item == null) {
            throw new IllegalArgumentException("Unknown item registry name: " + registryName);
        }
        ItemStack expected = new ItemStack(item, count, meta);
        base.assertInventoryContains(relPos.x(), relPos.y(), relPos.z(), expected);
    }

    public void assertItemInBus(TestPos relPos, ItemStack itemStack) {
        base.assertInventoryContains(relPos.x(), relPos.y(), relPos.z(), itemStack);
    }

    /**
     * Assert that at least {@code expectedPollution} units of pollution were emitted in the
     * origin chunk since this helper was created (i.e. since {@link GameTestHelper#gtnh()} was
     * first called).
     */
    public void assertPollutionEmitted(long expectedPollution) {
        long emitted = getPollutionAtOrigin() - pollutionBefore;
        if (emitted < expectedPollution) {
            throw new GameTestAssertException(
                "Expected >= " + expectedPollution + " pollution emitted but measured " + emitted + " (origin chunk)",
                originX,
                originY,
                originZ);
        }
    }

    /**
     * Assert that the cleanroom controller at {@code relPos} has an efficiency of at least
     * {@code expectedEfficiency} (0–10000, representing 0–100.00 %).
     */
    public void assertCleanroomStatus(TestPos relPos, int expectedEfficiency) {
        IGregTechTileEntity igte = requireGTTE(relPos);
        IMetaTileEntity mte = igte.getMetaTileEntity();
        int efficiency;
        try {
            efficiency = GT.getEfficiency(mte);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw error(
                "TE at " + relPos
                    + " does not expose mEfficiency via GTAdapter — is it really a cleanroom? ("
                    + mte.getClass()
                        .getName()
                    + "): "
                    + e.getMessage(),
                relPos);
        }
        if (efficiency < expectedEfficiency) {
            throw error(
                "Cleanroom at " + relPos + " has efficiency " + efficiency + " but expected >= " + expectedEfficiency,
                relPos);
        }
    }

    /**
     * Controller at {@code relPos} (test-local coordinates). {@link Multiblock} reads hatch lists from the live tile each
     * time.
     */
    public Multiblock multiblock(TestPos relPos) {
        TestPos abs = base.absolute(relPos.x(), relPos.y(), relPos.z());
        return new Multiblock(this, world, abs);
    }

    /** Test-local coordinates from a world-absolute {@link TestPos}. */
    TestPos absoluteToRelative(TestPos abs) {
        return new TestPos(abs.x() - originX, abs.y() - originY, abs.z() - originZ);
    }

    private IGregTechTileEntity requireGTTE(TestPos relPos) {
        TestPos abs = base.absolute(relPos.x(), relPos.y(), relPos.z());
        TileEntity te = world.getTileEntity(abs.x(), abs.y(), abs.z());
        if (!(te instanceof IGregTechTileEntity igte)) {
            throw error(
                "Expected an IGregTechTileEntity at " + relPos
                    + " but found: "
                    + (te != null ? te.getClass()
                        .getSimpleName() : "null"),
                relPos);
        }
        return igte;
    }

    private MTEMultiBlockBase requireMultiBlock(TestPos relPos) {
        IGregTechTileEntity igte = requireGTTE(relPos);
        IMetaTileEntity mte = igte.getMetaTileEntity();
        if (!(mte instanceof MTEMultiBlockBase multi)) {
            throw error(
                "TE at " + relPos
                    + " is not an MTEMultiBlockBase (found: "
                    + mte.getClass()
                        .getSimpleName()
                    + ")",
                relPos);
        }
        return multi;
    }

    private GameTestAssertException error(String message, TestPos relPos) {
        TestPos abs = base.absolute(relPos.x(), relPos.y(), relPos.z());
        return new GameTestAssertException(message, abs);
    }

    private long getPollutionAtOrigin() {
        return GT.getPollution(world.getChunkFromBlockCoords(originX, originZ));
    }
}
