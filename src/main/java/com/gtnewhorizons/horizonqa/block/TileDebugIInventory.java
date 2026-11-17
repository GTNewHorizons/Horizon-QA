package com.gtnewhorizons.horizonqa.block;

import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.horizonqa.item.ItemDebugItem;

public class TileDebugIInventory extends TileEntity implements ISidedInventory {

    public final ItemStack inv[] = new ItemStack[3];
    public final boolean allowedSidesIns[] = new boolean[6];
    public final boolean allowedSidesExt[] = new boolean[6];
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
        allowedSidesExt[0] = true;
        allowedSidesExt[1] = true;
        allowedSidesExt[2] = true;
        allowedSidesExt[3] = true;
        allowedSidesExt[4] = true;
        allowedSidesExt[5] = true;
        allowedItems[0] = new ArrayList<>();
        allowedItems[1] = new ArrayList<>();
    }

    public final ArrayList<NBTTagCompound> log = new ArrayList<>();
    public boolean doLog = true;

    // public TileDebugIInventory.Error lastError = null;

    // DO NOT SAVE THESE TO NBT! These are sanity checks on a per-object basis!
    // Public for ease of access
    public final UUID tileUUID = new UUID(worldObj.rand.nextLong(), worldObj.rand.nextLong());
    public final boolean canInsertItemCalledOnce[] = new boolean[3];
    public final boolean canExtractItemCalledOnce[] = new boolean[3];
    public final boolean isItemValidForSlotCalledOnce[] = new boolean[3];
    public final boolean getStackInSlotCalledOnce[] = new boolean[3];
    public final boolean getAccessibleSlotsFromSideCalledOnce[] = new boolean[6];
    public boolean getSizeInventoryCalledOnce = false;

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        if (side < 0 || side >= 6) {
            if (doLog) logEvent(
                Funcs.getAccessibleSlotsFromSide,
                new Object[] { side },
                ErrorWarn.ERR | ErrorWarn.OpType.CHECKING | (side < 0 ? ErrorWarn.Side.NEGT : ErrorWarn.Side.HIGH));
        }
        getAccessibleSlotsFromSideCalledOnce[side] = true;
        if (doLog) logEvent(Funcs.getAccessibleSlotsFromSide, new Object[] { side }, ErrorWarn.NONE);
        if (allowedSidesIns[side]) {
            if (allowedSidesExt[side]) return new int[] { 0, 1, 2 };
            return new int[] { 0, 1 };
        }
        if (allowedSidesExt[side]) return new int[] { 2 };
        return new int[0];
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack item, int side) {
        Object argObj[] = doLog ? new Object[] { slot, item, side } : null;
        boolean slotOOB = false;
        boolean sideOOB = false;
        if (slot > 2 || slot < 0) slotOOB = true;
        if (side < 0 || side >= 6) sideOOB = true;
        if (slotOOB || sideOOB) {
            if (doLog) log.add(
                logEvent(
                    Funcs.canInsertItem,
                    argObj,
                    ErrorWarn.ERR | (slotOOB ? slot < 0 ? ErrorWarn.Slot.NEGT : ErrorWarn.Slot.HIGH : ErrorWarn.NONE)
                        | (sideOOB ? side < 0 ? ErrorWarn.Side.NEGT : ErrorWarn.Side.HIGH : ErrorWarn.NONE)
                        | ErrorWarn.OpType.CHECKING
                        | ErrorWarn.OpType.INSERTION));
            return false;
        }
        canInsertItemCalledOnce[slot] = true;
        if (doLog) log.add(
            logEvent(
                Funcs.canInsertItem,
                argObj,
                ErrorWarn.OpType.INSERTION | ErrorWarn.OpType.CHECKING
                    | (item == null ? ErrorWarn.ItemStack.NULL | ErrorWarn.WRN
                        : item.stackSize == 0 ? ErrorWarn.ItemStack.ZERO | ErrorWarn.WRN
                            : item.stackSize < 0 ? ErrorWarn.ItemStack.NEGT | ErrorWarn.WRN
                                : item.stackSize > 127 ? ErrorWarn.ItemStack.O127 | ErrorWarn.WRN
                                    : item.stackSize > 64 ? ErrorWarn.ItemStack.OV64 | ErrorWarn.WRN
                                        : ErrorWarn.NONE)));
        if (item == null) return slot != 2 && allowedSidesExt[side] && !failCheckIfNull;
        return slot < 2 && allowedSidesIns[side]
            && Boolean.logicalXor(
                item.getItem() != null && allowedItems[slot].contains(item.getItem().delegate.name()),
                disallowItemsToggle);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack item, int side) {
        Object argObj[] = doLog ? new Object[] { slot, item, side } : null;
        boolean slotOOB = false;
        boolean sideOOB = false;
        if (slot > 2 || slot < 0) slotOOB = true;
        if (side < 0 || side >= 6) sideOOB = true;
        if (slotOOB || sideOOB) {
            if (doLog) log.add(
                logEvent(
                    Funcs.canExtractItem,
                    argObj,
                    ErrorWarn.ERR | (slotOOB ? slot < 0 ? ErrorWarn.Slot.NEGT : ErrorWarn.Slot.HIGH : ErrorWarn.NONE)
                        | (sideOOB ? side < 0 ? ErrorWarn.Side.NEGT : ErrorWarn.Side.HIGH : ErrorWarn.NONE)
                        | ErrorWarn.OpType.CHECKING
                        | ErrorWarn.OpType.EXTRACTION));
            return false;
        }
        canExtractItemCalledOnce[slot] = true;
        if (doLog) log.add(
            logEvent(
                Funcs.canInsertItem,
                argObj,
                ErrorWarn.OpType.EXTRACTION | ErrorWarn.OpType.CHECKING
                    | (item == null ? ErrorWarn.ItemStack.NULL | ErrorWarn.WRN
                        : item.stackSize == 0 ? ErrorWarn.ItemStack.ZERO | ErrorWarn.WRN
                            : item.stackSize < 0 ? ErrorWarn.ItemStack.NEGT | ErrorWarn.WRN
                                : item.stackSize > 127 ? ErrorWarn.ItemStack.O127 | ErrorWarn.WRN
                                    : item.stackSize > 64 ? ErrorWarn.ItemStack.OV64 | ErrorWarn.WRN
                                        : ErrorWarn.NONE)));
        if (slot == 2 && machineMode && (inv[0] == null || inv[1] == null)) return false;
        if (item == null) return allowedSidesExt[side] && !failCheckIfNull;
        return slot == 2 || slot < 2 && allowedSidesExt[side];
    }

    @Override
    public int getSizeInventory() {
        getSizeInventoryCalledOnce = true;
        return 3;
    }

    @Override
    public ItemStack getStackInSlot(int slotIn) {
        if (slotIn < 0 || slotIn > 2) {
            if (doLog) log.add(
                logEvent(
                    Funcs.getStackInSlot,
                    new Object[] { slotIn },
                    ErrorWarn.ERR | ErrorWarn.OpType.EXTRACTION
                        | (slotIn < 0 ? ErrorWarn.Slot.NEGT : ErrorWarn.Slot.HIGH)));
            return null;
        }
        if (slotIn == 2) {
            if (machineMode && (inv[0] == null || inv[1] == null || inv[0].stackSize < 1 || inv[1].stackSize < 1)) {
                if (doLog) log.add(
                    logEvent(
                        Funcs.getStackInSlot,
                        new Object[] { slotIn },
                        ErrorWarn.WRN | ErrorWarn.OpType.EXTRACTION | ErrorWarn.ItemStack.INSUF));
                return returnNullForNoItem ? null : getOutputItem().setStackDisplayName("getStackInSlot: null");
            }
            ItemStack stack = getOutputItem();
            stack.setStackDisplayName(
                "getStackInSlot: "
                    + (inv[0] == null ? null
                        : ((inv[0].getItem() == null ? null : inv[0].getItem().delegate.name()) + " ("
                            + inv[0].stackSize
                            + ")"))
                    + " | "
                    + (inv[1] == null ? null
                        : ((inv[1].getItem() == null ? null : inv[1].getItem().delegate.name()) + " ("
                            + inv[1].stackSize
                            + ")")));
            stack.stackSize = machineMode ? Math.min(inv[0].stackSize, inv[1].stackSize) : 64;
            if (doLog) log.add(logEvent(Funcs.getStackInSlot, new Object[] { slotIn }, ErrorWarn.OpType.EXTRACTION));
            return stack;
        }
        ItemStack stack = inv[slotIn];
        if (stack == null) {
            if (doLog) log.add(
                logEvent(
                    Funcs.getStackInSlot,
                    new Object[] { slotIn },
                    ErrorWarn.WRN | ErrorWarn.OpType.EXTRACTION | ErrorWarn.ItemStack.NULL));
            return returnNullForNoItem ? null : getOutputItem().setStackDisplayName("getStackInSlot: null");
        }
        stack.setStackDisplayName(
            "getStackInSlot: " + (inv[slotIn] == null ? null
                : ((inv[slotIn].getItem() == null ? null : inv[slotIn].getItem().delegate.name()) + " ("
                    + inv[slotIn].stackSize
                    + ")")));
        if (doLog) log.add(logEvent(Funcs.getStackInSlot, new Object[] { slotIn }, ErrorWarn.OpType.EXTRACTION));
        return stack;
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        Object argObjs[] = doLog ? new Object[] { index, count } : null;
        boolean indexOOB = index < 0 || index > 2;
        boolean countOOB = count < 0 || count > 64;
        if (indexOOB || count < 0) {
            if (doLog) log.add(
                logEvent(
                    Funcs.decrStackSize,
                    argObjs,
                    ErrorWarn.ERR | ErrorWarn.OpType.EXTRACTION
                        | (indexOOB ? index < 0 ? ErrorWarn.Slot.NEGT : ErrorWarn.Slot.HIGH : ErrorWarn.NONE)
                        | (countOOB ? index < 0 ? ErrorWarn.Count.NEGT : ErrorWarn.Count.HIGH : ErrorWarn.NONE)));
            return null;
        }
        if (index == 2) {
            if (machineMode && (inv[0] == null || inv[1] == null || inv[0].stackSize < 1 || inv[1].stackSize < 1)) {
                if (doLog) log.add(
                    logEvent(
                        Funcs.decrStackSize,
                        argObjs,
                        ErrorWarn.ERR | ErrorWarn.OpType.EXTRACTION
                            | ErrorWarn.ItemStack.INSUF
                            | (countOOB ? ErrorWarn.Count.HIGH : ErrorWarn.NONE)));
                return returnNullForNoItem ? null : getOutputItem().setStackDisplayName("decrStackSize: null");
            }
            int insuf = 0;
            if (machineMode && (inv[0].stackSize < count || inv[1].stackSize < count)) {
                insuf = ErrorWarn.WRN | ErrorWarn.ItemStack.INSUF;
                count = Math.min(inv[0].stackSize, inv[1].stackSize);
            }
            ItemStack stack = getOutputItem();
            stack.setStackDisplayName(
                "decrStackSize: "
                    + (inv[0] == null ? null
                        : ((inv[0].getItem() == null ? null : inv[0].getItem().delegate.name()) + " ("
                            + inv[0].stackSize
                            + ")"))
                    + " | "
                    + (inv[1] == null ? null
                        : ((inv[1].getItem() == null ? null : inv[1].getItem().delegate.name()) + " ("
                            + inv[1].stackSize
                            + ")")));
            stack.stackSize = count;
            if (doLog) log.add(
                logEvent(
                    Funcs.decrStackSize,
                    argObjs,
                    ErrorWarn.OpType.EXTRACTION | insuf
                        | (countOOB ? ErrorWarn.WRN | ErrorWarn.Count.HIGH : ErrorWarn.NONE)));
            return stack;
        }
        ItemStack stack = inv[index];
        if (stack == null || stack.stackSize < 1) {
            if (doLog) log.add(
                logEvent(
                    Funcs.decrStackSize,
                    argObjs,
                    ErrorWarn.ERR | ErrorWarn.OpType.EXTRACTION
                        | ErrorWarn.ItemStack.NULL
                        | (countOOB ? ErrorWarn.Count.HIGH : ErrorWarn.NONE)));
            return returnNullForNoItem ? null : getOutputItem().setStackDisplayName("decrStackSize: null");
        }
        int insuf = 0;
        if (stack.stackSize < count) {
            insuf = ErrorWarn.WRN | ErrorWarn.ItemStack.INSUF;
            count = stack.stackSize;
        }
        ItemStack out;
        if (count == stack.stackSize) out = stack;
        else {
            out = stack.copy();
            out.stackSize = count;
            stack.stackSize -= count;
        }
        if (doLog) log.add(
            logEvent(
                Funcs.decrStackSize,
                argObjs,
                ErrorWarn.OpType.EXTRACTION | insuf
                    | (countOOB ? ErrorWarn.WRN | ErrorWarn.Count.HIGH : ErrorWarn.NONE)));
        return out;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int index) {
        return null; // todo : not a huge priority (GUI)
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        Object argObjs[] = doLog ? new Object[] { index, stack } : null;
        boolean indexOOB = index < 0 || index > 2;
        boolean stackBad = stack != null && stack.stackSize < 0;
        int warnNums = stack == null ? ErrorWarn.NONE
            : stack.stackSize == 0 ? ErrorWarn.ItemStack.ZERO | ErrorWarn.WRN
                : stack.stackSize < 0 ? ErrorWarn.ItemStack.NEGT | ErrorWarn.WRN
                    : stack.stackSize > 127 ? ErrorWarn.ItemStack.O127 | ErrorWarn.WRN
                        : stack.stackSize > 64 ? ErrorWarn.ItemStack.OV64 | ErrorWarn.WRN : ErrorWarn.NONE;
        if (indexOOB || stackBad) {
            if (doLog) log.add(
                logEvent(
                    Funcs.setInventorySlotContents,
                    argObjs,
                    ErrorWarn.ERR | (indexOOB ? index < 0 ? ErrorWarn.Slot.NEGT : ErrorWarn.Slot.HIGH : ErrorWarn.NONE)
                        | warnNums));
            return;
        }
        if (index == 2) {
            if (stack == null) {
                stack = getOutputItem();
                stack.stackSize = 0;
            }
            if (stack.getItem().delegate.name() != getOutputItem().getItem().delegate.name()
                || machineMode && (inv[0] == null || inv[1] == null)) {
                if (doLog) log.add(
                    logEvent(
                        Funcs.setInventorySlotContents,
                        argObjs,
                        ErrorWarn.ERR | warnNums | ErrorWarn.ItemStack.BAD));
                return;
            }
            int maxCnt = Math.min(inv[0].stackSize, inv[1].stackSize);
            int delta = stack.stackSize - maxCnt;
            if (machineMode) {
                inv[0].stackSize = Math.min(
                    0,
                    Math.max(
                        inv[0].getItem()
                            .getItemStackLimit(),
                        inv[0].stackSize + delta));
                inv[1].stackSize = Math.min(
                    0,
                    Math.max(
                        inv[1].getItem()
                            .getItemStackLimit(),
                        inv[1].stackSize + delta));
            } // this is to be *very* robust with weird setInventorySlotContents behaviors
            if (doLog) log.add(logEvent(Funcs.setInventorySlotContents, argObjs, warnNums));
            return;
        }
        if (stack != null && !Boolean
            .logicalXor(allowedItems[index].contains(stack.getItem().delegate.name()), disallowItemsToggle)) {
            if (doLog)
                log.add(logEvent(Funcs.setInventorySlotContents, argObjs, ErrorWarn.ERR | ErrorWarn.ItemStack.BAD));
            return;
        }
        inv[index] = stack;
        if (doLog) log.add(logEvent(Funcs.setInventorySlotContents, argObjs, warnNums));
        return;
    } // COULD implement extra warnings for weird behaviors here

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
        return false;
    }

    @Override
    public void openInventory() {

    }

    @Override
    public void closeInventory() {

    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        boolean badSlot = (slot < 0 || slot > 2);
        int warnNums = stack == null ? ErrorWarn.NONE
            : stack.stackSize == 0 ? ErrorWarn.ItemStack.ZERO | ErrorWarn.WRN
                : stack.stackSize < 0 ? ErrorWarn.ItemStack.NEGT | ErrorWarn.WRN
                    : stack.stackSize > 127 ? ErrorWarn.ItemStack.O127 | ErrorWarn.WRN
                        : stack.stackSize > 64 ? ErrorWarn.ItemStack.OV64 | ErrorWarn.WRN : ErrorWarn.NONE;
        if (badSlot) {
            if (doLog) log.add(
                logEvent(
                    Funcs.isItemValidForSlot,
                    new Object[] { slot, stack },
                    ErrorWarn.ERR | ErrorWarn.OpType.CHECKING
                        | warnNums
                        | (slot < 0 ? ErrorWarn.Slot.NEGT : ErrorWarn.Slot.HIGH)));
            return false;
        }
        if (doLog) log.add(
            logEvent(Funcs.isItemValidForSlot, new Object[] { slot, stack }, ErrorWarn.OpType.CHECKING | warnNums));
        isItemValidForSlotCalledOnce[slot] = true;
        return slot < 2 && Boolean.logicalXor(
            stack.getItem() != null && allowedItems[slot].contains(stack.getItem().delegate.name()),
            disallowItemsToggle);
    }

    // gets a blank output item, not necessarily the item in slot 2
    public ItemStack getOutputItem() {
        return new ItemStack(ItemDebugItem.INSTANCE, 1);
    }

    public enum Funcs {
        getStackInSlot,
        setInventorySlotContents,
        decrStackSize,
        isItemValidForSlot,
        getStackInSlotOnClosing,
        getInventoryStackLimit,
        getSizeInventory,
        getAccessibleSlotsFromSide,
        canInsertItem,
        canExtractItem,
    }

    // bitwise OR these for a status code
    // first two bits = type, next = op type, groups = error categories (each w/ own bits)
    // intended API: (err & OPTYPE) == INSERTION | CHECKING, (err & ITEMSTACK) != 0, etc
    public final static class ErrorWarn {

        public final static int NONE = 0;
        public final static int ERR = 0xC0000000;
        public final static int WRN = 0x80000000;
        public final static int OPTYPE = 0x30000000;

        public final static class OpType {

            public final static int INSERTION = 0x00000000;
            public final static int EXTRACTION = 0x10000000;
            public final static int CHECKING = 0x20000000;
        } // these can be OR'ed together

        public final static int ITEMSTACK = 0xF;

        public final static class ItemStack {

            public final static int NULL = 0x1;
            public final static int ZERO = 0x2;
            public final static int NEGT = 0x3;
            public final static int OV64 = 0x4;
            public final static int O127 = 0x5;
            public final static int INSUF = 0x6;
            public final static int BAD = 0x7;
        }

        public final static int SLOT = 0x30;

        public final static class Slot {

            public final static int NEGT = 0x10;
            public final static int HIGH = 0x20;
        }

        public final static int SIDE = 0xC0;

        public final static class Side {

            public final static int NEGT = 0x40;
            public final static int HIGH = 0x80;
        }

        public final static int COUNT = 0x300;

        public final static class Count {

            public final static int NEGT = 0x100;
            public final static int HIGH = 0x200;
        }
    }

    NBTTagCompound logEvent(Funcs event, Object[] args, int status) {
        NBTTagCompound ret = new NBTTagCompound();
        ret.setLong("time", worldObj.getTotalWorldTime());
        ret.setString("inst_uuid", this.tileUUID.toString());
        ret.setInteger("func", event.ordinal());
        NBTTagList arglist = new NBTTagList();
        for (Object a : args) {
            if (a instanceof Integer aint) arglist.appendTag(new NBTTagInt(aint));
            else if (a instanceof ItemStack astack) {
                NBTTagCompound stacktag = new NBTTagCompound();
                arglist.appendTag(astack.writeToNBT(stacktag));
            } else throw new IllegalArgumentException(
                "Invalid arg type given to logEvent!\tClass: " + a.getClass()
                    .getName());
        }
        ret.setTag("args", arglist);
        ret.setInteger("status", status);
        ret.setInteger(
            "prev",
            (canInsertItemCalledOnce[0] ? 0x1 : 0x0) | (canInsertItemCalledOnce[1] ? 0x2 : 0x0)
                | (canInsertItemCalledOnce[2] ? 0x4 : 0x0)
                | (canExtractItemCalledOnce[0] ? 0x8 : 0x0)
                | (canExtractItemCalledOnce[1] ? 0x10 : 0x0)
                | (canExtractItemCalledOnce[2] ? 0x20 : 0x0)
                | (isItemValidForSlotCalledOnce[0] ? 0x40 : 0x0)
                | (isItemValidForSlotCalledOnce[1] ? 0x80 : 0x0)
                | (isItemValidForSlotCalledOnce[2] ? 0x100 : 0x0)
                | (getStackInSlotCalledOnce[0] ? 0x200 : 0x0)
                | (getStackInSlotCalledOnce[1] ? 0x400 : 0x0)
                | (getStackInSlotCalledOnce[2] ? 0x800 : 0x0)
                | (getAccessibleSlotsFromSideCalledOnce[0] ? 0x1000 : 0x0)
                | (getAccessibleSlotsFromSideCalledOnce[1] ? 0x2000 : 0x0)
                | (getAccessibleSlotsFromSideCalledOnce[2] ? 0x4000 : 0x0)
                | (getAccessibleSlotsFromSideCalledOnce[3] ? 0x8000 : 0x0)
                | (getAccessibleSlotsFromSideCalledOnce[4] ? 0x10000 : 0x0)
                | (getAccessibleSlotsFromSideCalledOnce[5] ? 0x20000 : 0x0)
                | (getSizeInventoryCalledOnce ? 0x40000 : 0x0));
        return ret;
    }

}
