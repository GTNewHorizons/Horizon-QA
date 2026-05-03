package com.gtnewhorizons.gametest.visual.drawables;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

/**
 * Immutable descriptor for one translucent "ghost block" overlay, used to highlight
 * assertion-failure positions (red) or expected block positions (green) without
 * physically placing any blocks in the world.
 *
 * <p>Call {@link #render(float)} while the outer GL matrix is translated by
 * {@code (-camX, -camY, -camZ)} so that the stored world coordinates work directly.
 */
public final class GhostBlockDiff {

    /** World-space block coordinates of the ghost. */
    public final int x, y, z;
    /** Normalised RGB fill color [0..1]. */
    public final float r, g, b;
    /**
     * Optional label (e.g. failure reason) drawn above this ghost via {@link FloatingText}
     * at half scale; long strings wrap instead of clipping. {@code null} = no label.
     */
    public final String label;

    private static final float ALPHA = 0.45f;
    /** Inset on every axis so all six faces sit inside the voxel, away from neighboring block skins. */
    private static final double INSET = 0.0045;

    public GhostBlockDiff(int x, int y, int z, float r, float g, float b, String label) {
        this.x = x; this.y = y; this.z = z;
        this.r = r; this.g = g; this.b = b;
        this.label = label;
    }

    /**
     * Draw this ghost block into the current GL context (outer camera-offset matrix assumed).
     *
     * @param partialTicks same as {@link FloatingText} / {@link net.minecraftforge.client.event.RenderWorldLastEvent#partialTicks}
     */
    public void render(float partialTicks) {
        double x0 = x - INSET;
        double y0 = y - INSET;
        double z0 = z - INSET;
        double x1 = x + 1.0 + INSET;
        double y1 = y + 1.0 + INSET;
        double z1 = z + 1.0 + INSET;

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-2.0f, -16.0f);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        // +X
        face(tess, x1,y0,z1, x1,y1,z1, x1,y1,z0, x1,y0,z0);
        // -X
        face(tess, x0,y0,z0, x0,y1,z0, x0,y1,z1, x0,y0,z1);
        // +Y
        face(tess, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0);
        // -Y
        face(tess, x0,y0,z1, x0,y0,z0, x1,y0,z0, x1,y0,z1);
        // +Z
        face(tess, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1);
        // -Z
        face(tess, x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0);
        tess.draw();

        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0f, 0f);
        GL11.glEnable(GL11.GL_CULL_FACE);

        // Label above voxel: FloatingText wraps long lines (same behaviour as cell labels).
        if (label != null && !label.isEmpty()) {
            FloatingText.render(x + 0.5, y + 1.25, z + 0.5, new String[] { label }, 0.5f, partialTicks);
        }
    }

    private void face(Tessellator tess,
            double ax, double ay, double az, double bx, double by, double bz,
            double cx, double cy, double cz, double dx, double dy, double dz) {
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
