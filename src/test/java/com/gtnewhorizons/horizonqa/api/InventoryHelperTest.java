package com.gtnewhorizons.horizonqa.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

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
    public void inventoryQueriesRejectNullInventory() {
        assertIllegalArgument("inventory must not be null", () -> InventoryHelper.isEmpty(null));
        assertIllegalArgument("inventory must not be null", () -> InventoryHelper.getSlot(null, 0));
    }

    private static void assertIllegalArgument(String message, ThrowingRunnable action) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action);
        assertEquals(message, exception.getMessage());
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
}
