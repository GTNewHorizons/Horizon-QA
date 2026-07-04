package com.gtnewhorizons.horizonqa.visual;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;
import com.gtnewhorizons.horizonqa.visual.drawables.FloatingText;
import com.gtnewhorizons.horizonqa.visual.drawables.HighlightBox;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class WandLabelRenderer {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemHorizonWand)) return;

        Map<String, int[]> labels = ItemHorizonWand.getLabels(held);
        if (labels.isEmpty()) return;

        EntityLivingBase viewer = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        float pt = event.partialTicks;
        double camX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * pt;
        double camY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * pt;
        double camZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * pt;

        GL11.glPushMatrix();
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_LINE_BIT
                | GL11.GL_POLYGON_BIT
                | GL11.GL_TEXTURE_BIT
                | GL11.GL_HINT_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glTranslated(-camX, -camY, -camZ);

        for (Map.Entry<String, int[]> entry : labels.entrySet()) {
            int[] pos = entry.getValue();
            boolean outside = ItemHorizonWand.hasCompleteSelection(held)
                && !ItemHorizonWand.isInsideSelection(held, pos[0], pos[1], pos[2]);
            float r = outside ? 1.0f : 0.15f;
            float g = outside ? 0.20f : 0.75f;
            float b = outside ? 0.15f : 1.0f;
            HighlightBox.render(pos[0], pos[1], pos[2], pos[0] + 1.0, pos[1] + 1.0, pos[2] + 1.0, r, g, b, 0.85f);
            FloatingText.render(
                pos[0] + 0.5,
                pos[1] + 0.75,
                pos[2] + 0.5,
                new String[] { (outside ? "\u00a7c" : "\u00a7b") + entry.getKey() },
                pt);
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }
}
