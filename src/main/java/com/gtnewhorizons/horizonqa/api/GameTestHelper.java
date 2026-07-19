package com.gtnewhorizons.horizonqa.api;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizons.horizonqa.api.annotation.Experimental;
import com.gtnewhorizons.horizonqa.api.event.EventLog;
import com.gtnewhorizons.horizonqa.api.gt.GTNHGameTestHelper;
import com.gtnewhorizons.horizonqa.internal.GameTestInstance;
import com.gtnewhorizons.horizonqa.internal.GameTestSequence;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.Loader;

/**
 * Passed to every {@code @GameTest} method. Provides world interaction, assertions, and the fluent
 * sequence API.
 *
 * <p>
 * Coordinate-based methods use test-local positions and provide parallel overloads for raw
 * coordinates, {@link TestPos}, and structure-label strings. Label overloads resolve through
 * {@link #pos(String)}, including structure rotation, and throw {@link LabelResolutionException} when
 * the fixture has no matching label.
 */
@Experimental
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

    /** Convert a test-local block position to an absolute {@link TestPos} in world space. */
    public TestPos absolute(TestPos pos) {
        return absolute(pos.x(), pos.y(), pos.z());
    }

    /**
     * Resolve a named coordinate label from this test's structure template. Returned coordinates are
     * test-local and include the test's {@code @GameTest(rotation = ...)} transform.
     *
     * @throws LabelResolutionException if this test has no such label
     */
    public TestPos pos(String label) {
        return instance.resolveLabel(label);
    }

    /**
     * Resolve a named coordinate label to world-absolute coordinates. The label is first resolved with
     * {@link #pos(String)}, so test rotation is applied.
     *
     * @throws LabelResolutionException if this test has no such label
     */
    public TestPos absolute(String label) {
        return absolute(pos(label));
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
     * Pass at the end of {@code timeoutTicks} after the final tick's callbacks and sequence actions
     * have run.
     */
    public void succeedAtTimeout() {
        instance.setSucceedAtTimeout();
    }

    /**
     * Run {@code callback} once per test tick until the test ends (pass or fail). Useful for negative
     * assertions that must hold continuously or during a specific sequence window.
     *
     * <p>
     * The returned handle is initially enabled and can temporarily disable or permanently remove the
     * callback. Per-tick callbacks run in registration order during the END phase, before
     * {@code succeedWhen}, END-phase sequence actions, and timeout evaluation. A callback registered
     * while per-tick callbacks are running begins on the next tick.
     *
     * @return a handle that controls this callback registration
     */
    public TickCallbackHandle onEachTick(Runnable callback) {
        return instance.addEachTickCallback(callback);
    }

    /** Register {@code callback} to run once when this test ends, regardless of outcome. */
    public void afterTest(Runnable callback) {
        instance.addCleanup(callback);
    }

    /**
     * Immediately fail this test with {@code message}. Throws {@link GameTestAssertException} so that
     * any enclosing {@code thenExecute} lambda propagates the failure correctly.
     */
    public void fail(String message) {
        throw new GameTestAssertException(message, originX, originY, originZ);
    }

    /** Immediately fail this test with no message. */
    public void fail() {
        throw new GameTestAssertException("Test failed", originX, originY, originZ);
    }

    /** Fail if {@code condition} is {@code false}. */
    public void assertTrue(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message, originX, originY, originZ);
    }

    /** Fail if {@code condition} is {@code false}. */
    public void assertTrue(boolean condition) {
        if (!condition) throw new GameTestAssertException("Expected true but was false", originX, originY, originZ);
    }

    /** Fail if {@code condition} is {@code true}. */
    public void assertFalse(boolean condition, String message) {
        if (condition) throw new GameTestAssertException(message, originX, originY, originZ);
    }

    /** Fail if {@code condition} is {@code true}. */
    public void assertFalse(boolean condition) {
        if (condition) throw new GameTestAssertException("Expected false but was true", originX, originY, originZ);
    }

    /** Fail if {@code actual} is not {@code null}. */
    public void assertNull(Object actual, String message) {
        if (actual != null) throw new GameTestAssertException(message, originX, originY, originZ);
    }

    /** Fail if {@code actual} is not {@code null}. */
    public void assertNull(Object actual) {
        if (actual != null)
            throw new GameTestAssertException("Expected null but was: <" + actual + ">", originX, originY, originZ);
    }

    /** Fail if {@code actual} is {@code null}. */
    public void assertNotNull(Object actual, String message) {
        if (actual == null) throw new GameTestAssertException(message, originX, originY, originZ);
    }

    /** Fail if {@code actual} is {@code null}. */
    public void assertNotNull(Object actual) {
        if (actual == null) throw new GameTestAssertException("Expected non-null value", originX, originY, originZ);
    }

    /** Fail if {@code expected} and {@code actual} are not equal per {@link Objects#equals}. */
    public void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) throw new GameTestAssertException(
            message + ": expected <" + expected + "> but found <" + actual + ">",
            originX,
            originY,
            originZ);
    }

    /** Fail if {@code expected} and {@code actual} are not equal per {@link Objects#equals}. */
    public void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new GameTestAssertException(
            "Expected <" + expected + "> but found <" + actual + ">",
            originX,
            originY,
            originZ);
    }

    /**
     * Fail if {@code expected} and {@code actual} differ by item, damage, stack size, or NBT.
     * The failure message prints all four fields for both stacks. Two {@code null} stacks compare equal.
     */
    public void assertItemEqual(ItemStack expected, ItemStack actual, String message) {
        if (!itemStacksEqual(expected, actual)) throw new GameTestAssertException(
            message + ": expected <" + describeItemStack(expected) + "> but found <" + describeItemStack(actual) + ">",
            originX,
            originY,
            originZ);
    }

    /**
     * Fail if {@code expected} and {@code actual} differ by item, damage, stack size, or NBT.
     * The failure message prints all four fields for both stacks. Two {@code null} stacks compare equal.
     */
    public void assertItemEqual(ItemStack expected, ItemStack actual) {
        assertItemEqual(expected, actual, "Item mismatch");
    }

    private static boolean itemStacksEqual(ItemStack expected, ItemStack actual) {
        if (expected == actual) return true;
        if (expected == null || actual == null) return false;
        return expected.getItem() == actual.getItem() && expected.getItemDamage() == actual.getItemDamage()
            && expected.stackSize == actual.stackSize
            && Objects.equals(expected.getTagCompound(), actual.getTagCompound());
    }

    private static String describeItemStack(ItemStack stack) {
        if (stack == null) return "null";
        String itemId = Item.itemRegistry.getNameForObject(stack.getItem());
        return "item ID=" + (itemId != null ? itemId : "<unregistered>")
            + ", damage="
            + stack.getItemDamage()
            + ", stack size="
            + stack.stackSize
            + ", NBT="
            + stack.getTagCompound();
    }

    /** Fail if {@code expected != actual}. */
    public void assertEquals(long expected, long actual, String message) {
        if (expected != actual) throw new GameTestAssertException(
            message + ": expected <" + expected + "> but found <" + actual + ">",
            originX,
            originY,
            originZ);
    }

    /** Fail if {@code expected != actual}. */
    public void assertEquals(long expected, long actual) {
        if (expected != actual) throw new GameTestAssertException(
            "Expected <" + expected + "> but found <" + actual + ">",
            originX,
            originY,
            originZ);
    }

    /** Fail if {@code |expected - actual| > delta}. */
    public void assertEquals(double expected, double actual, double delta, String message) {
        if (Math.abs(expected - actual) > delta) throw new GameTestAssertException(
            message + ": expected <" + expected + "> but found <" + actual + "> (delta " + delta + ")",
            originX,
            originY,
            originZ);
    }

    /** Fail if {@code |expected - actual| > delta}. */
    public void assertEquals(double expected, double actual, double delta) {
        if (Math.abs(expected - actual) > delta) throw new GameTestAssertException(
            "Expected <" + expected + "> but found <" + actual + "> (delta " + delta + ")",
            originX,
            originY,
            originZ);
    }

    /** Fail if {@code unexpected} and {@code actual} are equal per {@link Objects#equals}. */
    public void assertNotEquals(Object unexpected, Object actual, String message) {
        if (Objects.equals(unexpected, actual)) throw new GameTestAssertException(
            message + ": expected anything except <" + unexpected + ">",
            originX,
            originY,
            originZ);
    }

    /** Fail if {@code unexpected} and {@code actual} are equal per {@link Objects#equals}. */
    public void assertNotEquals(Object unexpected, Object actual) {
        if (Objects.equals(unexpected, actual)) throw new GameTestAssertException(
            "Expected anything except <" + unexpected + ">",
            originX,
            originY,
            originZ);
    }

    /** Fail if {@code unexpected == actual}. */
    public void assertNotEquals(long unexpected, long actual, String message) {
        if (unexpected == actual) throw new GameTestAssertException(
            message + ": expected anything except <" + unexpected + ">",
            originX,
            originY,
            originZ);
    }

    /** Fail if {@code unexpected == actual}. */
    public void assertNotEquals(long unexpected, long actual) {
        if (unexpected == actual) throw new GameTestAssertException(
            "Expected anything except <" + unexpected + ">",
            originX,
            originY,
            originZ);
    }

    /** Fail if {@code |unexpected - actual| <= delta}. */
    public void assertNotEquals(double unexpected, double actual, double delta, String message) {
        if (Math.abs(unexpected - actual) <= delta) throw new GameTestAssertException(
            message + ": expected anything except <" + unexpected + "> (delta " + delta + ")",
            originX,
            originY,
            originZ);
    }

    /** Fail if {@code |unexpected - actual| <= delta}. */
    public void assertNotEquals(double unexpected, double actual, double delta) {
        if (Math.abs(unexpected - actual) <= delta) throw new GameTestAssertException(
            "Expected anything except <" + unexpected + "> (delta " + delta + ")",
            originX,
            originY,
            originZ);
    }

    /** Fail if {@code expected} and {@code actual} are not the same object reference ({@code ==}). */
    public void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) throw new GameTestAssertException(
            message + ": expected same instance <" + expected + "> but found <" + actual + ">",
            originX,
            originY,
            originZ);
    }

    /** Fail if {@code expected} and {@code actual} are not the same object reference ({@code ==}). */
    public void assertSame(Object expected, Object actual) {
        if (expected != actual) throw new GameTestAssertException(
            "Expected same instance <" + expected + "> but found <" + actual + ">",
            originX,
            originY,
            originZ);
    }

    /** Fail if {@code unexpected} and {@code actual} are the same object reference ({@code ==}). */
    public void assertNotSame(Object unexpected, Object actual, String message) {
        if (unexpected == actual) throw new GameTestAssertException(
            message + ": expected different instances but both were <" + actual + ">",
            originX,
            originY,
            originZ);
    }

    /** Fail if {@code unexpected} and {@code actual} are the same object reference ({@code ==}). */
    public void assertNotSame(Object unexpected, Object actual) {
        if (unexpected == actual) throw new GameTestAssertException(
            "Expected different instances but both were <" + actual + ">",
            originX,
            originY,
            originZ);
    }

    /**
     * Fail if {@code actual} is not an instance of {@code type}. Returns {@code actual} cast to {@code T}.
     */
    @SuppressWarnings("unchecked")
    public <T> T assertInstanceOf(Class<T> type, Object actual, String message) {
        if (!type.isInstance(actual)) {
            String actualType = actual == null ? "null"
                : actual.getClass()
                    .getSimpleName();
            throw new GameTestAssertException(
                message + ": expected instance of " + type.getSimpleName() + " but was " + actualType,
                originX,
                originY,
                originZ);
        }
        return (T) actual;
    }

    /**
     * Fail if {@code actual} is not an instance of {@code type}. Returns {@code actual} cast to {@code T}.
     */
    public <T> T assertInstanceOf(Class<T> type, Object actual) {
        return assertInstanceOf(type, actual, "Type assertion failed");
    }

    /**
     * Assert that {@code action} throws an exception of exactly type {@code expectedType}.
     * Returns the thrown exception for further inspection. Fails if nothing is thrown or the
     * wrong type is thrown.
     */
    public <T extends Throwable> T assertThrows(Class<T> expectedType, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expectedType.isInstance(actual)) {
                return expectedType.cast(actual);
            }
            throw new GameTestAssertException(
                message + ": expected "
                    + expectedType.getSimpleName()
                    + " but got "
                    + actual.getClass()
                        .getSimpleName(),
                originX,
                originY,
                originZ);
        }
        throw new GameTestAssertException(
            message + ": expected " + expectedType.getSimpleName() + " to be thrown but nothing was thrown",
            originX,
            originY,
            originZ);
    }

    /**
     * Assert that {@code action} throws an exception of exactly type {@code expectedType}.
     * Returns the thrown exception for further inspection.
     */
    public <T extends Throwable> T assertThrows(Class<T> expectedType, Runnable action) {
        return assertThrows(expectedType, action, "assertThrows failed");
    }

    /**
     * Assert that two iterables contain equal elements in the same order (deep equality via
     * {@link Objects#equals}).
     */
    public void assertIterableEquals(Iterable<?> expected, Iterable<?> actual, String message) {
        Iterator<?> expIt = expected.iterator();
        Iterator<?> actIt = actual.iterator();
        int index = 0;
        while (expIt.hasNext() && actIt.hasNext()) {
            Object exp = expIt.next();
            Object act = actIt.next();
            if (!Objects.equals(exp, act)) throw new GameTestAssertException(
                message + ": element [" + index + "]: expected <" + exp + "> but found <" + act + ">",
                originX,
                originY,
                originZ);
            index++;
        }
        if (expIt.hasNext()) throw new GameTestAssertException(
            message + ": expected iterable has more elements at index " + index,
            originX,
            originY,
            originZ);
        if (actIt.hasNext()) throw new GameTestAssertException(
            message + ": actual iterable has more elements at index " + index,
            originX,
            originY,
            originZ);
    }

    /**
     * Assert that two iterables contain equal elements in the same order.
     */
    public void assertIterableEquals(Iterable<?> expected, Iterable<?> actual) {
        assertIterableEquals(expected, actual, "Iterable mismatch");
    }

    /**
     * Assert that {@code actualLines} matches {@code expectedLines} line by line. Each expected line is
     * first compared verbatim; on mismatch it is tried as a {@link java.util.regex.Pattern regex}.
     * Use {@code ">>"} to skip all remaining actual lines, or {@code ">> N >>"} to skip exactly N lines.
     */
    public void assertLinesMatch(List<String> expectedLines, List<String> actualLines, String message) {
        int ai = 0;
        int ei = 0;
        while (ei < expectedLines.size()) {
            String exp = expectedLines.get(ei);
            if (">>".equals(exp)) {
                ai = actualLines.size();
                ei++;
                continue;
            }
            if (exp.startsWith(">> ") && exp.endsWith(" >>")) {
                String inner = exp.substring(3, exp.length() - 3)
                    .trim();
                try {
                    ai += Integer.parseInt(inner);
                    ei++;
                    continue;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                        "assertLinesMatch: invalid skip directive '>> " + inner + " >>' - not a number",
                        e);
                }
            }
            if (ai >= actualLines.size()) throw new GameTestAssertException(
                message + ": line [" + ei + "] expected <" + exp + "> but actual output ended",
                originX,
                originY,
                originZ);
            String act = actualLines.get(ai);
            if (!exp.equals(act)) {
                boolean matched = false;
                try {
                    matched = act.matches(exp);
                } catch (java.util.regex.PatternSyntaxException e) {
                    throw new GameTestAssertException(
                        message + ": line [" + ei + "]: regex <" + exp + "> is invalid - " + e.getMessage(),
                        originX,
                        originY,
                        originZ);
                }
                if (!matched) throw new GameTestAssertException(
                    message + ": line [" + ei + "]: expected <" + exp + "> but found <" + act + ">",
                    originX,
                    originY,
                    originZ);
            }
            ei++;
            ai++;
        }
        if (ai < actualLines.size()) throw new GameTestAssertException(
            message + ": "
                + (actualLines.size() - ai)
                + " unmatched actual line(s) starting with <"
                + actualLines.get(ai)
                + ">",
            originX,
            originY,
            originZ);
    }

    /**
     * Assert that {@code actualLines} matches {@code expectedLines} — see
     * {@link #assertLinesMatch(List, List, String)}.
     */
    public void assertLinesMatch(List<String> expectedLines, List<String> actualLines) {
        assertLinesMatch(expectedLines, actualLines, "Lines mismatch");
    }

    public WorldServer getWorld() {
        return world;
    }

    /**
     * Event log for this test instance. Events are appended in emit order; filter with
     * {@code snapshot().stream()} to query specific event types.
     */
    public EventLog getRecorder() {
        return instance.getRecorder();
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
     * Assert that the block at test-local position {@code pos} is {@code expected}.
     */
    public void assertBlockPresent(Block expected, TestPos pos) {
        assertBlockPresent(expected, pos.x(), pos.y(), pos.z());
    }

    /** Assert that the block at the named structure position is {@code expected}. */
    public void assertBlockPresent(Block expected, String label) {
        assertBlockPresent(expected, pos(label));
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

    /** Assert that the block at {@code pos} is {@code expected} with the given metadata. */
    public void assertBlockPresent(Block expected, int meta, TestPos pos) {
        assertBlockPresent(expected, meta, pos.x(), pos.y(), pos.z());
    }

    /** Assert that the block at the named structure position is {@code expected} with the given metadata. */
    public void assertBlockPresent(Block expected, int meta, String label) {
        assertBlockPresent(expected, meta, pos(label));
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
     * Assert that the block at test-local position {@code pos} is NOT {@code unexpected}.
     */
    public void assertBlockAbsent(Block unexpected, TestPos pos) {
        assertBlockAbsent(unexpected, pos.x(), pos.y(), pos.z());
    }

    /** Assert that the block at the named structure position is NOT {@code unexpected}. */
    public void assertBlockAbsent(Block unexpected, String label) {
        assertBlockAbsent(unexpected, pos(label));
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

    /** Assert that a TileEntity exists at the test-local position and return it. */
    public TileEntity assertTileEntityPresent(TestPos pos) {
        return assertTileEntityPresent(pos.x(), pos.y(), pos.z());
    }

    /** Assert that a TileEntity exists at the named structure position and return it. */
    public TileEntity assertTileEntityPresent(String label) {
        return assertTileEntityPresent(pos(label));
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

    /** Assert that a TileEntity of {@code type} exists at the test-local position and return it. */
    public <T extends TileEntity> T assertTileEntityPresent(Class<T> type, TestPos pos) {
        return assertTileEntityPresent(type, pos.x(), pos.y(), pos.z());
    }

    /** Assert that a TileEntity of {@code type} exists at the named structure position and return it. */
    public <T extends TileEntity> T assertTileEntityPresent(Class<T> type, String label) {
        return assertTileEntityPresent(type, pos(label));
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

    /** Assert that the TileEntity NBT at the test-local position contains {@code expectedSubset}. */
    public void assertTileNBT(TestPos pos, NBTTagCompound expectedSubset) {
        assertTileNBT(pos.x(), pos.y(), pos.z(), expectedSubset);
    }

    /** Assert that the TileEntity NBT at the named structure position contains {@code expectedSubset}. */
    public void assertTileNBT(String label, NBTTagCompound expectedSubset) {
        assertTileNBT(pos(label), expectedSubset);
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

    /** Assert a value at a dotted NBT path on the TileEntity at the test-local position. */
    public void assertTileNBTPath(TestPos pos, String path, String expectedValue) {
        assertTileNBTPath(pos.x(), pos.y(), pos.z(), path, expectedValue);
    }

    /** Assert a value at a dotted NBT path on the TileEntity at the named structure position. */
    public void assertTileNBTPath(String label, String path, String expectedValue) {
        assertTileNBTPath(pos(label), path, expectedValue);
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

    /** Return a deep copy of the TileEntity's serialized NBT at the test-local position. */
    public NBTTagCompound getTileNBT(TestPos pos) {
        return getTileNBT(pos.x(), pos.y(), pos.z());
    }

    /** Return a deep copy of the TileEntity's serialized NBT at the named structure position. */
    public NBTTagCompound getTileNBT(String label) {
        return getTileNBT(pos(label));
    }

    /**
     * Return live entities of {@code type} whose bounding boxes intersect the single test-local
     * block at {@code (x, y, z)}.
     */
    public <T extends Entity> List<T> getEntities(Class<T> type, int x, int y, int z) {
        return getEntities(type, x, y, z, x, y, z);
    }

    /** Return live entities of {@code type} intersecting the single test-local block at {@code pos}. */
    public <T extends Entity> List<T> getEntities(Class<T> type, TestPos pos) {
        return getEntities(type, pos.x(), pos.y(), pos.z());
    }

    /** Return live entities of {@code type} intersecting the single block at the named structure position. */
    public <T extends Entity> List<T> getEntities(Class<T> type, String label) {
        return getEntities(type, pos(label));
    }

    /**
     * Return live entities of {@code type} whose bounding boxes intersect the inclusive test-local
     * block range.
     */
    @SuppressWarnings("unchecked")
    public <T extends Entity> List<T> getEntities(Class<T> type, int minX, int minY, int minZ, int maxX, int maxY,
        int maxZ) {
        AxisAlignedBB box = localEntityBox(minX, minY, minZ, maxX, maxY, maxZ);
        List<?> raw = world.getEntitiesWithinAABB(type, box);
        List<T> result = new ArrayList<>();
        for (Object object : raw) {
            if (!type.isInstance(object)) continue;
            T entity = (T) object;
            if (!entity.isDead) {
                result.add(entity);
            }
        }
        return result;
    }

    /** Return live entities of {@code type} intersecting the inclusive test-local block range. */
    public <T extends Entity> List<T> getEntities(Class<T> type, TestPos min, TestPos max) {
        return getEntities(type, min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    /** Return live entities of {@code type} intersecting the inclusive range between two structure labels. */
    public <T extends Entity> List<T> getEntities(Class<T> type, String minLabel, String maxLabel) {
        return getEntities(type, pos(minLabel), pos(maxLabel));
    }

    /**
     * Assert that at least one live entity exists in the single test-local block at {@code (x, y, z)}.
     * Returns the first matching entity for further inspection.
     */
    public Entity assertEntityPresent(int x, int y, int z) {
        return assertEntityPresent(Entity.class, x, y, z);
    }

    /** Assert that at least one live entity exists in the single test-local block at {@code pos}. */
    public Entity assertEntityPresent(TestPos pos) {
        return assertEntityPresent(pos.x(), pos.y(), pos.z());
    }

    /** Assert that at least one live entity exists at the named structure position. */
    public Entity assertEntityPresent(String label) {
        return assertEntityPresent(pos(label));
    }

    /**
     * Assert that at least one live entity of {@code type} exists in the single test-local block at
     * {@code (x, y, z)}. Returns the first matching entity for further inspection.
     */
    public <T extends Entity> T assertEntityPresent(Class<T> type, int x, int y, int z) {
        List<T> entities = getEntities(type, x, y, z);
        if (entities.isEmpty()) {
            throw new GameTestAssertException(
                "Expected " + entityTypeName(type) + " at (" + x + "," + y + "," + z + ") but found none",
                absolute(x, y, z));
        }
        return entities.get(0);
    }

    /** Assert that at least one live entity of {@code type} exists in the block at {@code pos}. */
    public <T extends Entity> T assertEntityPresent(Class<T> type, TestPos pos) {
        return assertEntityPresent(type, pos.x(), pos.y(), pos.z());
    }

    /** Assert that at least one live entity of {@code type} exists at the named structure position. */
    public <T extends Entity> T assertEntityPresent(Class<T> type, String label) {
        return assertEntityPresent(type, pos(label));
    }

    /**
     * Assert that no live entity exists in the single test-local block at {@code (x, y, z)}.
     */
    public void assertEntityAbsent(int x, int y, int z) {
        assertEntityAbsent(Entity.class, x, y, z);
    }

    /** Assert that no live entity exists in the single test-local block at {@code pos}. */
    public void assertEntityAbsent(TestPos pos) {
        assertEntityAbsent(pos.x(), pos.y(), pos.z());
    }

    /** Assert that no live entity exists at the named structure position. */
    public void assertEntityAbsent(String label) {
        assertEntityAbsent(pos(label));
    }

    /**
     * Assert that no live entity of {@code type} exists in the single test-local block at
     * {@code (x, y, z)}.
     */
    public void assertEntityAbsent(Class<? extends Entity> type, int x, int y, int z) {
        List<? extends Entity> entities = getEntities(type, x, y, z);
        if (!entities.isEmpty()) {
            String entityType = entityPluralName(type);
            throw new GameTestAssertException(
                "Expected no " + entityType + " at (" + x + "," + y + "," + z + ") but found " + entities.size(),
                absolute(x, y, z));
        }
    }

    /** Assert that no live entity of {@code type} exists in the single test-local block at {@code pos}. */
    public void assertEntityAbsent(Class<? extends Entity> type, TestPos pos) {
        assertEntityAbsent(type, pos.x(), pos.y(), pos.z());
    }

    /** Assert that no live entity of {@code type} exists at the named structure position. */
    public void assertEntityAbsent(Class<? extends Entity> type, String label) {
        assertEntityAbsent(type, pos(label));
    }

    /**
     * Assert that exactly {@code expectedCount} live entities exist in the single test-local block at
     * {@code (x, y, z)}.
     */
    public void assertEntityCount(int expectedCount, int x, int y, int z) {
        assertEntityCount(Entity.class, expectedCount, x, y, z);
    }

    /** Assert the live entity count in the single test-local block at {@code pos}. */
    public void assertEntityCount(int expectedCount, TestPos pos) {
        assertEntityCount(expectedCount, pos.x(), pos.y(), pos.z());
    }

    /** Assert the live entity count at the named structure position. */
    public void assertEntityCount(int expectedCount, String label) {
        assertEntityCount(expectedCount, pos(label));
    }

    /**
     * Assert that exactly {@code expectedCount} live entities of {@code type} exist in the single
     * test-local block at {@code (x, y, z)}.
     */
    public void assertEntityCount(Class<? extends Entity> type, int expectedCount, int x, int y, int z) {
        assertEntityCount(type, expectedCount, x, y, z, x, y, z);
    }

    /** Assert the live entity count of {@code type} in the single test-local block at {@code pos}. */
    public void assertEntityCount(Class<? extends Entity> type, int expectedCount, TestPos pos) {
        assertEntityCount(type, expectedCount, pos.x(), pos.y(), pos.z());
    }

    /** Assert the live entity count of {@code type} at the named structure position. */
    public void assertEntityCount(Class<? extends Entity> type, int expectedCount, String label) {
        assertEntityCount(type, expectedCount, pos(label));
    }

    /**
     * Assert that exactly {@code expectedCount} live entities exist in the inclusive test-local block
     * range.
     */
    public void assertEntityCount(int expectedCount, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        assertEntityCount(Entity.class, expectedCount, minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Assert the live entity count in the inclusive test-local block range. */
    public void assertEntityCount(int expectedCount, TestPos min, TestPos max) {
        assertEntityCount(expectedCount, min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    /** Assert the live entity count in the inclusive range between two structure labels. */
    public void assertEntityCount(int expectedCount, String minLabel, String maxLabel) {
        assertEntityCount(expectedCount, pos(minLabel), pos(maxLabel));
    }

    /**
     * Assert that exactly {@code expectedCount} live entities of {@code type} exist in the inclusive
     * test-local block range.
     */
    public void assertEntityCount(Class<? extends Entity> type, int expectedCount, int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ) {
        int actual = getEntities(type, minX, minY, minZ, maxX, maxY, maxZ).size();
        if (actual != expectedCount) {
            String entityType = entityCountName(type, expectedCount);
            throw new GameTestAssertException(
                "Expected " + expectedCount
                    + " "
                    + entityType
                    + " in range ("
                    + minX
                    + ","
                    + minY
                    + ","
                    + minZ
                    + ")..("
                    + maxX
                    + ","
                    + maxY
                    + ","
                    + maxZ
                    + ") but found "
                    + actual,
                absolute(minX, minY, minZ));
        }
    }

    /** Assert the live entity count of {@code type} in the inclusive test-local block range. */
    public void assertEntityCount(Class<? extends Entity> type, int expectedCount, TestPos min, TestPos max) {
        assertEntityCount(type, expectedCount, min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    /** Assert the live entity count of {@code type} in the inclusive range between two structure labels. */
    public void assertEntityCount(Class<? extends Entity> type, int expectedCount, String minLabel, String maxLabel) {
        assertEntityCount(type, expectedCount, pos(minLabel), pos(maxLabel));
    }

    /**
     * Spawn {@code entity} at the test-local position and return it.
     *
     * @throws GameTestAssertException if the world refuses to spawn the entity
     */
    public <T extends Entity> T spawnEntity(T entity, double x, double y, double z) {
        entity.dimension = world.provider.dimensionId;
        entity.setPosition(originX + x, originY + y, originZ + z);
        if (!world.spawnEntityInWorld(entity)) {
            String entityType = entityTypeName(entity.getClass());
            throw new GameTestAssertException(
                "World refused to spawn " + entityType + " at (" + x + "," + y + "," + z + ")",
                localDoublePos(x, y, z));
        }
        return entity;
    }

    /** Spawn {@code entity} at the test-local block position and return it. */
    public <T extends Entity> T spawnEntity(T entity, TestPos pos) {
        return spawnEntity(entity, pos.x(), pos.y(), pos.z());
    }

    /** Spawn {@code entity} at the named structure position and return it. */
    public <T extends Entity> T spawnEntity(T entity, String label) {
        return spawnEntity(entity, pos(label));
    }

    /**
     * Create an entity by NBT entity id, spawn it at the test-local position, and return it.
     */
    public Entity spawnEntity(String entityId, double x, double y, double z) {
        Entity entity = EntityList.createEntityByName(entityId, world);
        if (entity == null) {
            throw new GameTestAssertException("Unknown entity '" + entityId + "'", localDoublePos(x, y, z));
        }
        return spawnEntity(entity, x, y, z);
    }

    /** Create an entity by NBT entity id and spawn it at the test-local block position. */
    public Entity spawnEntity(String entityId, TestPos pos) {
        return spawnEntity(entityId, pos.x(), pos.y(), pos.z());
    }

    /** Create an entity by NBT entity id and spawn it at the named structure position. */
    public Entity spawnEntity(String entityId, String label) {
        return spawnEntity(entityId, pos(label));
    }

    /**
     * Assert that the entity's NBT contains all keys/values from {@code expectedSubset}. Only the keys
     * present in {@code expectedSubset} are checked.
     */
    public void assertEntityNBT(Entity entity, NBTTagCompound expectedSubset) {
        NBTTagCompound actual = getEntityNBT(entity);
        String entityType = entityTypeName(entity.getClass());
        TestPos pos = entityPos(entity);
        for (String key : expectedSubset.func_150296_c()) {
            if (!actual.hasKey(key)) {
                throw new GameTestAssertException("Entity " + entityType + " missing NBT key '" + key + "'", pos);
            }
            NBTBase expectedTag = expectedSubset.getTag(key);
            NBTBase actualTag = actual.getTag(key);
            if (!expectedTag.equals(actualTag)) {
                throw new GameTestAssertException(
                    "Entity " + entityType
                        + " NBT key '"
                        + key
                        + "': expected "
                        + expectedTag
                        + " but found "
                        + actualTag,
                    pos);
            }
        }
    }

    /**
     * Assert a specific value at a dotted NBT path on an entity (e.g. {@code "Pos.0"}). Comparison is
     * done via the tag's string representation.
     */
    public void assertEntityNBTPath(Entity entity, String path, String expectedValue) {
        NBTTagCompound nbt = getEntityNBT(entity);
        String actual = NBTPathAccessor.resolveAsString(nbt, path);
        String entityType = entityTypeName(entity.getClass());
        TestPos pos = entityPos(entity);
        if (actual == null) {
            throw new GameTestAssertException("Entity " + entityType + " has no NBT at path '" + path + "'", pos);
        }
        if (!actual.equals(expectedValue)) {
            throw new GameTestAssertException(
                "Entity " + entityType + " NBT path '" + path + "': expected " + expectedValue + " but found " + actual,
                pos);
        }
    }

    /**
     * Return a deep copy of an entity's serialized NBT.
     */
    public NBTTagCompound getEntityNBT(Entity entity) {
        NBTTagCompound nbt = new NBTTagCompound();
        if (!entity.writeToNBTOptional(nbt)) {
            entity.writeToNBT(nbt);
        }
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

    /** Insert an ItemStack into the inventory at the named structure position. */
    public void insertItem(String label, ItemStack stack) {
        insertItem(pos(label), stack);
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

    /** Assert that the inventory at the named structure position contains at least {@code expected}. */
    public void assertInventoryContains(String label, ItemStack expected) {
        assertInventoryContains(pos(label), expected);
    }

    /**
     * Count all items matching {@code template} (item, damage, and NBT) in the inventory at test-local position.
     * The template's stack size is ignored.
     */
    public long countItems(int x, int y, int z, ItemStack template) {
        return InventoryHelper.count(getInventoryAt(x, y, z), template);
    }

    /**
     * Count all items matching {@code template} in the inventory at a test-local (relative) TestPos.
     */
    public long countItems(TestPos pos, ItemStack template) {
        return countItems(pos.x(), pos.y(), pos.z(), template);
    }

    /** Count all matching items in the inventory at the named structure position. */
    public long countItems(String label, ItemStack template) {
        return countItems(pos(label), template);
    }

    /**
     * Assert that exactly {@code expectedCount} items matching {@code template} exist across the inventory at
     * test-local position. The template's stack size is ignored and {@code expectedCount} may be zero.
     */
    public void assertInventoryCount(int x, int y, int z, ItemStack template, long expectedCount) {
        if (expectedCount < 0) {
            throw new IllegalArgumentException("expectedCount must be >= 0, got " + expectedCount);
        }
        long actualCount = countItems(x, y, z, template);
        if (actualCount != expectedCount) {
            throw new GameTestAssertException(
                "Inventory at (" + x
                    + ","
                    + y
                    + ","
                    + z
                    + ") expected exactly "
                    + expectedCount
                    + "x "
                    + template.getDisplayName()
                    + " but found "
                    + actualCount,
                absolute(x, y, z));
        }
    }

    /**
     * Assert an exact matching item count across the inventory at a test-local (relative) TestPos.
     */
    public void assertInventoryCount(TestPos pos, ItemStack template, long expectedCount) {
        assertInventoryCount(pos.x(), pos.y(), pos.z(), template, expectedCount);
    }

    /** Assert an exact matching item count in the inventory at the named structure position. */
    public void assertInventoryCount(String label, ItemStack template, long expectedCount) {
        assertInventoryCount(pos(label), template, expectedCount);
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

    /** Assert that every slot of the inventory at the test-local position is empty. */
    public void assertInventoryEmpty(TestPos pos) {
        assertInventoryEmpty(pos.x(), pos.y(), pos.z());
    }

    /** Assert that every slot of the inventory at the named structure position is empty. */
    public void assertInventoryEmpty(String label) {
        assertInventoryEmpty(pos(label));
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
        if (!InventoryHelper.stacksMatch(actual, expected) || actual.stackSize != expected.stackSize) {
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

    /** Assert the contents of a specific inventory slot at the test-local position. */
    public void assertSlot(TestPos pos, int slot, ItemStack expected) {
        assertSlot(pos.x(), pos.y(), pos.z(), slot, expected);
    }

    /** Assert the contents of a specific inventory slot at the named structure position. */
    public void assertSlot(String label, int slot, ItemStack expected) {
        assertSlot(pos(label), slot, expected);
    }

    /**
     * Directly set an inventory slot at test-local position to a copy of {@code stack}, bypassing normal insertion
     * rules, then mark the inventory dirty.
     */
    public void setSlot(int x, int y, int z, int slot, ItemStack stack) {
        InventoryHelper.setSlot(getInventoryAt(x, y, z), slot, stack);
    }

    /** Directly set an inventory slot at a test-local (relative) TestPos. */
    public void setSlot(TestPos pos, int slot, ItemStack stack) {
        setSlot(pos.x(), pos.y(), pos.z(), slot, stack);
    }

    /** Directly set an inventory slot at the named structure position. */
    public void setSlot(String label, int slot, ItemStack stack) {
        setSlot(pos(label), slot, stack);
    }

    /**
     * Directly clear an inventory slot at test-local position, bypassing normal extraction rules, then mark the
     * inventory dirty.
     */
    public void clearSlot(int x, int y, int z, int slot) {
        InventoryHelper.clearSlot(getInventoryAt(x, y, z), slot);
    }

    /** Directly clear an inventory slot at a test-local (relative) TestPos. */
    public void clearSlot(TestPos pos, int slot) {
        clearSlot(pos.x(), pos.y(), pos.z(), slot);
    }

    /** Directly clear an inventory slot at the named structure position. */
    public void clearSlot(String label, int slot) {
        clearSlot(pos(label), slot);
    }

    /**
     * Extract up to {@code maxAmount} items matching {@code template} from the inventory.
     * Returns the actual amount extracted.
     */
    public int extractItem(int x, int y, int z, ItemStack template, int maxAmount) {
        IInventory inv = getInventoryAt(x, y, z);
        return InventoryHelper.extract(inv, template, maxAmount);
    }

    /** Extract matching items from the inventory at the test-local position. */
    public int extractItem(TestPos pos, ItemStack template, int maxAmount) {
        return extractItem(pos.x(), pos.y(), pos.z(), template, maxAmount);
    }

    /** Extract matching items from the inventory at the named structure position. */
    public int extractItem(String label, ItemStack template, int maxAmount) {
        return extractItem(pos(label), template, maxAmount);
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

    /** Insert a FluidStack into the fluid handler at the test-local position. */
    public void insertFluid(TestPos pos, FluidStack fluid) {
        insertFluid(pos.x(), pos.y(), pos.z(), fluid);
    }

    /** Insert a FluidStack into the fluid handler at the named structure position. */
    public void insertFluid(String label, FluidStack fluid) {
        insertFluid(pos(label), fluid);
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

    /** Assert that the fluid handler at the test-local position contains at least {@code expected}. */
    public void assertFluidTank(TestPos pos, FluidStack expected) {
        assertFluidTank(pos.x(), pos.y(), pos.z(), expected);
    }

    /** Assert that the fluid handler at the named structure position contains at least {@code expected}. */
    public void assertFluidTank(String label, FluidStack expected) {
        assertFluidTank(pos(label), expected);
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

    /** Assert that the fluid handler at the test-local position has no fluid. */
    public void assertFluidTankEmpty(TestPos pos) {
        assertFluidTankEmpty(pos.x(), pos.y(), pos.z());
    }

    /** Assert that the fluid handler at the named structure position has no fluid. */
    public void assertFluidTankEmpty(String label) {
        assertFluidTankEmpty(pos(label));
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

    /** Place a block with metadata at the test-local position. */
    public void setBlock(TestPos pos, Block block, int meta) {
        setBlock(pos.x(), pos.y(), pos.z(), block, meta);
    }

    /** Place a block with metadata at the named structure position. */
    public void setBlock(String label, Block block, int meta) {
        setBlock(pos(label), block, meta);
    }

    public void setBlock(int x, int y, int z, Block block) {
        setBlock(x, y, z, block, 0);
    }

    /** Place a block at the test-local position. */
    public void setBlock(TestPos pos, Block block) {
        setBlock(pos, block, 0);
    }

    /** Place a block at the named structure position. */
    public void setBlock(String label, Block block) {
        setBlock(pos(label), block);
    }

    /**
     * Destroy the block at test-local position (replace with air). Drops are not spawned.
     */
    public void destroyBlock(int x, int y, int z) {
        TestPos pos = absolute(x, y, z);
        world.setBlock(pos.x(), pos.y(), pos.z(), Blocks.air, 0, 3);
    }

    /** Destroy the block at the test-local position. */
    public void destroyBlock(TestPos pos) {
        destroyBlock(pos.x(), pos.y(), pos.z());
    }

    /** Destroy the block at the named structure position. */
    public void destroyBlock(String label) {
        destroyBlock(pos(label));
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

    /** Apply an NBT compound to the TileEntity at the test-local position. */
    public void setTile(TestPos pos, NBTTagCompound nbt) {
        setTile(pos.x(), pos.y(), pos.z(), nbt);
    }

    /** Apply an NBT compound to the TileEntity at the named structure position. */
    public void setTile(String label, NBTTagCompound nbt) {
        setTile(pos(label), nbt);
    }

    private AxisAlignedBB localEntityBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int localMinX = Math.min(minX, maxX);
        int localMinY = Math.min(minY, maxY);
        int localMinZ = Math.min(minZ, maxZ);
        int localMaxX = Math.max(minX, maxX);
        int localMaxY = Math.max(minY, maxY);
        int localMaxZ = Math.max(minZ, maxZ);
        TestPos min = absolute(localMinX, localMinY, localMinZ);
        TestPos max = absolute(localMaxX, localMaxY, localMaxZ);
        return AxisAlignedBB.getBoundingBox(min.x(), min.y(), min.z(), max.x() + 1.0D, max.y() + 1.0D, max.z() + 1.0D);
    }

    private TestPos localDoublePos(double x, double y, double z) {
        return absolute((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    private TestPos entityPos(Entity entity) {
        return localDoublePos(entity.posX - originX, entity.posY - originY, entity.posZ - originZ);
    }

    private static String entityTypeName(Class<? extends Entity> type) {
        String name = type.getSimpleName();
        return name.isEmpty() ? type.getName() : name;
    }

    private static String entityPluralName(Class<? extends Entity> type) {
        return type == Entity.class ? "entities" : entityTypeName(type) + " entities";
    }

    private static String entityCountName(Class<? extends Entity> type, int count) {
        if (type == Entity.class) {
            return count == 1 ? "entity" : "entities";
        }
        return count == 1 ? entityTypeName(type) : entityTypeName(type) + " entities";
    }

    /**
     * Place a redstone block at the given test-local position for {@code durationTicks}, then remove it.
     * Uses a delayed sequence event on the test instance.
     */
    public void pulseRedstone(int x, int y, int z, int durationTicks) {
        setBlock(x, y, z, Blocks.redstone_block, 0);
        instance.scheduleDelayed(durationTicks, () -> destroyBlock(x, y, z));
    }

    /** Pulse a redstone block at the test-local position. */
    public void pulseRedstone(TestPos pos, int durationTicks) {
        pulseRedstone(pos.x(), pos.y(), pos.z(), durationTicks);
    }

    /** Pulse a redstone block at the named structure position. */
    public void pulseRedstone(String label, int durationTicks) {
        pulseRedstone(pos(label), durationTicks);
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

    /** Set a redstone signal source at the test-local position. */
    public void setRedstoneInput(TestPos pos, int strength) {
        setRedstoneInput(pos.x(), pos.y(), pos.z(), strength);
    }

    /** Set a redstone signal source at the named structure position. */
    public void setRedstoneInput(String label, int strength) {
        setRedstoneInput(pos(label), strength);
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

    /** Assert the minimum redstone power at the test-local position. */
    public void assertRedstonePower(TestPos pos, int minPower) {
        assertRedstonePower(pos.x(), pos.y(), pos.z(), minPower);
    }

    /** Assert the minimum redstone power at the named structure position. */
    public void assertRedstonePower(String label, int minPower) {
        assertRedstonePower(pos(label), minPower);
    }

    /**
     * Disable random block ticks in this world (e.g. crop growth, leaf decay) for deterministic tests.
     * The original {@code randomTickSpeed} value is automatically restored after the test.
     */
    public void disableRandomTicks() {
        String original = world.getGameRules()
            .getGameRuleStringValue("randomTickSpeed");
        world.getGameRules()
            .setOrCreateGameRule("randomTickSpeed", "0");
        afterTest(
            () -> world.getGameRules()
                .setOrCreateGameRule("randomTickSpeed", original));
    }

    /**
     * Fix the world time to a specific value and disable the daylight cycle.
     * The original time and {@code doDaylightCycle} value are automatically restored after the test.
     */
    public void fixWorldTime(long ticks) {
        String originalCycle = world.getGameRules()
            .getGameRuleStringValue("doDaylightCycle");
        long originalTime = world.getWorldTime();
        world.setWorldTime(ticks);
        world.getGameRules()
            .setOrCreateGameRule("doDaylightCycle", "false");
        afterTest(() -> {
            world.setWorldTime(originalTime);
            world.getGameRules()
                .setOrCreateGameRule("doDaylightCycle", originalCycle);
        });
    }

    /**
     * Apply a weather preset and lock it for the duration of the test.
     * The original weather state is automatically restored after the test.
     */
    public void setWeather(Weather weather) {
        WorldInfo info = world.getWorldInfo();
        boolean wasRaining = info.isRaining();
        boolean wasThundering = info.isThundering();
        int rainTime = info.getRainTime();
        int thunderTime = info.getThunderTime();
        weather.applyTo(world);
        afterTest(() -> {
            info.setRaining(wasRaining);
            info.setThundering(wasThundering);
            info.setRainTime(rainTime);
            info.setThunderTime(thunderTime);
        });
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

    /** Simulate a right-click on the block at the test-local position. */
    public boolean simulateRightClick(TestPos pos, FakePlayer player, ItemStack heldItem) {
        return simulateRightClick(pos.x(), pos.y(), pos.z(), player, heldItem);
    }

    /** Simulate a right-click on the block at the named structure position. */
    public boolean simulateRightClick(String label, FakePlayer player, ItemStack heldItem) {
        return simulateRightClick(pos(label), player, heldItem);
    }

    /**
     * Simulate a left-click (block punch) at test-local position.
     */
    public void simulateLeftClick(int x, int y, int z, FakePlayer player) {
        TestPos pos = absolute(x, y, z);
        Block block = world.getBlock(pos.x(), pos.y(), pos.z());
        block.onBlockClicked(world, pos.x(), pos.y(), pos.z(), player);
    }

    /** Simulate a left-click on the block at the test-local position. */
    public void simulateLeftClick(TestPos pos, FakePlayer player) {
        simulateLeftClick(pos.x(), pos.y(), pos.z(), player);
    }

    /** Simulate a left-click on the block at the named structure position. */
    public void simulateLeftClick(String label, FakePlayer player) {
        simulateLeftClick(pos(label), player);
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
