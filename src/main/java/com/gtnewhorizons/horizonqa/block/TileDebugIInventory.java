package com.gtnewhorizons.horizonqa.block;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import java.util.ArrayList;
import java.util.UUID;

public class TileDebugIInventory extends TileEntity implements ISidedInventory {

    public final ItemStack inv[] = new ItemStack[3];
    public final ArrayList<String> allowedItemsIns[] = new ArrayList[2];
    public final boolean allowedSidesIns[] = new boolean[6];
    public final ArrayList<String> allowedItemsExt[] = new ArrayList[2];
    public final boolean allowedSidesExt[] = new boolean[6];
    public boolean disallowItemsExToggle = true;
    public boolean disallowItemsInToggle = true;
    public boolean disallowItemsToggle = true;
    public final ArrayList<String> allowedItems[] = new ArrayList[2];
    public int inventoryStackLimit = 64;
    public boolean failCheckIfNull = true;
    public boolean returnNullForNoItem = true;
    public boolean machineMode = true;

    public TileDebugIInventory() {
        allowedSidesIns[0] = true;
        allowedSidesIns[1] = true;
        allowedSidesIns[2] = true;
        allowedSidesIns[3] = true;
        allowedSidesIns[4] = true;
        allowedSidesIns[5] = true;
        allowedItemsIns[0] = new ArrayList<>();
        allowedItemsIns[1] = new ArrayList<>();
        allowedSidesExt[0] = true;
        allowedSidesExt[1] = true;
        allowedSidesExt[2] = true;
        allowedSidesExt[3] = true;
        allowedSidesExt[4] = true;
        allowedSidesExt[5] = true;
        allowedItemsExt[0] = new ArrayList<>();
        allowedItemsExt[1] = new ArrayList<>();
        allowedItems[0] = new ArrayList<>();
        allowedItems[1] = new ArrayList<>();
    }

    public final ArrayList<NBTTagCompound> log = new ArrayList<>();
    public boolean doLog = true;

    // public TileDebugIInventory.Error lastError = null;

    //DO NOT SAVE THESE TO NBT! These are sanity checks on a per-object basis!
    //Public for ease of access
    public UUID tileUUID = new UUID(worldObj.rand.nextLong(), worldObj.rand.nextLong());
    public boolean canInsertItemCalledOnce[] = new boolean[3];
    public boolean canExtractItemCalledOnce[] = new boolean[3];
    public boolean isItemValidForSlotCalledOnce[] = new boolean[3];

    @Override
    public int[] getAccessibleSlotsFromSide(int p_94128_1_) {
        return new int[]{0, 1, 2};
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack item, int side) {
        Object argObj[] = doLog ? new Object[]{slot, item, side} : null;
        boolean slotOOB = false;
        boolean sideOOB = false;
        if ( slot > 2 || slot < 0 ) slotOOB = true;
        if ( side < 0 || side >= 6 ) sideOOB = true;
        if (slotOOB || sideOOB) {
            if ( doLog)log.add(logEvent(Funcs.canInsertItem, argObj, ErrorWarn.ERR | (slotOOB ? ErrorWarn.SLOT | ErrorWarn.OOB : ErrorWarn.NONE) | (sideOOB ? ErrorWarn.SIDE | ErrorWarn.OOB : ErrorWarn.NONE) | ErrorWarn.CHECKING | ErrorWarn.INSERTION));
            return false;
        } canInsertItemCalledOnce[slot] = true;
        if ( doLog)log.add(logEvent(Funcs.canInsertItem, argObj, ErrorWarn.INSERTION | ErrorWarn.CHECKING | (item == null ? ErrorWarn.NULL_ITEMSTACK | ErrorWarn.WRN : item.stackSize == 0 ? ErrorWarn.ZERO_ITEMSTACK | ErrorWarn.WRN : item.stackSize < 0 ? ErrorWarn.NEGT_ITEMSTACK | ErrorWarn.WRN : item.stackSize > 127 ? ErrorWarn.O127_ITEMSTACK | ErrorWarn.WRN : item.stackSize > 64 ? ErrorWarn.OV64_ITEMSTACK | ErrorWarn.WRN : ErrorWarn.NONE)));
        if ( item == null ) return slot != 2 && allowedSidesExt[side] && !failCheckIfNull;
        return slot < 2 && allowedSidesIns[side] && Boolean.logicalXor(item.getItem() != null && allowedItemsIns[slot].contains(item.getItem().delegate.name()), disallowItemsInToggle);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack item, int side) {
        Object argObj[] = doLog ? new Object[]{slot, item, side} : null;
        boolean slotOOB = false;
        boolean sideOOB = false;
        if ( slot > 2 || slot < 0 ) slotOOB = true;
        if ( side < 0 || side >= 6 ) sideOOB = true;
        if (slotOOB || sideOOB) {
            if ( doLog)log.add(logEvent(Funcs.canExtractItem, argObj, ErrorWarn.ERR | (slotOOB ? ErrorWarn.SLOT | ErrorWarn.OOB : ErrorWarn.NONE) | (sideOOB ? ErrorWarn.SIDE | ErrorWarn.OOB : ErrorWarn.NONE) | ErrorWarn.CHECKING | ErrorWarn.EXTRACTION));
            return false;
        } canExtractItemCalledOnce[slot] = true;
        if ( doLog)log.add(logEvent(Funcs.canInsertItem, argObj, ErrorWarn.EXTRACTION | ErrorWarn.CHECKING | (item == null ? ErrorWarn.NULL_ITEMSTACK | ErrorWarn.WRN : item.stackSize == 0 ? ErrorWarn.ZERO_ITEMSTACK | ErrorWarn.WRN : item.stackSize < 0 ? ErrorWarn.NEGT_ITEMSTACK | ErrorWarn.WRN : item.stackSize > 127 ? ErrorWarn.O127_ITEMSTACK | ErrorWarn.WRN : item.stackSize > 64 ? ErrorWarn.OV64_ITEMSTACK | ErrorWarn.WRN : ErrorWarn.NONE)));
        if ( slot == 2 && machineMode && (inv[0] == null || inv[1] == null)) return false;
        if ( item == null ) return allowedSidesExt[side] && !failCheckIfNull;
        return slot == 2 || slot < 2 && allowedSidesExt[side] && Boolean.logicalXor(item.getItem() != null && allowedItemsExt[slot].contains(item.getItem().delegate.name()), disallowItemsExToggle);
    }

    @Override
    public int getSizeInventory() {
        return 3;
    }

    @Override
    public ItemStack getStackInSlot(int slotIn) {
        if ( slotIn < 0 || slotIn > 2 )return null; //todo: log
        if ( slotIn == 2 ) {
            ItemStack stack = getOutputItem();
            stack.setStackDisplayName("getStackInSlot: " + (inv[0] == null ? null : ((inv[0].getItem() == null ? null : inv[0].getItem().delegate.name())+" (" + inv[0].stackSize + ")")) + " | " + (inv[1] == null ? null : ((inv[1].getItem() == null ? null : inv[1].getItem().delegate.name())+" (" + inv[1].stackSize + ")")));
            return stack;
        }
        ItemStack stack = inv[slotIn];
        if ( stack == null ) return returnNullForNoItem ? null : getOutputItem().setStackDisplayName("getStackInSlot: null");
        stack.setStackDisplayName("getStackInSlot: " + (inv[slotIn] == null ? null : ((inv[slotIn].getItem() == null ? null : inv[slotIn].getItem().delegate.name())+" (" + inv[slotIn].stackSize + ")")));
        return stack;
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int index) {
        return null; //todo
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {

    }

    @Override
    public String getInventoryName() {
        return "Debug Inventory";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return true;
    }

    @Override
    public int getInventoryStackLimit() {
        return this.inventoryStackLimit;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {

    }

    @Override
    public void closeInventory() {

    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if ( slot < 0 || slot > 2 ) return false; //todo: log
        isItemValidForSlotCalledOnce[slot] = true;
        return slot < 2 && Boolean.logicalXor(stack.getItem() != null && allowedItems[slot].contains(stack.getItem().delegate.name()), disallowItemsToggle);
    }

    // gets a blank output item, not necessarily the item in slot 2
    public ItemStack getOutputItem() {
        return null;
    }

    public enum Funcs {
        getStackInSlot,
        setInventorySlotContents,
        decrStackSize,
        isItemValidForSlot,
        getStackInSlotOnClosing,
        getInventoryStackLimit,
        getSizeInventory,
        getAccessibleSlotsFromSide, canInsertItem, canExtractItem,
    }

    //bitwise OR these for a status code
    //first two bits = type, next few digits = more abt type
    //last four digits = specific thing
    public static class ErrorWarn {
        public static int NONE = 0;
        public static int ERR = 0xC0000000;
        public static int WRN = 0x80000000;
        public static int INSERTION = 0x10000;
        public static int EXTRACTION = 0x20000;
        public static int CHECKING = 0x100000;
        public static int NULL_ITEMSTACK = 0x1;
        public static int ZERO_ITEMSTACK = 0x2;
        public static int NEGT_ITEMSTACK = 0x3;
        public static int OV64_ITEMSTACK = 0x4;
        public static int O127_ITEMSTACK = 0x5;
        public static int DIDNT_CHECK = 0x10;
        public static int SLOT = 0x100;
        public static int SIDE = 0x200;
        public static int OOB = 0x80;
    }

    NBTTagCompound logEvent(Funcs event, Object[] args, int status ) {
        NBTTagCompound ret = new NBTTagCompound();
        ret.setString("inst_uuid", this.tileUUID.toString());
        ret.setInteger("func", event.ordinal());
        NBTTagList arglist = new NBTTagList();
        for ( Object a : args ) {
            if ( a instanceof Integer aint ) arglist.appendTag(new NBTTagInt(aint));
            else if ( a instanceof ItemStack astack) {
                NBTTagCompound stacktag = new NBTTagCompound();
                arglist.appendTag(astack.writeToNBT(stacktag));
            }
            else throw new IllegalArgumentException("Invalid arg type given to logEvent!\tClass: " + a.getClass().getName());
        }
        ret.setTag("args", arglist);
        ret.setInteger("status", status);
        return ret;
    }

}
