package com.gtnewhorizons.horizonqa.item;

import java.util.List;

import net.minecraft.client.Minecraft;
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
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemHorizonWand extends Item {

    public static ItemHorizonWand INSTANCE;

    public static final String TAG_POS1_X = "pos1X";
    public static final String TAG_POS1_Y = "pos1Y";
    public static final String TAG_POS1_Z = "pos1Z";
    public static final String TAG_POS1_SET = "pos1Set";
    public static final String TAG_POS2_X = "pos2X";
    public static final String TAG_POS2_Y = "pos2Y";
    public static final String TAG_POS2_Z = "pos2Z";
    public static final String TAG_POS2_SET = "pos2Set";
    public static final String TAG_PENDING = "pending";

    // dx/dy/dz offsets indexed by face side (0=down,1=up,2=north,3=south,4=west,5=east)
    private static final int[][] FACE_NORMALS = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };

    public ItemHorizonWand() {
        super();
        setUnlocalizedName("horizonqa.wand");
        setTextureName("minecraft:blaze_rod");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            int tx = x, ty = y, tz = z;
            if (player.isSneaking() && side >= 0 && side < 6) {
                tx += FACE_NORMALS[side][0];
                ty += FACE_NORMALS[side][1];
                tz += FACE_NORMALS[side][2];
            }
            NBTTagCompound nbt = getOrCreateNBT(stack);
            if (nbt.getBoolean(TAG_PENDING)) {
                setPos2(stack, player, tx, ty, tz);
            } else {
                setPos1(stack, player, tx, ty, tz);
            }
        }
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            int[] pos = getTargetedPosition(player);
            NBTTagCompound nbt = getOrCreateNBT(stack);
            if (nbt.getBoolean(TAG_PENDING)) {
                setPos2(stack, player, pos[0], pos[1], pos[2]);
            } else {
                setPos1(stack, player, pos[0], pos[1], pos[2]);
            }
        }
        return stack;
    }

    public static int[] getTargetedPosition(EntityPlayer player) {
        return getTargetedPosition(player, true);
    }

    public static int[] getTargetedPositionFromHit(int x, int y, int z, int side, boolean sneaking) {
        if (sneaking && side >= 0 && side < 6) {
            return new int[] { x + FACE_NORMALS[side][0], y + FACE_NORMALS[side][1], z + FACE_NORMALS[side][2] };
        }
        return new int[] { x, y, z };
    }

    private static int[] getTargetedPosition(EntityPlayer player, boolean includeSurfaceOffset) {
        double dist = getBlockReachDistance(player);

        Vec3 start = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLookVec();
        Vec3 end = Vec3.createVectorHelper(
            start.xCoord + look.xCoord * dist,
            start.yCoord + look.yCoord * dist,
            start.zCoord + look.zCoord * dist);

        MovingObjectPosition hit = player.worldObj.rayTraceBlocks(start, end);

        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            int tx = hit.blockX;
            int ty = hit.blockY;
            int tz = hit.blockZ;
            if (includeSurfaceOffset && player.isSneaking() && hit.sideHit >= 0 && hit.sideHit < 6) {
                tx += FACE_NORMALS[hit.sideHit][0];
                ty += FACE_NORMALS[hit.sideHit][1];
                tz += FACE_NORMALS[hit.sideHit][2];
            }
            return new int[] { tx, ty, tz };
        } else {
            return new int[] { MathHelper.floor_double(end.xCoord), MathHelper.floor_double(end.yCoord),
                MathHelper.floor_double(end.zCoord) };
        }
    }

    private static double getBlockReachDistance(EntityPlayer player) {
        if (player.worldObj.isRemote) {
            return getClientBlockReachDistance();
        }
        if (player instanceof EntityPlayerMP) {
            return ((EntityPlayerMP) player).theItemInWorldManager.getBlockReachDistance();
        }
        return 5.0;
    }

    @SideOnly(Side.CLIENT)
    private static double getClientBlockReachDistance() {
        return Minecraft.getMinecraft().playerController.getBlockReachDistance();
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
                EnumChatFormatting.GREEN
                    + String.format(StatCollector.translateToLocal("horizonqa.wand.pos1.set"), x, y, z)));
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
                EnumChatFormatting.AQUA
                    + String.format(StatCollector.translateToLocal("horizonqa.wand.pos2.set"), x, y, z)));
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        NBTTagCompound nbt = stack.getTagCompound();

        if (nbt == null || !nbt.getBoolean(TAG_POS1_SET)) {
            list.add(StatCollector.translateToLocal("horizonqa.wand.tooltip.pos1.unset"));
        } else {
            list.add(
                String.format(
                    StatCollector.translateToLocal("horizonqa.wand.tooltip.pos1"),
                    nbt.getInteger(TAG_POS1_X),
                    nbt.getInteger(TAG_POS1_Y),
                    nbt.getInteger(TAG_POS1_Z)));
        }

        boolean pending = nbt != null && nbt.getBoolean(TAG_PENDING);
        if (nbt == null || !nbt.getBoolean(TAG_POS2_SET)) {
            list.add(
                StatCollector.translateToLocal(
                    pending ? "horizonqa.wand.tooltip.pos2.pending" : "horizonqa.wand.tooltip.pos2.unset"));
        } else {
            list.add(
                String.format(
                    StatCollector.translateToLocal("horizonqa.wand.tooltip.pos2"),
                    nbt.getInteger(TAG_POS2_X),
                    nbt.getInteger(TAG_POS2_Y),
                    nbt.getInteger(TAG_POS2_Z)));
        }

        if (nbt != null && nbt.getBoolean(TAG_POS1_SET) && nbt.getBoolean(TAG_POS2_SET)) {
            int dx = Math.abs(nbt.getInteger(TAG_POS2_X) - nbt.getInteger(TAG_POS1_X)) + 1;
            int dy = Math.abs(nbt.getInteger(TAG_POS2_Y) - nbt.getInteger(TAG_POS1_Y)) + 1;
            int dz = Math.abs(nbt.getInteger(TAG_POS2_Z) - nbt.getInteger(TAG_POS1_Z)) + 1;
            list.add(String.format(StatCollector.translateToLocal("horizonqa.wand.tooltip.size"), dx, dy, dz));
        }

        list.add(StatCollector.translateToLocal("horizonqa.wand.tooltip.surface_mode"));
    }

    public static NBTTagCompound getOrCreateNBT(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }
}
