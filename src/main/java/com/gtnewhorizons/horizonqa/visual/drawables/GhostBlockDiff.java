package com.gtnewhorizons.horizonqa.visual.drawables;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

public final class GhostBlockDiff {

    public final int x, y, z;
    public final float r, g, b;
    public final String label;

    private static final float ALPHA = 0.45f;
    private static final double INSET = 0.0045;

    public GhostBlockDiff(int x, int y, int z, float r, float g, float b, String label) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.r = r;
        this.g = g;
        this.b = b;
        this.label = label;
    }

    public void render(float partialTicks) {
        double x0 = x - INSET;
        double y0 = y - INSET;
        double z0 = z - INSET;
        double x1 = x + 1.0 + INSET;
        double y1 = y + 1.0 + INSET;
        double z1 = z + 1.0 + INSET;

        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_POLYGON_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-2.0f, -16.0f);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        face(tess, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0);
        face(tess, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
        face(tess, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        face(tess, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        face(tess, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face(tess, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        tess.draw();
        GL11.glPopAttrib();

        if (label != null && !label.isEmpty()) {
            FloatingText.render(x + 0.5, y + 1.25, z + 0.5, new String[] { label }, 0.5f, partialTicks);
        }
    }

    private void face(Tessellator tess, double ax, double ay, double az, double bx, double by, double bz, double cx,
        double cy, double cz, double dx, double dy, double dz) {
        tess.setColorRGBA_F(r, g, b, ALPHA);
        tess.addVertex(ax, ay, az);
        tess.setColorRGBA_F(r, g, b, ALPHA);
        tess.addVertex(bx, by, bz);
        tess.setColorRGBA_F(r, g, b, ALPHA);
        tess.addVertex(cx, cy, cz);
        tess.setColorRGBA_F(r, g, b, ALPHA);
        tess.addVertex(dx, dy, dz);
    }
}
