package com.gtnewhorizons.gametest.visual;

import java.util.Collection;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.gametest.command.GameTestCommandUtils.CellRecord;
import com.gtnewhorizons.gametest.internal.GameTestInstance;
import com.gtnewhorizons.gametest.internal.GameTestStatus;
import com.gtnewhorizons.gametest.internal.InteractiveTestSession;
import com.gtnewhorizons.gametest.visual.drawables.DebugBeacon;
import com.gtnewhorizons.gametest.visual.drawables.FloatingText;
import com.gtnewhorizons.gametest.visual.drawables.GhostBlockDiff;
import com.gtnewhorizons.gametest.visual.drawables.HighlightBox;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class GameTestOverlayRenderer {

    private static final float[] COL_WHITE = { 1f, 1f, 1f };
    private static final float[] COL_RUNNING = { 0.55f, 0.55f, 0.55f };
    private static final float[] COL_PASSED = { 0.18f, 1.00f, 0.38f };
    private static final float[] COL_FAILED = { 1.00f, 0.16f, 0.16f };
    private static final float[] COL_TIMEOUT = { 1.00f, 0.62f, 0.04f };

    private static final double TEXT_Y_LIFT = 3.0;

    private static final int MAX_CELL_FAILURE_CHARS = 96;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        EntityLivingBase viewer = mc.renderViewEntity instanceof EntityLivingBase ? mc.renderViewEntity : mc.thePlayer;

        float pt = event.partialTicks;
        double camX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * pt;
        double camY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * pt;
        double camZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * pt;
        long wt = mc.theWorld.getTotalWorldTime();

        InteractiveTestSession session = InteractiveTestSession.get();
        Collection<CellRecord> cells = session.getKnownCells();
        if (cells.isEmpty() && VisualManager.getGhosts()
            .isEmpty()) return;

        GL11.glPushMatrix();
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_LINE_BIT
                | GL11.GL_POLYGON_BIT
                | GL11.GL_TEXTURE_BIT
                | GL11.GL_HINT_BIT);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glTranslated(-camX, -camY, -camZ);

        for (CellRecord cell : cells) {
            GameTestInstance inst = session.getLastInstance(cell.testId);
            GameTestStatus status = inst != null ? inst.getStatus() : GameTestStatus.NOT_STARTED;
            if (status == GameTestStatus.NOT_STARTED) continue;

            float[] col = statusColor(status);

            HighlightBox.render(
                cell.minX,
                cell.minY,
                cell.minZ,
                cell.maxX + 1.0,
                cell.maxY + 1.0,
                cell.maxZ + 1.0,
                1.0f,
                1.0f,
                1.0f,
                0.5f);

            double bcx = cell.minX - 0.5;
            double bcy = cell.minY;
            double bcz = cell.minZ - 0.5;
            DebugBeacon.render(bcx, bcy, bcz, col[0], col[1], col[2], pt, wt);
        }

        for (GhostBlockDiff ghost : VisualManager.getGhosts()) {
            ghost.render(pt);
        }

        for (CellRecord cell : cells) {
            GameTestInstance inst = session.getLastInstance(cell.testId);
            GameTestStatus status = inst != null ? inst.getStatus() : GameTestStatus.NOT_STARTED;
            if (status == GameTestStatus.NOT_STARTED) continue;

            double bcx = cell.minX - 0.5;
            double bcz = cell.minZ - 0.5;

            FloatingText.render(bcx, cell.minY + TEXT_Y_LIFT, bcz, buildLines(cell.testId, status, inst), pt);

            if (inst.hasFailPosition()) {
                String failLabel = null;
                if (inst.getFailureCause() != null) {
                    String m = inst.getFailureCause()
                        .getMessage();
                    if (m != null && !m.isEmpty()) {
                        failLabel = m;
                    }
                }
                new GhostBlockDiff(inst.getFailX(), inst.getFailY(), inst.getFailZ(), 1.0f, 0.12f, 0.12f, failLabel)
                    .render(pt);
            }
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private static float[] statusColor(GameTestStatus s) {
        return switch (s) {
            case PASSED -> COL_PASSED;
            case FAILED -> COL_FAILED;
            case TIMED_OUT -> COL_TIMEOUT;
            default -> COL_RUNNING;
        };
    }

    private static String[] buildLines(String testId, GameTestStatus status, GameTestInstance inst) {
        String name = testId.contains(":") ? testId.substring(testId.indexOf(':') + 1) : testId;
        String statusLine = statusLabel(status, inst);

        if (status == GameTestStatus.TIMED_OUT) {
            return new String[] { name, statusLine };
        }

        if (status == GameTestStatus.FAILED && inst != null) {
            Throwable cause = inst.getFailureCause();
            String msg = cause != null ? cause.getMessage() : null;
            if (msg != null) msg = msg.trim();
            boolean hasPos = inst.hasFailPosition();

            if (msg != null && !msg.isEmpty()) {
                String detail = "\u00a7c" + ellipsize(msg, MAX_CELL_FAILURE_CHARS);
                if (hasPos) {
                    return new String[] { name, statusLine, detail,
                        String.format("\u00a78%d %d %d\u00a7r", inst.getFailX(), inst.getFailY(), inst.getFailZ()) };
                }
                return new String[] { name, statusLine, detail };
            }

            String fallback = "\u00a7cNon-assertion error - see log\u00a7r";
            if (hasPos) {
                return new String[] { name, statusLine, fallback,
                    String.format("\u00a78%d %d %d\u00a7r", inst.getFailX(), inst.getFailY(), inst.getFailZ()) };
            }
            return new String[] { name, statusLine, fallback };
        }

        return new String[] { name, statusLine };
    }

    private static String ellipsize(String s, int maxChars) {
        if (s.length() <= maxChars) return s;
        if (maxChars <= 1) return "\u2026";
        return s.substring(0, maxChars - 1) + "\u2026";
    }

    private static String statusLabel(GameTestStatus s, GameTestInstance inst) {
        String base = switch (s) {
            case RUNNING -> "\u00a77RUNNING\u00a7r";
            case PASSED -> "\u00a7aPASSED\u00a7r";
            case FAILED -> "\u00a7cFAILED\u00a7r";
            case TIMED_OUT -> "\u00a76TIMED OUT\u00a7r";
            default -> "\u00a77\u2014\u00a7r";
        };
        if (inst == null) return base;
        if (s == GameTestStatus.RUNNING) {
            int t = inst.getTickCount();
            int lim = inst.getDefinition()
                .getTimeoutTicks();
            return base + String.format(" \u00a78%d/%d t\u00a7r", t, lim);
        }
        if (s == GameTestStatus.TIMED_OUT) {
            int lim = inst.getDefinition()
                .getTimeoutTicks();
            return base + String.format(" \u00a78(after %d t)\u00a7r", lim);
        }
        return base;
    }
}
