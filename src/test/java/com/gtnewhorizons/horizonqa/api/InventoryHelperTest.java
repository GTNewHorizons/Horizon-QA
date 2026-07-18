package com.gtnewhorizons.horizonqa.api;

import static org.junit.Assert.*;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;
import org.junit.function.ThrowingRunnable;

public class InventoryHelperTest {

    private static final IInventory EMPTY_INVENTORY = new EmptyInventory();

    @Test
    public void insertRejectsNullArguments() {
        assertIllegalArgument("inventory must not be null", () -> InventoryHelper.insert(null, null));
        assertIllegalArgument("stack must not be null", () -> InventoryHelper.insert(EMPTY_INVENTORY, null));
    }

    @Test
    public void extractRejectsNullArguments() {
        assertIllegalArgument("inventory must not be null", () -> InventoryHelper.extract(null, null, 1));
        assertIllegalArgument("template must not be null", () -> InventoryHelper.extract(EMPTY_INVENTORY, null, 1));
    }

    @Test
    public void containsRejectsNullArguments() {
        assertIllegalArgument("inventory must not be null", () -> InventoryHelper.contains(null, null));
        assertIllegalArgument("stack must not be null", () -> InventoryHelper.contains(EMPTY_INVENTORY, null));
    }

    @Test
    public void countRejectsNullArguments() {
        assertIllegalArgument("inventory must not be null", () -> InventoryHelper.count(null, null));
        assertIllegalArgument("template must not be null", () -> InventoryHelper.count(EMPTY_INVENTORY, null));
    }

    @Test
    public void inventoryQueriesRejectNullInventory() {
        assertIllegalArgument("inventory must not be null", () -> InventoryHelper.isEmpty(null));
        assertIllegalArgument("inventory must not be null", () -> InventoryHelper.getSlot(null, 0));
    }

    @Test
    public void directSlotMutationRejectsNullArguments() {
        ItemStack stack = new ItemStack(new Item());
        assertIllegalArgument("inventory must not be null", () -> InventoryHelper.setSlot(null, 0, stack));
        assertIllegalArgument("stack must not be null", () -> InventoryHelper.setSlot(EMPTY_INVENTORY, 0, null));
        assertIllegalArgument("inventory must not be null", () -> InventoryHelper.clearSlot(null, 0));
    }

    @Test
    public void countSumsMatchingStacksAcrossEverySlot() {
        Item expectedItem = new Item();
        Item otherItem = new Item();
        SidedInventory inventory = new SidedInventory(
            stack(expectedItem, 3, 2, "expected"),
            stack(otherItem, 11, 2, "expected"),
            stack(expectedItem, 5, 3, "expected"),
            stack(expectedItem, 7, 2, "expected"),
            stack(expectedItem, 13, 2, "other"));

        assertEquals(10L, InventoryHelper.count(inventory, stack(expectedItem, 64, 2, "expected")));
        assertEquals(0L, InventoryHelper.count(inventory, new ItemStack(new Item())));
    }

    @Test
    public void directSlotMutationCopiesStackAndMarksInventoryDirty() {
        SidedInventory inventory = new SidedInventory((ItemStack) null);
        ItemStack supplied = new ItemStack(new Item(), 3);

        InventoryHelper.setSlot(inventory, 0, supplied);

        assertEquals(3, inventory.getStackInSlot(0).stackSize);
        assertNotSame(supplied, inventory.getStackInSlot(0));
        assertEquals(1, inventory.dirtyCount);

        InventoryHelper.clearSlot(inventory, 0);

        assertNull(inventory.getStackInSlot(0));
        assertEquals(2, inventory.dirtyCount);
    }

    @Test
    public void extractRespectsSidedInventoryRules() {
        Item item = new Item();
        SidedInventory inventory = new SidedInventory(new ItemStack(item, 3), new ItemStack(item, 4));

        int extracted = InventoryHelper.extract(inventory, new ItemStack(item), 5);

        assertEquals(4, extracted);
        assertEquals(3, inventory.getStackInSlot(0).stackSize);
        assertNull(inventory.getStackInSlot(1));
    }

    private static void assertIllegalArgument(String message, ThrowingRunnable action) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action);
        assertEquals(message, exception.getMessage());
    }

    private static ItemStack stack(Item item, int size, int damage, String marker) {
        ItemStack stack = new ItemStack(item, size, damage);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("marker", marker);
        stack.setTagCompound(nbt);
        return stack;
    }

    private static final class EmptyInventory implements IInventory {

        @Override
        public int getSizeInventory() {
            return 0;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return null;
        }

        @Override
        public ItemStack decrStackSize(int slot, int amount) {
            return null;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int slot) {
            return null;
        }

        @Override
        public void setInventorySlotContents(int slot, ItemStack stack) {}

        @Override
        public String getInventoryName() {
            return "empty";
        }

        @Override
        public boolean hasCustomInventoryName() {
            return false;
        }

        @Override
        public int getInventoryStackLimit() {
            return 64;
        }

        @Override
        public void markDirty() {}

        @Override
        public boolean isUseableByPlayer(EntityPlayer player) {
            return false;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return false;
        }
    }

    private static final class SidedInventory implements ISidedInventory {

        private final ItemStack[] stacks;
        private int dirtyCount;

        private SidedInventory(ItemStack... stacks) {
            this.stacks = stacks;
        }

        @Override
        public int[] getAccessibleSlotsFromSide(int side) {
            return side == 0 ? new int[] { 0, 1 } : new int[0];
        }

        @Override
        public boolean canInsertItem(int slot, ItemStack stack, int side) {
            return false;
        }

        @Override
        public boolean canExtractItem(int slot, ItemStack stack, int side) {
            return slot == 1;
        }

        @Override
        public int getSizeInventory() {
            return stacks.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stacks[slot];
        }

        @Override
        public ItemStack decrStackSize(int slot, int amount) {
            ItemStack stack = stacks[slot];
            if (stack == null) return null;
            int removed = Math.min(amount, stack.stackSize);
            ItemStack result = stack.copy();
            result.stackSize = removed;
            stack.stackSize -= removed;
            if (stack.stackSize == 0) stacks[slot] = null;
            return result;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int slot) {
            return stacks[slot];
        }

        @Override
        public void setInventorySlotContents(int slot, ItemStack stack) {
            stacks[slot] = stack;
        }

        @Override
        public String getInventoryName() {
            return "sided";
        }

        @Override
        public boolean hasCustomInventoryName() {
            return false;
        }

        @Override
        public int getInventoryStackLimit() {
            return 64;
        }

        @Override
        public void markDirty() {
            dirtyCount++;
        }

        @Override
        public boolean isUseableByPlayer(EntityPlayer player) {
            return false;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return false;
        }
    }
}
