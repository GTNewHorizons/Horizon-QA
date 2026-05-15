package com.gtnewhorizons.gametest.visual.drawables;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;

import org.lwjgl.opengl.GL11;

public final class FloatingText {

    private static final float SCALE = 0.025f;
    private static final int PAD = 2;
    private static final double MAX_VIEW_DISTANCE_SQ = 5.0 * 5.0;
    private static final int MAX_LINE_PIXEL_WIDTH = 240;

    private FloatingText() {}

    private static String[] wrapLines(FontRenderer fr, String[] lines) {
        List<String> out = new ArrayList<>(lines.length * 2);
        for (String raw : lines) {
            if (raw == null) continue;
            for (Object chunk : fr.listFormattedStringToWidth(raw, MAX_LINE_PIXEL_WIDTH)) {
                out.add((String) chunk);
            }
        }
        return out.toArray(new String[out.size()]);
    }

    public static void render(double wx, double wy, double wz, String[] lines, float scaleMultiplier,
        float partialTicks) {
        if (lines == null || lines.length == 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        if (view == null) return;
        double camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;
        double dx = wx - camX;
        double dy = wy - camY;
        double dz = wz - camZ;
        if (dx * dx + dy * dy + dz * dz > MAX_VIEW_DISTANCE_SQ) return;

        FontRenderer fr = mc.fontRenderer;
        if (fr == null) return;

        lines = wrapLines(fr, lines);
        if (lines.length == 0) return;

        float s = SCALE * scaleMultiplier;

        int maxW = 0;
        for (String l : lines) {
            int w = fr.getStringWidth(l);
            if (w > maxW) maxW = w;
        }
        int totalH = lines.length * (fr.FONT_HEIGHT + 1) - 1;

        GL11.glPushMatrix();
        GL11.glTranslated(wx, wy, wz);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0f, 1f, 0f);
        GL11.glRotatef(RenderManager.instance.playerViewX, 1f, 0f, 0f);
        GL11.glScalef(-s, -s, s);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int bx0 = -maxW / 2 - PAD;
        int bx1 = maxW / 2 + PAD;
        int by0 = -PAD;
        int by1 = totalH + PAD;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorRGBA_I(0x000000, 96);
        tess.addVertex(bx0, by1, 0.0);
        tess.setColorRGBA_I(0x000000, 96);
        tess.addVertex(bx1, by1, 0.0);
        tess.setColorRGBA_I(0x000000, 96);
        tess.addVertex(bx1, by0, 0.0);
        tess.setColorRGBA_I(0x000000, 96);
        tess.addVertex(bx0, by0, 0.0);
        tess.draw();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int tw = fr.getStringWidth(line);
            fr.drawStringWithShadow(line, -tw / 2, i * (fr.FONT_HEIGHT + 1), 0xFFFFFF);
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    public static void render(double wx, double wy, double wz, String[] lines, float partialTicks) {
        render(wx, wy, wz, lines, 1.0f, partialTicks);
    }
}
