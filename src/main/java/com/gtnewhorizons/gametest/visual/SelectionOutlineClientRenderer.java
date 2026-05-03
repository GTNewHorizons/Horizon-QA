package com.gtnewhorizons.gametest.visual;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.gametest.item.ItemGameTestWand;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/**
 * Axios-style selection hull: cuboid wireframe via {@link GL11#GL_LINES} with {@link GL11#GL_LINE_SMOOTH}
 * / hint (driver MSAA may still apply); dim ghost pass through blocks, then depth-tested lines.
 * Hull faces in pulsating {@code #c8d1ff}; ghost + depth face passes. Faces are not culled.
 */
public final class SelectionOutlineClientRenderer {

    private static final float FACE_R = 156f / 255f;
    private static final float FACE_G = 168f / 255f;
    private static final float FACE_B = 232f / 255f;

    /**
     * Mean face opacity; final value uses {@link #facePulseModulation} (smoothstep of a sine
     * ramp) so the breath eases at lows and highs instead of moving at a uniform rate.
     */
    private static final float FACE_ALPHA_CENTER = 0.12f;

    /** Half-range of the eased opacity swing (modulation is in roughly [-1, 1]). */
    private static final float FACE_ALPHA_PULSE = 0.10f;

    /** Ghost face pass (depth off): same eased phase as depth faces. */
    private static final float FACE_THROUGH_ALPHA_CENTER = 0.035f;

    private static final float FACE_THROUGH_ALPHA_PULSE = 0.028f;

    /** Sine cycle length in world ticks for face pulse. */
    private static final float FACE_PULSE_PERIOD_TICKS = 60f;

    /** RGB brightness multiplier centre; {@link #FACE_COLOR_PULSE} tracks the same sine as alpha. */
    private static final float FACE_COLOR_CENTER = 1.0f;

    private static final float FACE_COLOR_PULSE = 0.0f;

    /**
     * Expansion past the voxel AABB for wireframe corners; faces add {@link #FACE_OUT_EXTRA}. Polygon
     * offset ({@link #POLY_OFFSET_NEAR_FACE_FACTOR} / {@link #POLY_OFFSET_NEAR_FACE_UNITS}) pushes
     * depth-tested faces in depth so they do not z-fight coplanar terrain.
     */
    private static final double OUT = 0.0045;

    /**
     * Extra expansion for face geometry only — wireframe stays at {@link #OUT}; faces render on a
     * slightly larger shell so quads are not coplanar with neighbour block faces.
     */
    private static final double FACE_OUT_EXTRA = 0.0055;

    /** Screen-space line width for {@link GL11#GL_LINES} wireframe (driver clamp may apply). */
    private static final float WIREFRAME_LINE_WIDTH = 1.2f;

    /** Edges occluded by geometry: drawn first, reads as deep / behind blocks. */
    private static final float EDGE_ALPHA_THROUGH = 0.25f;

    /** Slightly dim white so through-block edges feel recessed vs bright foreground edges. */
    private static final float EDGE_DEEP_R = 0.72f;
    private static final float EDGE_DEEP_G = 0.74f;
    private static final float EDGE_DEEP_B = 0.78f;

    private static final float EDGE_ALPHA_NEAR = 0.45f;

    private static final float EDGE_WHITE_R = 1f;
    private static final float EDGE_WHITE_G = 1f;
    private static final float EDGE_WHITE_B = 1f;

    private static final float POLY_OFFSET_NEAR_FACE_FACTOR = -2.0f;
    private static final float POLY_OFFSET_NEAR_FACE_UNITS = -24f;
    private static final float POLY_OFFSET_WIREFRAME_LINE_FACTOR = -1.5f;
    private static final float POLY_OFFSET_WIREFRAME_LINE_UNITS = -10f;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase viewer = mc.renderViewEntity instanceof EntityLivingBase
            ? mc.renderViewEntity
            : mc.thePlayer;
        if (viewer == null || mc.theWorld == null) return;

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemGameTestWand)) return;

        NBTTagCompound nbt = held.getTagCompound();
        if (nbt == null
            || !nbt.getBoolean(ItemGameTestWand.TAG_POS1_SET)
            || !nbt.getBoolean(ItemGameTestWand.TAG_POS2_SET)) {
            return;
        }

        SelectionBounds bounds = SelectionBounds.fromNBT(nbt);

        float pt = event.partialTicks;
        double vx = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * pt;
        double vy = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * pt;
        double vz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * pt;
        float wtime = mc.theWorld.getTotalWorldTime() + pt;

        GL11.glPushMatrix();
        GL11.glTranslated(-vx, -vy, -vz);
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_POLYGON_BIT | GL11.GL_LINE_BIT | GL11.GL_HINT_BIT);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float breathe = facePulseModulation(wtime, FACE_PULSE_PERIOD_TICKS);
        float colorScale = clamp01(FACE_COLOR_CENTER + FACE_COLOR_PULSE * breathe);

        renderGhostWireframe(bounds);
        renderGhostFaces(bounds, breathe, colorScale);
        renderDepthTestedFaces(bounds, breathe, colorScale);
        renderDepthTestedWireframe(bounds);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    /** Ghost wireframe through blocks: dim white, low opacity (depth off). */
    private static void renderGhostWireframe(SelectionBounds b) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_LINE);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(WIREFRAME_LINE_WIDTH);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA_F(EDGE_DEEP_R, EDGE_DEEP_G, EDGE_DEEP_B, EDGE_ALPHA_THROUGH);
        addTrueWireframeEdges(tess, b.x0, b.y0, b.z0, b.x1, b.y1, b.z1);
        tess.draw();
    }

    /** Ghost faces through blocks (depth still off). */
    private static void renderGhostFaces(SelectionBounds b, float breathe, float colorScale) {
        float alphaThrough = clamp01(FACE_THROUGH_ALPHA_CENTER + FACE_THROUGH_ALPHA_PULSE * breathe);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_QUADS);
        tess.setColorRGBA_F(FACE_R * colorScale, FACE_G * colorScale, FACE_B * colorScale, alphaThrough);
        addHullFacesSolid(tess, b.fx0, b.fy0, b.fz0, b.fx1, b.fy1, b.fz1);
        tess.draw();
    }

    /** Hull faces: depth-tested shell (expanded hull + polygon offset vs z-fighting). */
    private static void renderDepthTestedFaces(SelectionBounds b, float breathe, float colorScale) {
        float alpha = clamp01(FACE_ALPHA_CENTER + FACE_ALPHA_PULSE * breathe);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(POLY_OFFSET_NEAR_FACE_FACTOR, POLY_OFFSET_NEAR_FACE_UNITS);

        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_QUADS);
        tess.setColorRGBA_F(FACE_R * colorScale, FACE_G * colorScale, FACE_B * colorScale, alpha);
        addHullFacesSolid(tess, b.fx0, b.fy0, b.fz0, b.fx1, b.fy1, b.fz1);
        tess.draw();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** White wireframe (depth-tested; line offset vs coplanar terrain). */
    private static void renderDepthTestedWireframe(SelectionBounds b) {
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_LINE);
        GL11.glPolygonOffset(POLY_OFFSET_WIREFRAME_LINE_FACTOR, POLY_OFFSET_WIREFRAME_LINE_UNITS);

        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA_F(EDGE_WHITE_R, EDGE_WHITE_G, EDGE_WHITE_B, EDGE_ALPHA_NEAR);
        addTrueWireframeEdges(tess, b.x0, b.y0, b.z0, b.x1, b.y1, b.z1);
        tess.draw();

        GL11.glDisable(GL11.GL_POLYGON_OFFSET_LINE);
        GL11.glPolygonOffset(0f, 0f);
    }

    /** Pre-computed wireframe and face bounds for the current selection. */
    private static final class SelectionBounds {
        final double x0, y0, z0, x1, y1, z1;
        final double fx0, fy0, fz0, fx1, fy1, fz1;

        SelectionBounds(double x0, double y0, double z0, double x1, double y1, double z1,
                         double fx0, double fy0, double fz0, double fx1, double fy1, double fz1) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0;
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.fx0 = fx0; this.fy0 = fy0; this.fz0 = fz0;
            this.fx1 = fx1; this.fy1 = fy1; this.fz1 = fz1;
        }

        static SelectionBounds fromNBT(NBTTagCompound nbt) {
            int bx1 = nbt.getInteger(ItemGameTestWand.TAG_POS1_X);
            int by1 = nbt.getInteger(ItemGameTestWand.TAG_POS1_Y);
            int bz1 = nbt.getInteger(ItemGameTestWand.TAG_POS1_Z);
            int bx2 = nbt.getInteger(ItemGameTestWand.TAG_POS2_X);
            int by2 = nbt.getInteger(ItemGameTestWand.TAG_POS2_Y);
            int bz2 = nbt.getInteger(ItemGameTestWand.TAG_POS2_Z);

            double minX = Math.min(bx1, bx2);
            double minY = Math.min(by1, by2);
            double minZ = Math.min(bz1, bz2);
            double maxX = Math.max(bx1, bx2) + 1.0;
            double maxY = Math.max(by1, by2) + 1.0;
            double maxZ = Math.max(bz1, bz2) + 1.0;

            return new SelectionBounds(
                minX - OUT, minY - OUT, minZ - OUT,
                maxX + OUT, maxY + OUT, maxZ + OUT,
                minX - OUT - FACE_OUT_EXTRA, minY - OUT - FACE_OUT_EXTRA, minZ - OUT - FACE_OUT_EXTRA,
                maxX + OUT + FACE_OUT_EXTRA, maxY + OUT + FACE_OUT_EXTRA, maxZ + OUT + FACE_OUT_EXTRA);
        }
    }

    /** OpenGL Tessellator color alpha should stay ≥0 and sane; sine can barely exceed ±1 anyway. */
    private static float clamp01(float x) {
        return x <= 0f ? 0f : x >= 1f ? 1f : x;
    }

    /**
     * One period of “breathing” in [-1, 1]: sine lifts to [0, 1], {@link #smoothstep01} eases so
     * opacity lingers near min and max (ease-out into peaks / valleys), then maps back to [-1, 1].
     */
    private static float facePulseModulation(float wtime, float periodTicks) {
        float phase = (float) ((wtime * (Math.PI * 2.0)) / periodTicks);
        float t = 0.5f + 0.5f * (float) Math.sin(phase);
        float eased = smoothstep01(t);
        return eased * 2f - 1f;
    }

    private static float smoothstep01(float x) {
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        return x * x * (3f - 2f * x);
    }

    /**
     * Six solid quads, CCW outward (same hull as legacy textured version — used without UV / without
     * texture bind).
     */
    private static void addHullFacesSolid(Tessellator tess,
        double x0, double y0, double z0, double x1, double y1, double z1) {

        quadSolid(tess, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        quadSolid(tess, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        quadSolid(tess, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quadSolid(tess, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quadSolid(tess, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quadSolid(tess, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
    }

    /**
     * Twelve edges of an axis-aligned box as {@link GL11#GL_LINES} segment pairs (call inside
     * {@link Tessellator#startDrawing(int)} with {@code GL_LINES}).
     */
    private static void addTrueWireframeEdges(Tessellator tess,
        double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        // Bottom face
        tess.addVertex(minX, minY, minZ);
        tess.addVertex(maxX, minY, minZ);
        tess.addVertex(maxX, minY, minZ);
        tess.addVertex(maxX, minY, maxZ);
        tess.addVertex(maxX, minY, maxZ);
        tess.addVertex(minX, minY, maxZ);
        tess.addVertex(minX, minY, maxZ);
        tess.addVertex(minX, minY, minZ);

        // Top face
        tess.addVertex(minX, maxY, minZ);
        tess.addVertex(maxX, maxY, minZ);
        tess.addVertex(maxX, maxY, minZ);
        tess.addVertex(maxX, maxY, maxZ);
        tess.addVertex(maxX, maxY, maxZ);
        tess.addVertex(minX, maxY, maxZ);
        tess.addVertex(minX, maxY, maxZ);
        tess.addVertex(minX, maxY, minZ);

        // Vertical pillars
        tess.addVertex(minX, minY, minZ);
        tess.addVertex(minX, maxY, minZ);
        tess.addVertex(maxX, minY, minZ);
        tess.addVertex(maxX, maxY, minZ);
        tess.addVertex(maxX, minY, maxZ);
        tess.addVertex(maxX, maxY, maxZ);
        tess.addVertex(minX, minY, maxZ);
        tess.addVertex(minX, maxY, maxZ);
    }

    private static void quadSolid(Tessellator tess,
        double ax, double ay, double az,
        double bx, double by, double bz,
        double cx, double cy, double cz,
        double dx, double dy, double dz) {
        tess.addVertex(ax, ay, az);
        tess.addVertex(bx, by, bz);
        tess.addVertex(cx, cy, cz);
        tess.addVertex(dx, dy, dz);
    }
}
