package com.gtnewhorizons.gametest.api.gt;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.gametest.api.GameTestAssertException;
import com.gtnewhorizons.gametest.api.InventoryHelper;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * View of a single input or output bus tile. Obtained from {@link Multiblock}.
 */
@Experimental
public final class Bus {

    private final IGregTechTileEntity te;
    private final String label;

    Bus(IGregTechTileEntity te, String label) {
        this.te = te;
        this.label = label;
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
                    "Could not fully insert "
                        + stack.getDisplayName()
                        + " into "
                        + label
                        + ": "
                        + leftover
                        + " items remaining", te.getXCoord(), te.getYCoord(), te.getZCoord());
            }
        }
        return this;
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
            throw new IndexOutOfBoundsException("Slot "
                + index
                + " out of range for "
                + label
                + " (size="
                + inv.getSizeInventory()
                + ")");
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
