package com.gtnewhorizons.horizonqa.api.gt;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizons.horizonqa.api.GameTestAssertException;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;
import com.gtnewhorizons.horizonqa.api.event.CleanroomEfficiencyChanged;
import com.gtnewhorizons.horizonqa.api.event.EUSupplyJobRegistered;
import com.gtnewhorizons.horizonqa.api.event.HatchFilled;
import com.gtnewhorizons.horizonqa.api.event.MachineExploded;
import com.gtnewhorizons.horizonqa.api.event.PollutionEmitted;
import com.gtnewhorizons.horizonqa.api.event.ProgrammedCircuitSet;
import com.gtnewhorizons.horizonqa.api.event.state.ExplodedCause;
import com.gtnewhorizons.horizonqa.api.gt.adapter.GT5UnofficialAdapter;
import com.gtnewhorizons.horizonqa.api.gt.adapter.GTAdapter;
import com.gtnewhorizons.horizonqa.internal.TestEventRecorder;

import gregtech.api.interfaces.IConfigurationCircuitSupport;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;

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
    private final TestEventRecorder recorder;
    private final VirtualEUDynamo dynamo;
    private final long pollutionBefore;

    private int warpRange = DEFAULT_WARP_RANGE;

    public GTNHGameTestHelper(GameTestHelper base, WorldServer world, int originX, int originY, int originZ) {
        this.base = base;
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.recorder = (TestEventRecorder) base.getRecorder();
        this.dynamo = new VirtualEUDynamo(recorder);
        this.pollutionBefore = getPollutionAtOrigin();
    }

    TestEventRecorder recorder() {
        return recorder;
    }

    /** Adapter used to read GT-internal state through one quarantined seam. */
    public GTAdapter adapter() {
        return GT;
    }

    int originX() {
        return originX;
    }

    int originY() {
        return originY;
    }

    int originZ() {
        return originZ;
    }

    int warpRange() {
        return warpRange;
    }

    VirtualEUDynamo dynamo() {
        return dynamo;
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
     *
     * @see Multiblock#assertFormed()
     */
    public void assertMachineFormed(TestPos relPos) {
        multiblock(relPos).assertFormed();
    }

    /**
     * Fix all maintenance issues on the multiblock at {@code relPos} by calling
     * {@link MTEMultiBlockBase#fixAllIssues()}. Equivalent to using every maintenance
     * tool on the machine, setting all six flags to {@code true}.
     *
     * @see Multiblock#fixMaintenance()
     */
    public void fixAllMaintenanceIssues(TestPos relPos) {
        multiblock(relPos).fixMaintenance();
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
        fastForwardTicks(ticks, java.util.Collections.emptyList());
    }

    /**
     * Same as {@link #fastForwardTicks(int)} but registers {@code watchedControllersAbs} (world-absolute positions)
     * with the warp differ so per-tick recipe / formation / maintenance / explosion transitions are emitted as
     * {@link com.gtnewhorizons.horizonqa.api.event.TestEvent}s into the per-test recorder.
     */
    public void fastForwardTicks(int ticks, java.util.List<TestPos> watchedControllersAbs) {
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
            null,
            recorder,
            GT,
            watchedControllersAbs);
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
            },
            recorder,
            GT,
            java.util.Collections.singletonList(abs));

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
        recorder.record(
            () -> new EUSupplyJobRegistered(
                recorder.clock()
                    .tick(),
                abs,
                voltage,
                amperage,
                durationTicks));
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
        recorder.record(
            () -> new MachineExploded(
                recorder.clock()
                    .tick(),
                abs,
                ExplodedCause.UNKNOWN));
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
        final int finalFilled = filled;
        final FluidStack fs = fluidStack;
        recorder.record(
            () -> new HatchFilled(
                recorder.clock()
                    .tick(),
                abs,
                FluidRegistry.getFluidName(fs),
                fs.amount,
                finalFilled));
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
        TestPos absC = base.absolute(relPos.x(), relPos.y(), relPos.z());
        recorder.record(
            () -> new ProgrammedCircuitSet(
                recorder.clock()
                    .tick(),
                absC,
                config));
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
        recorder.record(
            () -> new PollutionEmitted(
                recorder.clock()
                    .tick(),
                new TestPos(originX, originY, originZ),
                emitted,
                emitted));
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
        final int finalEff = efficiency;
        TestPos absE = base.absolute(relPos.x(), relPos.y(), relPos.z());
        recorder.record(
            () -> new CleanroomEfficiencyChanged(
                recorder.clock()
                    .tick(),
                absE,
                finalEff));
    }

    /**
     * Controller at {@code relPos} (test-local coordinates). {@link Multiblock} reads hatch lists from the live tile
     * each
     * time.
     */
    public Multiblock multiblock(TestPos relPos) {
        TestPos abs = base.absolute(relPos.x(), relPos.y(), relPos.z());
        return new Multiblock(this, world, abs);
    }

    /**
     * Inject a synthetic {@link GTRecipe} into the multiblock's recipemap for the rest of the test.
     * The recipe (and its backend caches) is automatically removed when the test ends.
     *
     * @throws GameTestAssertException if the controller does not expose a RecipeMap
     */
    public void withTestRecipe(Multiblock multi, GTRecipe recipe) {
        RecipeMap<?> map = multi.resolveRecipeMap();
        TestRecipeScope scope = new TestRecipeScope(
            map,
            recipe,
            multi.worldServer(),
            multi.controllerAbsPos(),
            recorder);
        base.afterTest(scope::cleanup);
    }

    /**
     * Builds {@code builder} and injects the result into the multiblock's recipemap for the rest of the test.
     * The recipe is automatically removed when the test ends. Fails the test immediately if the builder
     * produces no recipe.
     *
     * @throws GameTestAssertException if the builder produces no recipe or the controller does not expose a RecipeMap
     */
    public void withTestRecipe(Multiblock multi, GTRecipeBuilder builder) {
        GTRecipe recipe = builder.build()
            .orElseThrow(
                () -> new GameTestAssertException(
                    "Recipe builder produced no recipe — verify itemInputs, itemOutputs, duration and eut are set",
                    multi.controllerAbsPos()));
        withTestRecipe(multi, recipe);
    }

    /** Registers a supply job using world-absolute coordinates. Used by {@link Hatch#supply}. */
    void supplyEUAbsolute(int absX, int absY, int absZ, long voltage, long amperage, int durationTicks) {
        dynamo.addJob(world, absX, absY, absZ, voltage, amperage, durationTicks);
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
