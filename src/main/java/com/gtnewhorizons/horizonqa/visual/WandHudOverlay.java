package com.gtnewhorizons.horizonqa.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class WandHudOverlay {

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemHorizonWand)) return;

        NBTTagCompound nbt = held.getTagCompound();
        boolean pos1Set = nbt != null && nbt.getBoolean(ItemHorizonWand.TAG_POS1_SET);
        boolean pos2Set = nbt != null && nbt.getBoolean(ItemHorizonWand.TAG_POS2_SET);
        boolean pending = nbt != null && nbt.getBoolean(ItemHorizonWand.TAG_PENDING);

        boolean lookingAtBlock = mc.objectMouseOver != null
            && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
        boolean surfaceMode = mc.thePlayer.isSneaking() && lookingAtBlock;

        FontRenderer fr = mc.fontRenderer;
        int lineH = fr.FONT_HEIGHT + 2;
        int x = 4, y = 4;

        String modeStr = surfaceMode ? "§eSURFACE" : "§7BLOCK";
        fr.drawStringWithShadow("§7Mode: " + modeStr, x, y, 0xFFFFFF);
        y += lineH;

        if (pos1Set) {
            String coords = nbt.getInteger(ItemHorizonWand.TAG_POS1_X) + ", "
                + nbt.getInteger(ItemHorizonWand.TAG_POS1_Y)
                + ", "
                + nbt.getInteger(ItemHorizonWand.TAG_POS1_Z);
            fr.drawStringWithShadow("§aPos1: §f" + coords, x, y, 0xFFFFFF);
        } else {
            fr.drawStringWithShadow("§7Pos1: §8—", x, y, 0xFFFFFF);
        }
        y += lineH;

        if (pos2Set) {
            String coords = nbt.getInteger(ItemHorizonWand.TAG_POS2_X) + ", "
                + nbt.getInteger(ItemHorizonWand.TAG_POS2_Y)
                + ", "
                + nbt.getInteger(ItemHorizonWand.TAG_POS2_Z);
            fr.drawStringWithShadow("§bPos2: §f" + coords, x, y, 0xFFFFFF);
        } else if (pending) {
            fr.drawStringWithShadow("§bPos2: §eaim & right-click", x, y, 0xFFFFFF);
        } else {
            fr.drawStringWithShadow("§7Pos2: §8—", x, y, 0xFFFFFF);
        }
        y += lineH;

        if (pos1Set && pos2Set) {
            int dx = Math.abs(nbt.getInteger(ItemHorizonWand.TAG_POS2_X) - nbt.getInteger(ItemHorizonWand.TAG_POS1_X))
                + 1;
            int dy = Math.abs(nbt.getInteger(ItemHorizonWand.TAG_POS2_Y) - nbt.getInteger(ItemHorizonWand.TAG_POS1_Y))
                + 1;
            int dz = Math.abs(nbt.getInteger(ItemHorizonWand.TAG_POS2_Z) - nbt.getInteger(ItemHorizonWand.TAG_POS1_Z))
                + 1;
            fr.drawStringWithShadow(
                "§eSize: §f" + dx + "×" + dy + "×" + dz + " §7(" + (dx * dy * dz) + ")",
                x,
                y,
                0xFFFFFF);
        }
    }
}
