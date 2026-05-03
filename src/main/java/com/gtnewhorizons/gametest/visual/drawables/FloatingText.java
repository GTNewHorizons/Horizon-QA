package com.gtnewhorizons.gametest.visual.drawables;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;

import org.lwjgl.opengl.GL11;

/**
 * Renders one or more lines of text as a billboard label at a world-space position.
 * The text always faces the camera regardless of player orientation (yaw + pitch
 * billboard rotation). Depth testing is disabled so failure messages remain readable
 * through terrain without the player needing line-of-sight.
 *
 * <p>Must be called while the outer GL matrix has been translated by
 * {@code (-camX, -camY, -camZ)} so that world coordinates work naturally.
 */
public final class FloatingText {

    /**
     * Converts font-pixel space to world-block space. At 0.025f one font pixel
     * is 1/40th of a block (matches vanilla name-tag scaling).
     */
    private static final float SCALE = 0.025f;
    /** Background quad padding in font pixels. */
    private static final int   PAD   = 2;

    private FloatingText() {}

    /**
     * Draw text lines centered at the given world position.
     *
     * @param wx/wy/wz world-space anchor (text is centered horizontally, top-aligned)
     * @param lines    one or more lines; MC color codes ({@code §a}, {@code §c}, …) accepted
     */
    public static void render(double wx, double wy, double wz, String[] lines) {
        if (lines == null || lines.length == 0) return;
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        if (fr == null) return;

        // Measure
        int maxW = 0;
        for (String l : lines) {
            int w = fr.getStringWidth(l);
            if (w > maxW) maxW = w;
        }
        int totalH = lines.length * (fr.FONT_HEIGHT + 1) - 1;

        GL11.glPushMatrix();
        // Translate to world-space anchor (outer matrix already has -cam applied)
        GL11.glTranslated(wx, wy, wz);
        // Billboard: yaw then pitch
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0f, 1f, 0f);
        GL11.glRotatef( RenderManager.instance.playerViewX, 1f, 0f, 0f);
        // Scale: negative Y so text is right-side-up from the viewer's perspective
        GL11.glScalef(-SCALE, -SCALE, SCALE);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Semi-transparent dark background quad
        int bx0 = -maxW / 2 - PAD;
        int bx1 =  maxW / 2 + PAD;
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

        // Text
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int tw = fr.getStringWidth(line);
            fr.drawStringWithShadow(line, -tw / 2, i * (fr.FONT_HEIGHT + 1), 0xFFFFFF);
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }
}
