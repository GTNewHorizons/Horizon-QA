package com.gtnewhorizons.gametest.api;

import com.gtnewhorizons.gametest.api.annotation.Stable;
import com.gtnewhorizons.gametest.api.gt.GTNHGameTestHelper;
import com.gtnewhorizons.gametest.core.GameTestInstance;
import com.gtnewhorizons.gametest.core.GameTestSequence;
import com.mojang.authlib.GameProfile;
import cpw.mods.fml.common.Loader;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Passed to every {@code @GameTest} method. Provides world interaction, assertions, and the fluent
 * sequence API.
 */
@Stable
@SuppressWarnings("unused")
public class GameTestHelper {

    private final GameTestInstance instance;
    private final WorldServer world;
    private final int originX;
    private final int originY;
    private final int originZ;
    private GTNHGameTestHelper gtnh;

    public GameTestHelper(GameTestInstance instance, WorldServer world, int originX, int originY, int originZ) {
        this.instance = instance;
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    /**
     * Convert test-local block coordinates to an absolute {@link TestPos} in world space.
     */
    public TestPos absolute(int x, int y, int z) {
        return new TestPos(originX + x, originY + y, originZ + z);
    }

    /**
     * Create and attach a new {@link GameTestSequence} to this test. Must be called at most once per
     * test method. Returns the sequence so the caller can chain step methods.
     */
    public GameTestSequence startSequence() {
        GameTestSequence seq = new GameTestSequence(instance);
        instance.setSequence(seq);
        return seq;
    }

    /**
     * Immediately mark this test as passed. Equivalent to {@code startSequence().thenSucceed()} but
     * without the one-tick delay.
     */
    public void succeed() {
        instance.succeed();
    }

    /** Polls {@code predicate} each tick; passes on the first {@code true}. At most once per test. */
    public void succeedWhen(BooleanSupplier predicate) {
        instance.setSucceedWhen(predicate);
    }

    /**
     * Immediately fail this test with {@code message}. Throws {@link GameTestAssertException} so that
     * any enclosing {@code thenExecute} lambda propagates the failure correctly.
     */
    public void fail(String message) {
        throw new GameTestAssertException(message, originX, originY, originZ);
    }

    public WorldServer getWorld() {
        return world;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public int getOriginZ() {
        return originZ;
    }

    /**
     * Assert that the block at test-local position {@code (x, y, z)} is {@code expected}.
     * Optionally checks metadata when {@code meta >= 0}.
     */
    public void assertBlockPresent(Block expected, int x, int y, int z) {
        assertBlockPresent(expected, -1, x, y, z);
    }

    /**
     * Assert that the block at test-local position is {@code expected} with the given metadata.
     * Pass {@code meta < 0} to skip the meta check.
     */
    public void assertBlockPresent(Block expected, int meta, int x, int y, int z) {
        TestPos pos = absolute(x, y, z);
        Block actual = world.getBlock(pos.x(), pos.y(), pos.z());
        if (actual != expected) {
            throw new GameTestAssertException(
                "Expected " + Block.blockRegistry.getNameForObject(expected)
                    + " at ("
                    + x
                    + ","
                    + y
                    + ","
                    + z
                    + ") but found "
                    + Block.blockRegistry.getNameForObject(actual),
                pos);
        }
        if (meta >= 0) {
            int actualMeta = world.getBlockMetadata(pos.x(), pos.y(), pos.z());
            if (actualMeta != meta) {
                throw new GameTestAssertException(
                    "Expected meta " + meta + " at (" + x + "," + y + "," + z + ") but found " + actualMeta,
                    pos);
            }
        }
    }

    /**
     * Assert that the block at test-local position {@code (x, y, z)} is NOT {@code unexpected}.
     */
    public void assertBlockAbsent(Block unexpected, int x, int y, int z) {
        TestPos pos = absolute(x, y, z);
        Block actual = world.getBlock(pos.x(), pos.y(), pos.z());
        if (actual == unexpected) {
            throw new GameTestAssertException(
                "Expected anything except " + Block.blockRegistry
                    .getNameForObject(unexpected) + " at (" + x + "," + y + "," + z + ") but found it",
                pos);
        }
    }

    /**
     * Assert that a TileEntity exists at test-local position {@code (x, y, z)}.
     * Returns the TileEntity for further inspection.
     */
    public TileEntity assertTileEntityPresent(int x, int y, int z) {
        TestPos pos = absolute(x, y, z);
        TileEntity te = world.getTileEntity(pos.x(), pos.y(), pos.z());
        if (te == null) {
            throw new GameTestAssertException(
                "Expected a TileEntity at (" + x + "," + y + "," + z + ") but found none",
                pos);
        }
        return te;
    }

    /**
     * Assert that a TileEntity of a specific type exists at test-local position.
     * Returns the TileEntity cast to the requested type.
     */
    @SuppressWarnings("unchecked")
    public <T extends TileEntity> T assertTileEntityPresent(Class<T> type, int x, int y, int z) {
        TileEntity te = assertTileEntityPresent(x, y, z);
        if (!type.isInstance(te)) {
            TestPos pos = absolute(x, y, z);
            throw new GameTestAssertException(
                "Expected TileEntity of type " + type.getSimpleName()
                    + " at ("
                    + x
                    + ","
                    + y
                    + ","
                    + z
                    + ") but found "
                    + te.getClass()
                        .getSimpleName(),
                pos);
        }
        return (T) te;
    }

    /**
     * Assert that the TileEntity NBT at test-local position contains all keys/values from
     * {@code expectedSubset}. Only the keys present in {@code expectedSubset} are checked.
     */
    public void assertTileNBT(int x, int y, int z, NBTTagCompound expectedSubset) {
        TileEntity te = assertTileEntityPresent(x, y, z);
        NBTTagCompound actual = new NBTTagCompound();
        te.writeToNBT(actual);

        for (String key : expectedSubset.func_150296_c()) {
            if (!actual.hasKey(key)) {
                throw new GameTestAssertException(
                    "TileEntity at (" + x + "," + y + "," + z + ") missing NBT key '" + key + "'",
                    absolute(x, y, z));
            }
            NBTBase expectedTag = expectedSubset.getTag(key);
            NBTBase actualTag = actual.getTag(key);
            if (!expectedTag.equals(actualTag)) {
                throw new GameTestAssertException(
                    "TileEntity at (" + x
                        + ","
                        + y
                        + ","
                        + z
                        + ") NBT key '"
                        + key
                        + "': expected "
                        + expectedTag
                        + " but found "
                        + actualTag,
                    absolute(x, y, z));
            }
        }
    }

    /**
     * Assert a specific value at a dotted NBT path (e.g. {@code "mInventory.0.id"}).
     * Comparison is done via the tag's string representation.
     */
    public void assertTileNBTPath(int x, int y, int z, String path, String expectedValue) {
        NBTTagCompound nbt = getTileNBT(x, y, z);
        String actual = NBTPathAccessor.resolveAsString(nbt, path);
        if (actual == null) {
            throw new GameTestAssertException(
                "TileEntity at (" + x + "," + y + "," + z + ") has no NBT at path '" + path + "'",
                absolute(x, y, z));
        }
        if (!actual.equals(expectedValue)) {
            throw new GameTestAssertException(
                "TileEntity at (" + x
                    + ","
                    + y
                    + ","
                    + z
                    + ") NBT path '"
                    + path
                    + "': expected "
                    + expectedValue
                    + " but found "
                    + actual,
                absolute(x, y, z));
        }
    }

    /**
     * Return a deep copy of the TileEntity's serialized NBT at test-local position.
     */
    public NBTTagCompound getTileNBT(int x, int y, int z) {
        TileEntity te = assertTileEntityPresent(x, y, z);
        NBTTagCompound nbt = new NBTTagCompound();
        te.writeToNBT(nbt);
        return (NBTTagCompound) nbt.copy();
    }

    /**
     * Insert an ItemStack into the inventory at test-local position. Auto-detects
     * {@code ISidedInventory} vs plain {@code IInventory}.
     *
     * @throws GameTestAssertException if no inventory exists or the stack couldn't be fully inserted
     */
    public void insertItem(int x, int y, int z, ItemStack stack) {
        IInventory inv = getInventoryAt(x, y, z);
        int leftover = InventoryHelper.insert(inv, stack);
        if (leftover > 0) {
            throw new GameTestAssertException(
                "Could not fully insert " + stack
                    .getDisplayName() + " at (" + x + "," + y + "," + z + "): " + leftover + " items remaining",
                absolute(x, y, z));
        }
    }

    /**
     * Insert an ItemStack into the inventory at a test-local (relative) TestPos.
     */
    public void insertItem(TestPos pos, ItemStack stack) {
        insertItem(pos.x(), pos.y(), pos.z(), stack);
    }

    /**
     * Assert that the inventory at test-local position contains at least the given stack
     * (item, damage, NBT match; stack size is minimum).
     */
    public void assertInventoryContains(int x, int y, int z, ItemStack expected) {
        IInventory inv = getInventoryAt(x, y, z);
        if (!InventoryHelper.contains(inv, expected)) {
            throw new GameTestAssertException(
                "Inventory at (" + x
                    + ","
                    + y
                    + ","
                    + z
                    + ") does not contain "
                    + expected.stackSize
                    + "x "
                    + expected.getDisplayName(),
                absolute(x, y, z));
        }
    }

    /**
     * Assert that the inventory at test-local (relative) TestPos contains at least the given stack.
     */
    public void assertInventoryContains(TestPos pos, ItemStack expected) {
        assertInventoryContains(pos.x(), pos.y(), pos.z(), expected);
    }

    /**
     * Assert that every slot of the inventory at test-local position is empty.
     */
    public void assertInventoryEmpty(int x, int y, int z) {
        IInventory inv = getInventoryAt(x, y, z);
        if (!InventoryHelper.isEmpty(inv)) {
            throw new GameTestAssertException(
                "Inventory at (" + x + "," + y + "," + z + ") is not empty",
                absolute(x, y, z));
        }
    }

    /**
     * Assert that a specific slot contains the given stack (exact item + damage + NBT + size).
     */
    public void assertSlot(int x, int y, int z, int slot, ItemStack expected) {
        IInventory inv = getInventoryAt(x, y, z);
        ItemStack actual = InventoryHelper.getSlot(inv, slot);
        if (expected == null) {
            if (actual != null && actual.stackSize > 0) {
                throw new GameTestAssertException(
                    "Slot " + slot
                        + " at ("
                        + x
                        + ","
                        + y
                        + ","
                        + z
                        + ") expected empty but found "
                        + actual.getDisplayName(),
                    absolute(x, y, z));
            }
            return;
        }
        if (actual == null || !InventoryHelper.stacksMatch(actual, expected)
            || actual.stackSize != expected.stackSize) {
            String actualStr = actual != null ? actual.stackSize + "x " + actual.getDisplayName() : "<empty>";
            throw new GameTestAssertException(
                "Slot " + slot
                    + " at ("
                    + x
                    + ","
                    + y
                    + ","
                    + z
                    + ") expected "
                    + expected.stackSize
                    + "x "
                    + expected.getDisplayName()
                    + " but found "
                    + actualStr,
                absolute(x, y, z));
        }
    }

    /**
     * Extract up to {@code maxAmount} items matching {@code template} from the inventory.
     * Returns the actual amount extracted.
     */
    public int extractItem(int x, int y, int z, ItemStack template, int maxAmount) {
        IInventory inv = getInventoryAt(x, y, z);
        return InventoryHelper.extract(inv, template, maxAmount);
    }

    private IInventory getInventoryAt(int x, int y, int z) {
        TileEntity te = assertTileEntityPresent(x, y, z);
        if (!(te instanceof IInventory inv)) {
            throw new GameTestAssertException(
                "TileEntity at (" + x
                    + ","
                    + y
                    + ","
                    + z
                    + ") is not an IInventory ("
                    + te.getClass()
                        .getSimpleName()
                    + ")",
                absolute(x, y, z));
        }
        return inv;
    }

    /**
     * Insert a FluidStack into the fluid handler at test-local position.
     *
     * @throws GameTestAssertException if no fluid handler exists or the fluid couldn't be fully inserted
     */
    public void insertFluid(int x, int y, int z, FluidStack fluid) {
        IFluidHandler handler = getFluidHandlerAt(x, y, z);
        int filled = handler.fill(ForgeDirection.UNKNOWN, fluid, true);
        if (filled < fluid.amount) {
            throw new GameTestAssertException(
                "Could not fully insert " + fluid.amount
                    + "mB of "
                    + fluid.getLocalizedName()
                    + " at ("
                    + x
                    + ","
                    + y
                    + ","
                    + z
                    + "): only "
                    + filled
                    + "mB accepted",
                absolute(x, y, z));
        }
    }

    /**
     * Assert that the fluid handler at test-local position contains at least {@code expected.amount}
     * mB of the expected fluid.
     */
    public void assertFluidTank(int x, int y, int z, FluidStack expected) {
        IFluidHandler handler = getFluidHandlerAt(x, y, z);
        FluidStack drained = handler.drain(ForgeDirection.UNKNOWN, expected.copy(), false);
        if (drained == null || drained.amount < expected.amount || drained.getFluidID() != expected.getFluidID()) {
            String actualStr = drained != null ? drained.amount + "mB " + drained.getLocalizedName() : "<empty>";
            throw new GameTestAssertException(
                "Fluid tank at (" + x
                    + ","
                    + y
                    + ","
                    + z
                    + ") expected "
                    + expected.amount
                    + "mB "
                    + expected.getLocalizedName()
                    + " but found "
                    + actualStr,
                absolute(x, y, z));
        }
    }

    /**
     * Assert that the fluid handler at test-local position has no fluid.
     */
    public void assertFluidTankEmpty(int x, int y, int z) {
        IFluidHandler handler = getFluidHandlerAt(x, y, z);
        FluidStack drained = handler.drain(ForgeDirection.UNKNOWN, Integer.MAX_VALUE, false);
        if (drained != null && drained.amount > 0) {
            throw new GameTestAssertException(
                "Fluid tank at (" + x
                    + ","
                    + y
                    + ","
                    + z
                    + ") expected empty but found "
                    + drained.amount
                    + "mB "
                    + drained.getLocalizedName(),
                absolute(x, y, z));
        }
    }

    private IFluidHandler getFluidHandlerAt(int x, int y, int z) {
        TileEntity te = assertTileEntityPresent(x, y, z);
        if (!(te instanceof IFluidHandler handler)) {
            throw new GameTestAssertException(
                "TileEntity at (" + x
                    + ","
                    + y
                    + ","
                    + z
                    + ") is not an IFluidHandler ("
                    + te.getClass()
                        .getSimpleName()
                    + ")",
                absolute(x, y, z));
        }
        return handler;
    }

    /**
     * Place a block at test-local position. Uses flag 3 (notify + send to client).
     */
    public void setBlock(int x, int y, int z, Block block, int meta) {
        TestPos pos = absolute(x, y, z);
        world.setBlock(pos.x(), pos.y(), pos.z(), block, meta, 3);
    }

    public void setBlock(int x, int y, int z, Block block) {
        setBlock(x, y, z, block, 0);
    }

    /**
     * Destroy the block at test-local position (replace with air). Drops are not spawned.
     */
    public void destroyBlock(int x, int y, int z) {
        TestPos pos = absolute(x, y, z);
        world.setBlock(pos.x(), pos.y(), pos.z(), Blocks.air, 0, 3);
    }

    /**
     * Apply an NBT compound to the TileEntity at test-local position. The x/y/z keys in the compound
     * are overwritten with the absolute position so the TE doesn't teleport.
     */
    public void setTile(int x, int y, int z, NBTTagCompound nbt) {
        TestPos pos = absolute(x, y, z);
        TileEntity te = world.getTileEntity(pos.x(), pos.y(), pos.z());
        if (te == null) {
            throw new GameTestAssertException(
                "No TileEntity at (" + x + "," + y + "," + z + ") to apply NBT to",
                absolute(x, y, z));
        }
        NBTTagCompound copy = (NBTTagCompound) nbt.copy();
        copy.setInteger("x", pos.x());
        copy.setInteger("y", pos.y());
        copy.setInteger("z", pos.z());
        te.readFromNBT(copy);
        world.markBlockForUpdate(pos.x(), pos.y(), pos.z());
    }

    /**
     * Place a redstone block at the given test-local position for {@code durationTicks}, then remove it.
     * Uses a delayed sequence event on the test instance.
     */
    public void pulseRedstone(int x, int y, int z, int durationTicks) {
        setBlock(x, y, z, Blocks.redstone_block, 0);
        instance.scheduleDelayed(durationTicks, () -> { destroyBlock(x, y, z); });
    }

    /**
     * Set a redstone signal source (repeater-like) at test-local position by placing a redstone block
     * (strength = 15) or air (strength = 0).
     *
     * @param strength 0 to clear, any positive value places a redstone block (full signal 15)
     */
    public void setRedstoneInput(int x, int y, int z, int strength) {
        if (strength > 0) {
            setBlock(x, y, z, Blocks.redstone_block, 0);
        } else {
            destroyBlock(x, y, z);
        }
    }

    /**
     * Assert that the block at test-local position is receiving at least {@code minPower} redstone power.
     */
    public void assertRedstonePower(int x, int y, int z, int minPower) {
        TestPos pos = absolute(x, y, z);
        int power = world.getStrongestIndirectPower(pos.x(), pos.y(), pos.z());
        if (power < minPower) {
            throw new GameTestAssertException(
                "Expected redstone power >= " + minPower + " at (" + x + "," + y + "," + z + ") but found " + power,
                pos);
        }
    }

    /**
     * Disable random block ticks in this world (e.g. crop growth, leaf decay) for deterministic tests.
     */
    public void disableRandomTicks() {
        world.getGameRules()
            .setOrCreateGameRule("randomTickSpeed", "0");
    }

    /**
     * Fix the world time to a specific value and disable the daylight cycle.
     */
    public void fixWorldTime(long ticks) {
        world.setWorldTime(ticks);
        world.getGameRules()
            .setOrCreateGameRule("doDaylightCycle", "false");
    }

    /**
     * Apply a weather preset and lock it for the duration of the test.
     */
    public void setWeather(Weather weather) {
        weather.applyTo(world);
    }

    /**
     * Spawn a fake player with the given profile name, positioned at the test's origin.
     */
    public FakePlayer spawnFakePlayer(String profileName) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(profileName.getBytes()), profileName);
        FakePlayer player = FakePlayerFactory.get(world, profile);
        player.setPosition(originX + 0.5, originY + 1.0, originZ + 0.5);
        return player;
    }

    /**
     * Simulate a right-click on the block at test-local position using the given player and held item.
     * Returns true if the activation was handled.
     */
    public boolean simulateRightClick(int x, int y, int z, FakePlayer player, ItemStack heldItem) {
        TestPos pos = absolute(x, y, z);
        player.inventory.mainInventory[player.inventory.currentItem] = heldItem;
        Block block = world.getBlock(pos.x(), pos.y(), pos.z());
        int meta = world.getBlockMetadata(pos.x(), pos.y(), pos.z());
        return block.onBlockActivated(world, pos.x(), pos.y(), pos.z(), player, 0, 0.5f, 0.5f, 0.5f);
    }

    /**
     * Simulate a left-click (block punch) at test-local position.
     */
    public void simulateLeftClick(int x, int y, int z, FakePlayer player) {
        TestPos pos = absolute(x, y, z);
        Block block = world.getBlock(pos.x(), pos.y(), pos.z());
        block.onBlockClicked(world, pos.x(), pos.y(), pos.z(), player);
    }

    /**
     * Return the GTNH-specific helper that provides GregTech machine assertions, EU supply,
     * time-warp, and fluid-hatch utilities. The helper is created lazily on first call and
     * reused on subsequent calls within the same test instance.
     *
     * @throws IllegalStateException if GT5-Unofficial (mod ID {@code gregtech}) is not loaded
     */
    public GTNHGameTestHelper gtnh() {
        if (gtnh == null) {
            if (!Loader.isModLoaded("gregtech")) {
                throw new IllegalStateException(
                    "GT5-Unofficial (mod ID 'gregtech') is not loaded. Cannot use GTNHGameTestHelper.");
            }
            gtnh = new GTNHGameTestHelper(this, world, originX, originY, originZ);
        }
        return gtnh;
    }
}
