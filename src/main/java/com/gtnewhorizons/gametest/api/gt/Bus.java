package com.gtnewhorizons.gametest.api.gt;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.gametest.api.GameTestAssertException;
import com.gtnewhorizons.gametest.api.InventoryHelper;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.Experimental;
import com.gtnewhorizons.gametest.api.event.BusInserted;
import com.gtnewhorizons.gametest.api.event.ProgrammedCircuitSet;
import com.gtnewhorizons.gametest.internal.TestEventRecorder;

import gregtech.api.interfaces.IConfigurationCircuitSupport;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

/**
 * View of a single input or output bus tile. Obtained from {@link Multiblock}.
 */
@Experimental
public final class Bus {

    private final IGregTechTileEntity te;
    private final String label;
    private final TestEventRecorder recorder;

    Bus(IGregTechTileEntity te, String label) {
        this(te, label, null);
    }

    Bus(IGregTechTileEntity te, String label, TestEventRecorder recorder) {
        this.te = te;
        this.label = label;
        this.recorder = recorder;
    }

    /**
     * Inserts each stack using {@link InventoryHelper#insert}; fails if any stack is not fully accepted.
     */
    public Bus insert(ItemStack... stacks) {
        IInventory inv = inventory();
        for (ItemStack stack : stacks) {
            if (stack == null) continue;
            int leftover = InventoryHelper.insert(inv, stack);
            if (leftover > 0) {
                throw new GameTestAssertException(
                    "Could not fully insert " + stack
                        .getDisplayName() + " into " + label + ": " + leftover + " items remaining",
                    te.getXCoord(),
                    te.getYCoord(),
                    te.getZCoord());
            }
            if (recorder != null) {
                final ItemStack s = stack;
                final TestPos pos = new TestPos(te.getXCoord(), te.getYCoord(), te.getZCoord());
                recorder.record(
                    () -> new BusInserted(
                        recorder.clock()
                            .tick(),
                        pos,
                        s.getDisplayName(),
                        s.stackSize));
            }
        }
        return this;
    }

    /**
     * Sets the programmed circuit on this bus to {@code config} (0–24). Writes directly into the circuit slot to
     * bypass the normal inventory insertion that skips that slot.
     *
     * @throws IllegalArgumentException if {@code config} is out of range or the circuit item is unavailable
     * @throws GameTestAssertException  if this bus does not support configuration circuits
     */
    public Bus programmedCircuit(int config) {
        ItemStack circuit = GTUtility.getIntegratedCircuit(config);
        if (circuit == null) {
            throw new IllegalArgumentException("GTUtility.getIntegratedCircuit returned null for config " + config);
        }
        IMetaTileEntity mte = te.getMetaTileEntity();
        if (mte == null) {
            throw new GameTestAssertException(
                label + " has no meta tile entity",
                te.getXCoord(),
                te.getYCoord(),
                te.getZCoord());
        }
        if (!(mte instanceof IConfigurationCircuitSupport circuitSupport)) {
            throw new GameTestAssertException(
                label + " does not support configuration circuits ("
                    + mte.getClass()
                        .getSimpleName()
                    + ")",
                te.getXCoord(),
                te.getYCoord(),
                te.getZCoord());
        }
        if (!circuitSupport.allowSelectCircuit()) {
            throw new GameTestAssertException(
                label + " has circuit support disabled",
                te.getXCoord(),
                te.getYCoord(),
                te.getZCoord());
        }
        mte.setInventorySlotContents(circuitSupport.getCircuitSlot(), circuit);
        if (recorder != null) {
            TestPos pos = new TestPos(te.getXCoord(), te.getYCoord(), te.getZCoord());
            recorder.record(
                () -> new ProgrammedCircuitSet(
                    recorder.clock()
                        .tick(),
                    pos,
                    config));
        }
        return this;
    }

    /** Passes when at least one slot contains {@code stack} (item, damage, and NBT match; stack size ignored). */
    public void assertContains(ItemStack stack) {
        assertContains(ItemMatcher.of(stack));
    }

    /** Passes when at least one slot matches {@code matcher}. */
    public void assertContains(ItemMatcher matcher) {
        IInventory inv = inventory();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (slot != null && matcher.matches(slot)) return;
        }
        throw new GameTestAssertException(
            label + " does not contain " + matcher,
            te.getXCoord(),
            te.getYCoord(),
            te.getZCoord());
    }

    public void assertEmpty() {
        IInventory inv = inventory();
        if (!InventoryHelper.isEmpty(inv)) {
            throw new GameTestAssertException(label + " is not empty", te.getXCoord(), te.getYCoord(), te.getZCoord());
        }
    }

    /**
     * Stack in {@code index}, or {@code null} if empty.
     *
     * @throws IndexOutOfBoundsException if {@code index} is not in range
     */
    public ItemStack slot(int index) {
        IInventory inv = inventory();
        if (index < 0 || index >= inv.getSizeInventory()) {
            throw new IndexOutOfBoundsException(
                "Slot " + index + " out of range for " + label + " (size=" + inv.getSizeInventory() + ")");
        }
        return inv.getStackInSlot(index);
    }

    int size() {
        return inventory().getSizeInventory();
    }

    private IInventory inventory() {
        IMetaTileEntity mte = te.getMetaTileEntity();
        if (mte == null) {
            throw new GameTestAssertException(
                label + " has no meta tile entity (cannot access bus inventory)",
                te.getXCoord(),
                te.getYCoord(),
                te.getZCoord());
        }
        return mte;
    }
}
