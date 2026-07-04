package com.gtnewhorizons.horizonqa.visual;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;
import com.gtnewhorizons.horizonqa.visual.drawables.FloatingText;
import com.gtnewhorizons.horizonqa.visual.drawables.HighlightBox;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class WandLabelRenderer {

    private static final double TEXT_Y_OFFSET = 1.25;
    private static final double TEXT_MAX_VIEW_DISTANCE = 24.0;
    private static final float BOX_ALPHA_THROUGH = 0.22f;
    private static final float BOX_ALPHA_DEPTH_TESTED = 0.85f;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemHorizonWand)) return;

        Map<String, int[]> labels = ItemHorizonWand.getLabels(held);
        if (labels.isEmpty()) return;

        EntityLivingBase viewer = mc.renderViewEntity instanceof EntityLivingBase ? mc.renderViewEntity : mc.thePlayer;
        float pt = event.partialTicks;
        double camX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * pt;
        double camY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * pt;
        double camZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * pt;
        SelectionBounds selection = SelectionBounds.from(held);

        GL11.glPushMatrix();
        try {
            GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT
                    | GL11.GL_COLOR_BUFFER_BIT
                    | GL11.GL_CURRENT_BIT
                    | GL11.GL_LINE_BIT
                    | GL11.GL_POLYGON_BIT
                    | GL11.GL_TEXTURE_BIT
                    | GL11.GL_HINT_BIT);
            try {
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glTranslated(-camX, -camY, -camZ);

                for (Map.Entry<String, int[]> entry : labels.entrySet()) {
                    renderLabelBox(entry.getValue(), isOutsideSelection(selection, entry.getValue()), true);
                }
                for (Map.Entry<String, int[]> entry : labels.entrySet()) {
                    renderLabelBox(entry.getValue(), isOutsideSelection(selection, entry.getValue()), false);
                }
                for (Map.Entry<String, int[]> entry : labels.entrySet()) {
                    int[] pos = entry.getValue();
                    boolean outside = isOutsideSelection(selection, pos);
                    FloatingText.render(
                        pos[0] + 0.5,
                        pos[1] + TEXT_Y_OFFSET,
                        pos[2] + 0.5,
                        new String[] { (outside ? "\u00a7c" : "\u00a7b") + entry.getKey() },
                        pt,
                        TEXT_MAX_VIEW_DISTANCE);
                }
            } finally {
                GL11.glPopAttrib();
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void renderLabelBox(int[] pos, boolean outside, boolean throughWalls) {
        float r = outside ? 1.0f : 0.15f;
        float g = outside ? 0.20f : 0.75f;
        float b = outside ? 0.15f : 1.0f;
        float alpha = throughWalls ? BOX_ALPHA_THROUGH : BOX_ALPHA_DEPTH_TESTED;
        if (throughWalls) {
            HighlightBox
                .renderThroughWalls(pos[0], pos[1], pos[2], pos[0] + 1.0, pos[1] + 1.0, pos[2] + 1.0, r, g, b, alpha);
        } else {
            HighlightBox.render(pos[0], pos[1], pos[2], pos[0] + 1.0, pos[1] + 1.0, pos[2] + 1.0, r, g, b, alpha);
        }
    }

    private static boolean isOutsideSelection(SelectionBounds selection, int[] pos) {
        return selection != null && !selection.contains(pos[0], pos[1], pos[2]);
    }

    private static final class SelectionBounds {

        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private SelectionBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        private static SelectionBounds from(ItemStack stack) {
            NBTTagCompound nbt = stack != null ? stack.getTagCompound() : null;
            if (nbt == null || !nbt.getBoolean(ItemHorizonWand.TAG_POS1_SET)
                || !nbt.getBoolean(ItemHorizonWand.TAG_POS2_SET)) {
                return null;
            }
            int pos1X = nbt.getInteger(ItemHorizonWand.TAG_POS1_X);
            int pos1Y = nbt.getInteger(ItemHorizonWand.TAG_POS1_Y);
            int pos1Z = nbt.getInteger(ItemHorizonWand.TAG_POS1_Z);
            int pos2X = nbt.getInteger(ItemHorizonWand.TAG_POS2_X);
            int pos2Y = nbt.getInteger(ItemHorizonWand.TAG_POS2_Y);
            int pos2Z = nbt.getInteger(ItemHorizonWand.TAG_POS2_Z);
            return new SelectionBounds(
                Math.min(pos1X, pos2X),
                Math.min(pos1Y, pos2Y),
                Math.min(pos1Z, pos2Z),
                Math.max(pos1X, pos2X),
                Math.max(pos1Y, pos2Y),
                Math.max(pos1Z, pos2Z));
        }

        private boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }
}
