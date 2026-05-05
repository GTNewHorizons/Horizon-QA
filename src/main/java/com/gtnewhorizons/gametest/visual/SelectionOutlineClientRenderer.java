package com.gtnewhorizons.gametest.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.gametest.item.ItemGameTestWand;

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

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase viewer = mc.renderViewEntity instanceof EntityLivingBase ? mc.renderViewEntity : mc.thePlayer;
        if (viewer == null || mc.theWorld == null) return;

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemGameTestWand)) return;

        NBTTagCompound nbt = held.getTagCompound();
        boolean pos1Set = nbt != null && nbt.getBoolean(ItemGameTestWand.TAG_POS1_SET);
        boolean pending = nbt != null && nbt.getBoolean(ItemGameTestWand.TAG_PENDING);
        boolean pos2Set = nbt != null && nbt.getBoolean(ItemGameTestWand.TAG_POS2_SET);

        if (!pos1Set || (!pending && !pos2Set)) return;

        int bx1 = nbt.getInteger(ItemGameTestWand.TAG_POS1_X);
        int by1 = nbt.getInteger(ItemGameTestWand.TAG_POS1_Y);
        int bz1 = nbt.getInteger(ItemGameTestWand.TAG_POS1_Z);

        int bx2, by2, bz2;
        if (pending) {
            int[] live = getLivePos2(mc.thePlayer);
            bx2 = live[0];
            by2 = live[1];
            bz2 = live[2];
        } else {
            bx2 = nbt.getInteger(ItemGameTestWand.TAG_POS2_X);
            by2 = nbt.getInteger(ItemGameTestWand.TAG_POS2_Y);
            bz2 = nbt.getInteger(ItemGameTestWand.TAG_POS2_Z);
        }

        SelectionBounds bounds = SelectionBounds.fromCoords(bx1, by1, bz1, bx2, by2, bz2);

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

        float breathe = facePulseModulation(wtime, FACE_PULSE_PERIOD_TICKS);
        float colorScale = clamp01(FACE_COLOR_CENTER + FACE_COLOR_PULSE * breathe);

        renderGhostWireframe(bounds);
        renderGhostFaces(bounds, breathe, colorScale);
        renderDepthTestedFaces(bounds, breathe, colorScale);
        renderDepthTestedWireframe(bounds);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

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

    private static int[] getLivePos2(EntityPlayer player) {
        double dist = Minecraft.getMinecraft().playerController.getBlockReachDistance();
        Vec3 start = Vec3
            .createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLookVec();
        Vec3 end = Vec3.createVectorHelper(
            start.xCoord + look.xCoord * dist,
            start.yCoord + look.yCoord * dist,
            start.zCoord + look.zCoord * dist);

        MovingObjectPosition hit = player.worldObj.rayTraceBlocks(start, end);
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return new int[] { hit.blockX, hit.blockY, hit.blockZ };
        } else {
            return new int[] {
                MathHelper.floor_double(end.xCoord),
                MathHelper.floor_double(end.yCoord),
                MathHelper.floor_double(end.zCoord) };
        }
    }

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
        return x <= 0f ? 0f : x >= 1f ? 1f : x;
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
