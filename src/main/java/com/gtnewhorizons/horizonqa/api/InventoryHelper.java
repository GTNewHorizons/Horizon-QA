package com.gtnewhorizons.horizonqa.api;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

/**
 * Utility for inserting into / querying {@link IInventory} and {@link ISidedInventory} tile entities
 * without knowing which interface the block implements.
 */
@Experimental
public final class InventoryHelper {

    private InventoryHelper() {}

    /**
     * Insert {@code stack} into the inventory at the given tile. Tries {@link ISidedInventory} first
     * (testing all sides), then falls back to plain {@link IInventory} scanning. Returns the leftover
     * amount that could not be inserted (0 = fully inserted).
     */
    public static int insert(IInventory inventory, ItemStack stack) {
        requireInventory(inventory);
        requireStack(stack, "stack");
        if (stack.stackSize <= 0) return 0;

        ItemStack toInsert = stack.copy();

        if (inventory instanceof ISidedInventory sided) {
            for (int side = 0; side < 6 && toInsert.stackSize > 0; side++) {
                int[] slots = sided.getAccessibleSlotsFromSide(side);
                if (slots == null) continue;
                for (int slot : slots) {
                    if (!sided.canInsertItem(slot, toInsert, side)) continue;
                    toInsert.stackSize = mergeIntoSlot(inventory, slot, toInsert);
                    if (toInsert.stackSize <= 0) return 0;
                }
            }
        } else {
            for (int slot = 0; slot < inventory.getSizeInventory() && toInsert.stackSize > 0; slot++) {
                if (!inventory.isItemValidForSlot(slot, toInsert)) continue;
                toInsert.stackSize = mergeIntoSlot(inventory, slot, toInsert);
                if (toInsert.stackSize <= 0) return 0;
            }
        }
        return toInsert.stackSize;
    }

    /**
     * Extract up to {@code maxAmount} items matching {@code template} (item + meta + NBT) from the
     * inventory. Returns the actual amount extracted.
     */
    public static int extract(IInventory inventory, ItemStack template, int maxAmount) {
        requireInventory(inventory);
        requireStack(template, "template");
        if (maxAmount <= 0) return 0;

        int remaining = maxAmount;

        if (inventory instanceof ISidedInventory sided) {
            for (int side = 0; side < 6 && remaining > 0; side++) {
                int[] slots = sided.getAccessibleSlotsFromSide(side);
                if (slots == null) continue;
                for (int slot : slots) {
                    ItemStack existing = inventory.getStackInSlot(slot);
                    if (!stacksMatch(existing, template) || !sided.canExtractItem(slot, existing, side)) continue;
                    remaining -= extractFromSlot(inventory, slot, template, remaining);
                    if (remaining <= 0) break;
                }
            }
        } else {
            for (int slot = 0; slot < inventory.getSizeInventory() && remaining > 0; slot++) {
                remaining -= extractFromSlot(inventory, slot, template, remaining);
            }
        }
        return maxAmount - remaining;
    }

    /** Check if the inventory contains at least {@code stack.stackSize} items matching {@code stack}. */
    public static boolean contains(IInventory inventory, ItemStack stack) {
        requireInventory(inventory);
        requireStack(stack, "stack");
        int needed = stack.stackSize;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack slot = inventory.getStackInSlot(i);
            if (stacksMatch(slot, stack)) {
                needed -= slot.stackSize;
                if (needed <= 0) return true;
            }
        }
        return false;
    }

    /** Check if every slot in the inventory is null or has stackSize 0. */
    public static boolean isEmpty(IInventory inventory) {
        requireInventory(inventory);
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack slot = inventory.getStackInSlot(i);
            if (slot != null && slot.stackSize > 0) return false;
        }
        return true;
    }

    /** Get the {@link ItemStack} at a specific slot, or null. */
    public static ItemStack getSlot(IInventory inventory, int slot) {
        requireInventory(inventory);
        if (slot < 0 || slot >= inventory.getSizeInventory()) return null;
        return inventory.getStackInSlot(slot);
    }

    /**
     * Match two stacks by item, damage, and NBT (ignoring stack size).
     */
    public static boolean stacksMatch(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getItem() != b.getItem()) return false;
        if (a.getItemDamage() != b.getItemDamage()) return false;
        if (a.getTagCompound() == null && b.getTagCompound() == null) return true;
        if (a.getTagCompound() == null || b.getTagCompound() == null) return false;
        return a.getTagCompound()
            .equals(b.getTagCompound());
    }

    private static void requireInventory(IInventory inventory) {
        if (inventory == null) throw new IllegalArgumentException("inventory must not be null");
    }

    private static void requireStack(ItemStack stack, String name) {
        if (stack == null) throw new IllegalArgumentException(name + " must not be null");
    }

    private static int mergeIntoSlot(IInventory inv, int slot, ItemStack toInsert) {
        ItemStack existing = inv.getStackInSlot(slot);
        if (existing == null) {
            int maxStack = Math.min(inv.getInventoryStackLimit(), toInsert.getMaxStackSize());
            int placing = Math.min(toInsert.stackSize, maxStack);
            ItemStack placed = toInsert.copy();
            placed.stackSize = placing;
            inv.setInventorySlotContents(slot, placed);
            inv.markDirty();
            return toInsert.stackSize - placing;
        }
        if (!stacksMatch(existing, toInsert)) return toInsert.stackSize;
        int maxStack = Math.min(inv.getInventoryStackLimit(), existing.getMaxStackSize());
        int space = maxStack - existing.stackSize;
        if (space <= 0) return toInsert.stackSize;
        int transferring = Math.min(toInsert.stackSize, space);
        existing.stackSize += transferring;
        inv.markDirty();
        return toInsert.stackSize - transferring;
    }

    private static int extractFromSlot(IInventory inv, int slot, ItemStack template, int maxAmount) {
        ItemStack existing = inv.getStackInSlot(slot);
        if (existing == null || !stacksMatch(existing, template)) return 0;
        int taking = Math.min(existing.stackSize, maxAmount);
        existing.stackSize -= taking;
        if (existing.stackSize <= 0) {
            inv.setInventorySlotContents(slot, null);
        }
        inv.markDirty();
        return taking;
    }
}
