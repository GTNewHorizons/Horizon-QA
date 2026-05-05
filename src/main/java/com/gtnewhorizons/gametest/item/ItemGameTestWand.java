package com.gtnewhorizons.gametest.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemGameTestWand extends Item {

    public static ItemGameTestWand INSTANCE;

    public static final String TAG_POS1_X = "pos1X";
    public static final String TAG_POS1_Y = "pos1Y";
    public static final String TAG_POS1_Z = "pos1Z";
    public static final String TAG_POS1_SET = "pos1Set";
    public static final String TAG_POS2_X = "pos2X";
    public static final String TAG_POS2_Y = "pos2Y";
    public static final String TAG_POS2_Z = "pos2Z";
    public static final String TAG_POS2_SET = "pos2Set";
    public static final String TAG_PENDING = "pending";

    public ItemGameTestWand() {
        super();
        setUnlocalizedName("gametest.wand");
        setTextureName("minecraft:blaze_rod");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            NBTTagCompound nbt = getOrCreateNBT(stack);
            if (nbt.getBoolean(TAG_PENDING)) {
                setPos2(stack, player, x, y, z);
            } else {
                setPos1(stack, player, x, y, z);
            }
        }
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            int[] pos = getLookingAtBlock(player);
            NBTTagCompound nbt = getOrCreateNBT(stack);
            if (nbt.getBoolean(TAG_PENDING)) {
                setPos2(stack, player, pos[0], pos[1], pos[2]);
            } else {
                setPos1(stack, player, pos[0], pos[1], pos[2]);
            }
        }
        return stack;
    }

    public static int[] getLookingAtBlock(EntityPlayer player) {
        double dist = player instanceof EntityPlayerMP
            ? ((EntityPlayerMP) player).theItemInWorldManager.getBlockReachDistance()
            : 5.0;

        Vec3 start = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLookVec();
        Vec3 end = Vec3.createVectorHelper(
            start.xCoord + look.xCoord * dist,
            start.yCoord + look.yCoord * dist,
            start.zCoord + look.zCoord * dist);

        MovingObjectPosition hit = player.worldObj.rayTraceBlocks(start, end);

        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return new int[] { hit.blockX, hit.blockY, hit.blockZ };
        } else {
            return new int[] {
                MathHelper.floor_double(end.xCoord),
                MathHelper.floor_double(end.yCoord),
                MathHelper.floor_double(end.zCoord) };
        }
    }

    public static void setPos1(ItemStack stack, EntityPlayer player, int x, int y, int z) {
        NBTTagCompound nbt = getOrCreateNBT(stack);
        nbt.setInteger(TAG_POS1_X, x);
        nbt.setInteger(TAG_POS1_Y, y);
        nbt.setInteger(TAG_POS1_Z, z);
        nbt.setBoolean(TAG_POS1_SET, true);
        nbt.setBoolean(TAG_POS2_SET, false);
        nbt.setBoolean(TAG_PENDING, true);
        player.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + "Pos1"
                    + EnumChatFormatting.RESET
                    + " set to ("
                    + x
                    + ", "
                    + y
                    + ", "
                    + z
                    + ") — right-click to set "
                    + EnumChatFormatting.AQUA
                    + "Pos2"));
    }

    public static void setPos2(ItemStack stack, EntityPlayer player, int x, int y, int z) {
        NBTTagCompound nbt = getOrCreateNBT(stack);
        nbt.setInteger(TAG_POS2_X, x);
        nbt.setInteger(TAG_POS2_Y, y);
        nbt.setInteger(TAG_POS2_Z, z);
        nbt.setBoolean(TAG_POS2_SET, true);
        nbt.setBoolean(TAG_PENDING, false);
        player.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.AQUA + "Pos2"
                    + EnumChatFormatting.RESET
                    + " set to ("
                    + x
                    + ", "
                    + y
                    + ", "
                    + z
                    + ")"));
        printDimensions(player, nbt);
    }

    private static void printDimensions(EntityPlayer player, NBTTagCompound nbt) {
        if (nbt.getBoolean(TAG_POS1_SET) && nbt.getBoolean(TAG_POS2_SET)) {
            int dx = Math.abs(nbt.getInteger(TAG_POS2_X) - nbt.getInteger(TAG_POS1_X)) + 1;
            int dy = Math.abs(nbt.getInteger(TAG_POS2_Y) - nbt.getInteger(TAG_POS1_Y)) + 1;
            int dz = Math.abs(nbt.getInteger(TAG_POS2_Z) - nbt.getInteger(TAG_POS1_Z)) + 1;
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.YELLOW + "Selection: "
                        + dx
                        + "×"
                        + dy
                        + "×"
                        + dz
                        + " ("
                        + (dx * dy * dz)
                        + " blocks)"));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("rawtypes")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        NBTTagCompound nbt = stack.getTagCompound();

        if (nbt == null || !nbt.getBoolean(TAG_POS1_SET)) {
            list.add(
                EnumChatFormatting.GRAY + "Pos1: "
                    + EnumChatFormatting.DARK_GRAY
                    + "Not set (left-click or right-click)");
        } else {
            list.add(
                EnumChatFormatting.GREEN + "Pos1: "
                    + EnumChatFormatting.WHITE
                    + "("
                    + nbt.getInteger(TAG_POS1_X)
                    + ", "
                    + nbt.getInteger(TAG_POS1_Y)
                    + ", "
                    + nbt.getInteger(TAG_POS1_Z)
                    + ")");
        }

        boolean pending = nbt != null && nbt.getBoolean(TAG_PENDING);
        if (nbt == null || !nbt.getBoolean(TAG_POS2_SET)) {
            list.add(
                EnumChatFormatting.GRAY + "Pos2: "
                    + (pending ? EnumChatFormatting.YELLOW + "Aim and right-click to confirm"
                        : EnumChatFormatting.DARK_GRAY + "Not set"));
        } else {
            list.add(
                EnumChatFormatting.AQUA + "Pos2: "
                    + EnumChatFormatting.WHITE
                    + "("
                    + nbt.getInteger(TAG_POS2_X)
                    + ", "
                    + nbt.getInteger(TAG_POS2_Y)
                    + ", "
                    + nbt.getInteger(TAG_POS2_Z)
                    + ")");
        }

        if (nbt != null && nbt.getBoolean(TAG_POS1_SET) && nbt.getBoolean(TAG_POS2_SET)) {
            int dx = Math.abs(nbt.getInteger(TAG_POS2_X) - nbt.getInteger(TAG_POS1_X)) + 1;
            int dy = Math.abs(nbt.getInteger(TAG_POS2_Y) - nbt.getInteger(TAG_POS1_Y)) + 1;
            int dz = Math.abs(nbt.getInteger(TAG_POS2_Z) - nbt.getInteger(TAG_POS1_Z)) + 1;
            list.add(EnumChatFormatting.YELLOW + "Size: " + dx + "×" + dy + "×" + dz);
        }
    }

    public static NBTTagCompound getOrCreateNBT(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }
}
