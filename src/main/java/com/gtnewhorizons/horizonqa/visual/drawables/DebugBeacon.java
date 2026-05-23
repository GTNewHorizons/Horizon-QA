package com.gtnewhorizons.horizonqa.visual.drawables;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

public final class DebugBeacon {

    private static final ResourceLocation BEAM_TEX = new ResourceLocation("textures/entity/beacon_beam.png");

    private static final float HEIGHT = 200.0f;

    private DebugBeacon() {}

    public static void render(double wx, double wy, double wz, float r, float g, float b, float partialTicks,
        long worldTime) {

        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(BEAM_TEX);

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);

        int ri = (int) (r * 255);
        int gi = (int) (g * 255);
        int bi = (int) (b * 255);

        Tessellator tess = Tessellator.instance;

        float animationTime = (float) worldTime + partialTicks;
        float vScrollOffset = -animationTime * 0.2F - (float) MathHelper.floor_float(-animationTime * 0.1F);

        byte b0 = 1;
        double rotationAngle = (double) animationTime * 0.025D * (1.0D - (double) (b0 & 1) * 2.5D);
        double cornerRadius = (double) b0 * 0.2D;
        double c1x = wx + Math.cos(rotationAngle + 2.356194490192345D) * cornerRadius;
        double c1z = wz + Math.sin(rotationAngle + 2.356194490192345D) * cornerRadius;
        double c2x = wx + Math.cos(rotationAngle + (Math.PI / 4.0D)) * cornerRadius;
        double c2z = wz + Math.sin(rotationAngle + (Math.PI / 4.0D)) * cornerRadius;
        double c3x = wx + Math.cos(rotationAngle + 3.9269908169872414D) * cornerRadius;
        double c3z = wz + Math.sin(rotationAngle + 3.9269908169872414D) * cornerRadius;
        double c4x = wx + Math.cos(rotationAngle + 5.497787143782138D) * cornerRadius;
        double c4z = wz + Math.sin(rotationAngle + 5.497787143782138D) * cornerRadius;

        double h = HEIGHT;
        double u0 = 0.0D;
        double u1 = 1.0D;
        double vBot = (double) (-1.0F + vScrollOffset);
        double vTop = h * (0.5D / cornerRadius) + vBot;

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);

        tess.startDrawingQuads();
        tess.setColorRGBA(ri, gi, bi, 32);
        tess.addVertexWithUV(c1x, wy + h, c1z, u1, vTop);
        tess.addVertexWithUV(c1x, wy, c1z, u1, vBot);
        tess.addVertexWithUV(c2x, wy, c2z, u0, vBot);
        tess.addVertexWithUV(c2x, wy + h, c2z, u0, vTop);
        tess.addVertexWithUV(c4x, wy + h, c4z, u1, vTop);
        tess.addVertexWithUV(c4x, wy, c4z, u1, vBot);
        tess.addVertexWithUV(c3x, wy, c3z, u0, vBot);
        tess.addVertexWithUV(c3x, wy + h, c3z, u0, vTop);
        tess.addVertexWithUV(c2x, wy + h, c2z, u1, vTop);
        tess.addVertexWithUV(c2x, wy, c2z, u1, vBot);
        tess.addVertexWithUV(c4x, wy, c4z, u0, vBot);
        tess.addVertexWithUV(c4x, wy + h, c4z, u0, vTop);
        tess.addVertexWithUV(c3x, wy + h, c3z, u1, vTop);
        tess.addVertexWithUV(c3x, wy, c3z, u1, vBot);
        tess.addVertexWithUV(c1x, wy, c1z, u0, vBot);
        tess.addVertexWithUV(c1x, wy + h, c1z, u0, vTop);
        tess.draw();

        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glDepthMask(false);

        double ix0 = wx - 0.3D;
        double ix1 = wx + 0.3D;
        double iz0 = wz - 0.3D;
        double iz1 = wz + 0.3D;
        double vBot2 = (double) (-1.0F + vScrollOffset);
        double vTop2 = h + vBot2;

        tess.startDrawingQuads();
        tess.setColorRGBA(ri, gi, bi, 32);
        tess.addVertexWithUV(ix0, wy + h, iz0, u1, vTop2);
        tess.addVertexWithUV(ix0, wy, iz0, u1, vBot2);
        tess.addVertexWithUV(ix1, wy, iz0, u0, vBot2);
        tess.addVertexWithUV(ix1, wy + h, iz0, u0, vTop2);
        tess.addVertexWithUV(ix1, wy + h, iz1, u1, vTop2);
        tess.addVertexWithUV(ix1, wy, iz1, u1, vBot2);
        tess.addVertexWithUV(ix0, wy, iz1, u0, vBot2);
        tess.addVertexWithUV(ix0, wy + h, iz1, u0, vTop2);
        tess.addVertexWithUV(ix1, wy + h, iz0, u1, vTop2);
        tess.addVertexWithUV(ix1, wy, iz0, u1, vBot2);
        tess.addVertexWithUV(ix1, wy, iz1, u0, vBot2);
        tess.addVertexWithUV(ix1, wy + h, iz1, u0, vTop2);
        tess.addVertexWithUV(ix0, wy + h, iz1, u1, vTop2);
        tess.addVertexWithUV(ix0, wy, iz1, u1, vBot2);
        tess.addVertexWithUV(ix0, wy, iz0, u0, vBot2);
        tess.addVertexWithUV(ix0, wy + h, iz0, u0, vTop2);
        tess.draw();

        GL11.glDepthMask(true);
        GL11.glPopAttrib();
    }
}
