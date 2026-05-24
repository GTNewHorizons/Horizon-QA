package com.gtnewhorizons.horizonqa.visual;

import java.util.Arrays;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class SelectionOutlineClientRenderer {

    private static final float FACE_R = 156f / 255f;
    private static final float FACE_G = 168f / 255f;
    private static final float FACE_B = 232f / 255f;

    private static final float FACE_ALPHA_CENTER = 0.12f;
    private static final float FACE_ALPHA_PULSE = 0.10f;

    private static final float FACE_THROUGH_ALPHA_CENTER = 0.035f;
    private static final float FACE_THROUGH_ALPHA_PULSE = 0.028f;

    private static final float FACE_PULSE_PERIOD_TICKS = 60f;

    private static final float FACE_COLOR_CENTER = 1.0f;
    private static final float FACE_COLOR_PULSE = 0.0f;

    private static final double OUT = 0.0045;
    private static final double FACE_OUT_EXTRA = 0.0055;

    private static final float WIREFRAME_LINE_WIDTH = 1.2f;

    private static final float EDGE_ALPHA_THROUGH = 0.25f;

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

    private static final double AXIS_EXTENT = 32.0;
    private static final double AXIS_FADE_LENGTH = 8.0;
    private static final float AXIS_ALPHA_NEAR = 0.25f;

    private static final float TARGET_ALPHA_NEAR = 0.35f;
    private static final float TARGET_ALPHA_GHOST = 0.15f;
    private static final double TARGET_GLYPH_LEN = 0.3;

    private static final double CORNER_HALF_SIZE = 0.075;
    private static final float CORNER_BASE_ALPHA = 0.65f;
    private static final float CORNER_PULSE_ALPHA = 0.35f;
    private static final long CORNER_PULSE_DURATION_MS = 1000L;

    // corner pulse state
    private int[] lastPos1 = null;
    private int[] lastPos2 = null;
    private long pos1PulseEnd = 0L;
    private long pos2PulseEnd = 0L;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase viewer = mc.renderViewEntity instanceof EntityLivingBase ? mc.renderViewEntity : mc.thePlayer;
        if (viewer == null || mc.theWorld == null) return;

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemHorizonWand)) return;

        NBTTagCompound nbt = held.getTagCompound();
        boolean pos1Set = nbt != null && nbt.getBoolean(ItemHorizonWand.TAG_POS1_SET);
        boolean pending = nbt != null && nbt.getBoolean(ItemHorizonWand.TAG_PENDING);
        boolean pos2Set = nbt != null && nbt.getBoolean(ItemHorizonWand.TAG_POS2_SET);

        // Update corner pulse state
        if (pos1Set) {
            int[] p1 = { nbt.getInteger(ItemHorizonWand.TAG_POS1_X), nbt.getInteger(ItemHorizonWand.TAG_POS1_Y),
                nbt.getInteger(ItemHorizonWand.TAG_POS1_Z) };
            if (lastPos1 == null || !Arrays.equals(lastPos1, p1)) {
                lastPos1 = p1;
                pos1PulseEnd = System.currentTimeMillis() + CORNER_PULSE_DURATION_MS;
            }
        } else {
            lastPos1 = null;
        }
        if (pos2Set) {
            int[] p2 = { nbt.getInteger(ItemHorizonWand.TAG_POS2_X), nbt.getInteger(ItemHorizonWand.TAG_POS2_Y),
                nbt.getInteger(ItemHorizonWand.TAG_POS2_Z) };
            if (lastPos2 == null || !Arrays.equals(lastPos2, p2)) {
                lastPos2 = p2;
                pos2PulseEnd = System.currentTimeMillis() + CORNER_PULSE_DURATION_MS;
            }
        } else {
            lastPos2 = null;
        }

        float pulse1 = cornerPulseAlpha(pos1PulseEnd);
        float pulse2 = cornerPulseAlpha(pos2PulseEnd);

        int[] wandTarget = ItemHorizonWand.getTargetedPosition(mc.thePlayer);

        float pt = event.partialTicks;
        double vx = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * pt;
        double vy = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * pt;
        double vz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * pt;
        float wtime = mc.theWorld.getTotalWorldTime() + pt;

        GL11.glPushMatrix();
        GL11.glTranslated(-vx, -vy, -vz);
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_POLYGON_BIT
                | GL11.GL_LINE_BIT
                | GL11.GL_HINT_BIT);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Axis cross: hide once both corners are confirmed
        if (!(pos1Set && pos2Set)) {
            renderWandAxis(wandTarget[0] + 0.5, wandTarget[1] + 0.5, wandTarget[2] + 0.5);
        }

        // Target indicator: always shown while holding wand
        renderTargetIndicator(wandTarget, pending);

        if (pos1Set && (pending || pos2Set)) {
            int bx1 = nbt.getInteger(ItemHorizonWand.TAG_POS1_X);
            int by1 = nbt.getInteger(ItemHorizonWand.TAG_POS1_Y);
            int bz1 = nbt.getInteger(ItemHorizonWand.TAG_POS1_Z);

            int bx2, by2, bz2;
            if (pending) {
                bx2 = wandTarget[0];
                by2 = wandTarget[1];
                bz2 = wandTarget[2];
            } else {
                bx2 = nbt.getInteger(ItemHorizonWand.TAG_POS2_X);
                by2 = nbt.getInteger(ItemHorizonWand.TAG_POS2_Y);
                bz2 = nbt.getInteger(ItemHorizonWand.TAG_POS2_Z);
            }

            SelectionBounds bounds = SelectionBounds.fromCoords(bx1, by1, bz1, bx2, by2, bz2);

            float breathe = facePulseModulation(wtime, FACE_PULSE_PERIOD_TICKS);
            float colorScale = clamp01(FACE_COLOR_CENTER + FACE_COLOR_PULSE * breathe);

            renderGhostWireframe(bounds);
            renderGhostFaces(bounds, breathe, colorScale);
            renderDepthTestedFaces(bounds, breathe, colorScale);
            renderDepthTestedWireframe(bounds);
        }

        // Corner markers for confirmed pos1 / pos2
        renderCornerMarkers(nbt, pos1Set, pos2Set, pulse1, pulse2);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    // ---- Wand axis cross ----

    private static void renderWandAxis(double cx, double cy, double cz) {
        renderWandAxisGhost(cx, cy, cz);
        renderWandAxisDepthTested(cx, cy, cz);
    }

    private static void renderWandAxisGhost(double cx, double cy, double cz) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_LINE);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(WIREFRAME_LINE_WIDTH);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);
        addAxisLinesWithFade(
            tess,
            cx,
            cy,
            cz,
            AXIS_EXTENT,
            AXIS_FADE_LENGTH,
            EDGE_DEEP_R,
            EDGE_DEEP_G,
            EDGE_DEEP_B,
            EDGE_ALPHA_THROUGH);
        tess.draw();
    }

    private static void renderWandAxisDepthTested(double cx, double cy, double cz) {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_LINE);
        GL11.glPolygonOffset(POLY_OFFSET_WIREFRAME_LINE_FACTOR, POLY_OFFSET_WIREFRAME_LINE_UNITS);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(WIREFRAME_LINE_WIDTH);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);
        addAxisLinesWithFade(
            tess,
            cx,
            cy,
            cz,
            AXIS_EXTENT,
            AXIS_FADE_LENGTH,
            EDGE_WHITE_R,
            EDGE_WHITE_G,
            EDGE_WHITE_B,
            AXIS_ALPHA_NEAR);
        tess.draw();

        GL11.glDisable(GL11.GL_POLYGON_OFFSET_LINE);
        GL11.glPolygonOffset(0f, 0f);
    }

    private static void addAxisLinesWithFade(Tessellator tess, double cx, double cy, double cz, double extent,
        double fade, float r, float g, float b, float alpha) {
        double solid = extent - fade;
        // X axis
        addGradientLine(tess, cx, cy, cz, cx - solid, cy, cz, r, g, b, alpha, r, g, b, alpha);
        addGradientLine(tess, cx - solid, cy, cz, cx - extent, cy, cz, r, g, b, alpha, r, g, b, 0f);
        addGradientLine(tess, cx, cy, cz, cx + solid, cy, cz, r, g, b, alpha, r, g, b, alpha);
        addGradientLine(tess, cx + solid, cy, cz, cx + extent, cy, cz, r, g, b, alpha, r, g, b, 0f);
        // Y axis
        addGradientLine(tess, cx, cy, cz, cx, cy - solid, cz, r, g, b, alpha, r, g, b, alpha);
        addGradientLine(tess, cx, cy - solid, cz, cx, cy - extent, cz, r, g, b, alpha, r, g, b, 0f);
        addGradientLine(tess, cx, cy, cz, cx, cy + solid, cz, r, g, b, alpha, r, g, b, alpha);
        addGradientLine(tess, cx, cy + solid, cz, cx, cy + extent, cz, r, g, b, alpha, r, g, b, 0f);
        // Z axis
        addGradientLine(tess, cx, cy, cz - solid, cx, cy, cz, r, g, b, alpha, r, g, b, alpha);
        addGradientLine(tess, cx, cy, cz - extent, cx, cy, cz - solid, r, g, b, 0f, r, g, b, alpha);
        addGradientLine(tess, cx, cy, cz, cx, cy, cz + solid, r, g, b, alpha, r, g, b, alpha);
        addGradientLine(tess, cx, cy, cz + solid, cx, cy, cz + extent, r, g, b, alpha, r, g, b, 0f);
    }

    private static void addGradientLine(Tessellator tess, double ax, double ay, double az, double bx, double by,
        double bz, float r0, float g0, float b0, float a0, float r1, float g1, float b1, float a1) {
        tess.setColorRGBA_F(r0, g0, b0, a0);
        tess.addVertex(ax, ay, az);
        tess.setColorRGBA_F(r1, g1, b1, a1);
        tess.addVertex(bx, by, bz);
    }

    // ---- Target indicator ----

    private static void renderTargetIndicator(int[] target, boolean pending) {
        double tx = target[0], ty = target[1], tz = target[2];
        double x0 = tx - OUT, y0 = ty - OUT, z0 = tz - OUT;
        double x1 = tx + 1 + OUT, y1 = ty + 1 + OUT, z1 = tz + 1 + OUT;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(1.0f);

        // Ghost pass (through walls)
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_LINE);

        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA_F(1f, 1f, 1f, TARGET_ALPHA_GHOST);
        addTrueWireframeEdges(tess, x0, y0, z0, x1, y1, z1);
        tess.draw();

        // Depth-tested outline
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_LINE);
        GL11.glPolygonOffset(POLY_OFFSET_WIREFRAME_LINE_FACTOR, POLY_OFFSET_WIREFRAME_LINE_UNITS);

        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA_F(1f, 1f, 1f, TARGET_ALPHA_NEAR);
        addTrueWireframeEdges(tess, x0, y0, z0, x1, y1, z1);
        tess.draw();

        // Corner glyph: 3 arms from min corner, coloured by which pos is next
        float gr, gg, gb;
        if (pending) {
            // next click sets pos2 → aqua
            gr = 0.33f;
            gg = 1f;
            gb = 1f;
        } else {
            // next click sets pos1 → green
            gr = 0.33f;
            gg = 1f;
            gb = 0.33f;
        }

        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA_F(gr, gg, gb, 0.9f);
        tess.addVertex(tx, ty, tz);
        tess.addVertex(tx + TARGET_GLYPH_LEN, ty, tz);
        tess.addVertex(tx, ty, tz);
        tess.addVertex(tx, ty + TARGET_GLYPH_LEN, tz);
        tess.addVertex(tx, ty, tz);
        tess.addVertex(tx, ty, tz + TARGET_GLYPH_LEN);
        tess.draw();

        GL11.glDisable(GL11.GL_POLYGON_OFFSET_LINE);
        GL11.glPolygonOffset(0f, 0f);
    }

    // ---- Corner markers ----

    private static void renderCornerMarkers(NBTTagCompound nbt, boolean pos1Set, boolean pos2Set, float pulse1,
        float pulse2) {
        if (!pos1Set && !pos2Set) return;

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);

        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_QUADS);

        if (pos1Set) {
            float a = clamp01(CORNER_BASE_ALPHA + CORNER_PULSE_ALPHA * pulse1);
            double cx = nbt.getInteger(ItemHorizonWand.TAG_POS1_X) + 0.5;
            double cy = nbt.getInteger(ItemHorizonWand.TAG_POS1_Y) + 0.5;
            double cz = nbt.getInteger(ItemHorizonWand.TAG_POS1_Z) + 0.5;
            tess.setColorRGBA_F(0.33f, 1f, 0.33f, a);
            addHullFacesSolid(
                tess,
                cx - CORNER_HALF_SIZE,
                cy - CORNER_HALF_SIZE,
                cz - CORNER_HALF_SIZE,
                cx + CORNER_HALF_SIZE,
                cy + CORNER_HALF_SIZE,
                cz + CORNER_HALF_SIZE);
        }
        if (pos2Set) {
            float a = clamp01(CORNER_BASE_ALPHA + CORNER_PULSE_ALPHA * pulse2);
            double cx = nbt.getInteger(ItemHorizonWand.TAG_POS2_X) + 0.5;
            double cy = nbt.getInteger(ItemHorizonWand.TAG_POS2_Y) + 0.5;
            double cz = nbt.getInteger(ItemHorizonWand.TAG_POS2_Z) + 0.5;
            tess.setColorRGBA_F(0.33f, 1f, 1f, a);
            addHullFacesSolid(
                tess,
                cx - CORNER_HALF_SIZE,
                cy - CORNER_HALF_SIZE,
                cz - CORNER_HALF_SIZE,
                cx + CORNER_HALF_SIZE,
                cy + CORNER_HALF_SIZE,
                cz + CORNER_HALF_SIZE);
        }

        tess.draw();
    }

    private static float cornerPulseAlpha(long pulseEnd) {
        long remaining = pulseEnd - System.currentTimeMillis();
        if (remaining <= 0) return 0f;
        float t = remaining / (float) CORNER_PULSE_DURATION_MS;
        return t * t;
    }

    // ---- Selection box rendering ----

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

    // ---- Geometry helpers ----

    private static final class SelectionBounds {

        final double x0, y0, z0, x1, y1, z1;
        final double fx0, fy0, fz0, fx1, fy1, fz1;

        SelectionBounds(double x0, double y0, double z0, double x1, double y1, double z1, double fx0, double fy0,
            double fz0, double fx1, double fy1, double fz1) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.fx0 = fx0;
            this.fy0 = fy0;
            this.fz0 = fz0;
            this.fx1 = fx1;
            this.fy1 = fy1;
            this.fz1 = fz1;
        }

        static SelectionBounds fromCoords(int bx1, int by1, int bz1, int bx2, int by2, int bz2) {
            double minX = Math.min(bx1, bx2);
            double minY = Math.min(by1, by2);
            double minZ = Math.min(bz1, bz2);
            double maxX = Math.max(bx1, bx2) + 1.0;
            double maxY = Math.max(by1, by2) + 1.0;
            double maxZ = Math.max(bz1, bz2) + 1.0;

            return new SelectionBounds(
                minX - OUT,
                minY - OUT,
                minZ - OUT,
                maxX + OUT,
                maxY + OUT,
                maxZ + OUT,
                minX - OUT - FACE_OUT_EXTRA,
                minY - OUT - FACE_OUT_EXTRA,
                minZ - OUT - FACE_OUT_EXTRA,
                maxX + OUT + FACE_OUT_EXTRA,
                maxY + OUT + FACE_OUT_EXTRA,
                maxZ + OUT + FACE_OUT_EXTRA);
        }
    }

    private static float clamp01(float x) {
        return x <= 0f ? 0f : Math.min(x, 1f);
    }

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

    private static void addHullFacesSolid(Tessellator tess, double x0, double y0, double z0, double x1, double y1,
        double z1) {
        quadSolid(tess, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        quadSolid(tess, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        quadSolid(tess, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quadSolid(tess, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quadSolid(tess, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quadSolid(tess, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
    }

    private static void addTrueWireframeEdges(Tessellator tess, double minX, double minY, double minZ, double maxX,
        double maxY, double maxZ) {
        tess.addVertex(minX, minY, minZ);
        tess.addVertex(maxX, minY, minZ);
        tess.addVertex(maxX, minY, minZ);
        tess.addVertex(maxX, minY, maxZ);
        tess.addVertex(maxX, minY, maxZ);
        tess.addVertex(minX, minY, maxZ);
        tess.addVertex(minX, minY, maxZ);
        tess.addVertex(minX, minY, minZ);

        tess.addVertex(minX, maxY, minZ);
        tess.addVertex(maxX, maxY, minZ);
        tess.addVertex(maxX, maxY, minZ);
        tess.addVertex(maxX, maxY, maxZ);
        tess.addVertex(maxX, maxY, maxZ);
        tess.addVertex(minX, maxY, maxZ);
        tess.addVertex(minX, maxY, maxZ);
        tess.addVertex(minX, maxY, minZ);

        tess.addVertex(minX, minY, minZ);
        tess.addVertex(minX, maxY, minZ);
        tess.addVertex(maxX, minY, minZ);
        tess.addVertex(maxX, maxY, minZ);
        tess.addVertex(maxX, minY, maxZ);
        tess.addVertex(maxX, maxY, maxZ);
        tess.addVertex(minX, minY, maxZ);
        tess.addVertex(minX, maxY, maxZ);
    }

    private static void quadSolid(Tessellator tess, double ax, double ay, double az, double bx, double by, double bz,
        double cx, double cy, double cz, double dx, double dy, double dz) {
        tess.addVertex(ax, ay, az);
        tess.addVertex(bx, by, bz);
        tess.addVertex(cx, cy, cz);
        tess.addVertex(dx, dy, dz);
    }
}
