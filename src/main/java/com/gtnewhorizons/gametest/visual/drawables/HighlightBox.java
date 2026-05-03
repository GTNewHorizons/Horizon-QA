package com.gtnewhorizons.gametest.visual.drawables;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;

import org.lwjgl.opengl.GL11;

/**
 * Renders an axis-aligned wireframe box using {@link GL11#GL_LINES}.
 *
 * <p>Depth testing is enabled with {@link GL11#GL_LEQUAL} against the scene depth buffer
 * (lines do not write depth), so outlines are occluded by solid blocks ahead of them.
 *
 * <p>All vertex coordinates are world-space. The caller is responsible for setting
 * up an outer GL matrix that offsets by the negative camera position before calling
 * this method.
 */
public final class HighlightBox {

    /** Screen-space line width, driver-clamped. */
    private static final float LINE_WIDTH = 1.4f;

    private HighlightBox() {}

    /**
     * Draw a wireframe box from {@code (minX, minY, minZ)} to {@code (maxX, maxY, maxZ)}.
     * The coordinates are the outer block faces (i.e. {@code minX} is already the left
     * face of the leftmost block, {@code maxX + 1} is the right face of the rightmost).
     */
    public static void render(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float alpha) {

        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        if (view == null) return;
        double vx = view.posX, vy = view.posY, vz = view.posZ;
        double nearX = vx < minX ? minX : vx > maxX ? maxX : vx;
        double nearY = vy < minY ? minY : vy > maxY ? maxY : vy;
        double nearZ = vz < minZ ? minZ : vz > maxZ ? maxZ : vz;
        double dx = vx - nearX, dy = vy - nearY, dz = vz - nearZ;
        if (dx * dx + dy * dy + dz * dz > 32.0 * 32.0) return;

        GL11.glPushAttrib(GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
            GL11.glLineWidth(LINE_WIDTH);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            Tessellator tess = Tessellator.instance;
            tess.startDrawing(GL11.GL_LINES);

            // Bottom face
            line(tess, minX, minY, minZ, maxX, minY, minZ, r, g, b, alpha);
            line(tess, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, alpha);
            line(tess, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, alpha);
            line(tess, minX, minY, maxZ, minX, minY, minZ, r, g, b, alpha);
            // Top face
            line(tess, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, alpha);
            line(tess, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, alpha);
            line(tess, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, alpha);
            line(tess, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, alpha);
            // Verticals
            line(tess, minX, minY, minZ, minX, maxY, minZ, r, g, b, alpha);
            line(tess, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, alpha);
            line(tess, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, alpha);
            line(tess, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, alpha);

            tess.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void line(Tessellator tess,
            double ax, double ay, double az, double bx, double by, double bz,
            float r, float g, float b, float alpha) {
        tess.setColorRGBA_F(r, g, b, alpha);
        tess.addVertex(ax, ay, az);
        tess.setColorRGBA_F(r, g, b, alpha);
        tess.addVertex(bx, by, bz);
    }
}
