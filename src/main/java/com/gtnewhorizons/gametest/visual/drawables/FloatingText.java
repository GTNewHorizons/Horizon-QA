package com.gtnewhorizons.gametest.visual.drawables;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;

import org.lwjgl.opengl.GL11;

/**
 * Renders one or more lines of text as a billboard label at a world-space position.
 * Long lines are wrapped to roughly {@link #MAX_LINE_PIXEL_WIDTH} font pixels per line so nothing
 * is clipped at the screen edge. The text always faces the camera regardless of player orientation
 * (yaw + pitch billboard rotation). Depth testing is disabled so failure messages remain readable
 * through terrain without the player needing line-of-sight.
 * Hidden when farther than ten blocks from the view camera (eyes).
 *
 * <p>Must be called while the outer GL matrix has been translated by
 * {@code (-camX, -camY, -camZ)} so that world coordinates work naturally.
 * Pass {@code partialTicks} from the current render phase (e.g.
 * {@link net.minecraftforge.client.event.RenderWorldLastEvent#partialTicks}).
 */
public final class FloatingText {

    /**
     * Converts font-pixel space to world-block space. At 0.025f one font pixel
     * is 1/40th of a block (matches vanilla name-tag scaling).
     */
    private static final float SCALE = 0.025f;
    /** Background quad padding in font pixels. */
    private static final int   PAD   = 2;
    /** Beyond this Euclidean distance from the camera, labels are skipped. */
    private static final double MAX_VIEW_DISTANCE_SQ = 5.0 * 5.0;
    /** Max width per line in font pixels; longer segments wrap to extra lines (MC format codes preserved). */
    private static final int MAX_LINE_PIXEL_WIDTH = 240;

    private FloatingText() {}

    /** Expands caller lines by wrapping overly long segments to fit {@link #MAX_LINE_PIXEL_WIDTH}. */
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

    /**
     * Draw text lines centered at the given world position.
     *
     * @param wx/wy/wz       world-space anchor (text is centered horizontally, top-aligned)
     * @param lines          one or more lines; MC color codes ({@code §a}, {@code §c}, …) accepted
     * @param scaleMultiplier multiplies the base scale (use &lt;1 for small labels, e.g. 0.5f)
     * @param partialTicks   frame interpolation factor for camera position (see class Javadoc)
     */
    public static void render(double wx, double wy, double wz, String[] lines,
            float scaleMultiplier, float partialTicks) {
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
        GL11.glScalef(-s, -s, s);

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

    /**
     * Convenience overload at normal (1×) scale.
     */
    public static void render(double wx, double wy, double wz, String[] lines, float partialTicks) {
        render(wx, wy, wz, lines, 1.0f, partialTicks);
    }
}
