package com.gtnewhorizons.gametest.visual;

import java.util.Collection;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.gametest.command.GameTestCommandUtils.CellRecord;
import com.gtnewhorizons.gametest.core.GameTestInstance;
import com.gtnewhorizons.gametest.core.GameTestStatus;
import com.gtnewhorizons.gametest.core.InteractiveTestSession;
import com.gtnewhorizons.gametest.visual.drawables.DebugBeacon;
import com.gtnewhorizons.gametest.visual.drawables.FloatingText;
import com.gtnewhorizons.gametest.visual.drawables.GhostBlockDiff;
import com.gtnewhorizons.gametest.visual.drawables.HighlightBox;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/**
 * Client-side stateless overlay renderer. Subscribes to {@link RenderWorldLastEvent} and
 * each frame renders:
 *
 * <ol>
 *   <li>A bounding-box wireframe around every known test cell.</li>
 *   <li>A glowing beacon pillar above each cell, color-coded by test status.</li>
 *   <li>Billboarded floating text above the beacon: test name, status with tick progress while
 *       running, and (for failures) an ellipsized assertion summary plus fail coordinates when known;
 *       the red ghost marker keeps the full message.</li>
 *   <li>A red ghost-block at the assertion-failure position when the exception carried
 *       world coordinates.</li>
 *   <li>Any custom {@link GhostBlockDiff} objects submitted through
 *       {@link VisualManager}.</li>
 * </ol>
 *
 * <p>All geometry uses world-space coordinates. One outer {@code glTranslated(-camX, …)}
 * converts them to camera-relative space; the drawables never need the camera position.
 *
 * <p>Registered from {@link com.gtnewhorizons.gametest.ClientProxy#init}.
 */
public final class GameTestOverlayRenderer {

    // ── Status colour palette (R, G, B) ─────────────────────────────────────────────
    private static final float[] COL_WHITE = {1f, 1f, 1f};
    private static final float[] COL_RUNNING = {0.55f, 0.55f, 0.55f}; // gray
    private static final float[] COL_PASSED  = {0.18f, 1.00f, 0.38f}; // green
    private static final float[] COL_FAILED  = {1.00f, 0.16f, 0.16f}; // red
    private static final float[] COL_TIMEOUT = {1.00f, 0.62f, 0.04f}; // orange

    /**
     * Y blocks above the cell ceiling where floating text is anchored.
     * Gives enough clearance so the text does not overlap the beacon start.
     */
    private static final double TEXT_Y_LIFT   = 3.0;

    /** Above-cell failure summary: truncated; full text stays on the ghost {@link FloatingText} at the fail block. */
    private static final int MAX_CELL_FAILURE_CHARS = 96;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        EntityLivingBase viewer = mc.renderViewEntity instanceof EntityLivingBase
            ? mc.renderViewEntity : mc.thePlayer;

        float pt = event.partialTicks;
        double camX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * pt;
        double camY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * pt;
        double camZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * pt;
        long   wt   = mc.theWorld.getTotalWorldTime();

        InteractiveTestSession session = InteractiveTestSession.get();
        Collection<CellRecord> cells = session.getKnownCells();
        if (cells.isEmpty() && VisualManager.getGhosts().isEmpty()) return;

        // ── Set up outer state ──────────────────────────────────────────────────────
        GL11.glPushMatrix();
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_POLYGON_BIT
                | GL11.GL_TEXTURE_BIT | GL11.GL_HINT_BIT);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // One outer translation: world-space coordinates work naturally for all drawables.
        GL11.glTranslated(-camX, -camY, -camZ);

        // ── Per-cell visuals, pass 1: boxes and beacons (write depth) ─────────────────
        for (CellRecord cell : cells) {
            GameTestInstance inst   = session.getLastInstance(cell.testId);
            GameTestStatus   status = inst != null ? inst.getStatus() : GameTestStatus.NOT_STARTED;
            if (status == GameTestStatus.NOT_STARTED) continue;

            float[] col      = statusColor(status);

            // Bounding box wireframe (depth-tested; occluded by closer geometry)
            HighlightBox.render(
                cell.minX,       cell.minY,       cell.minZ,
                cell.maxX + 1.0, cell.maxY + 1.0, cell.maxZ + 1.0,
                1.0f, 1.0f, 1.0f, 0.5f);

            // Beacon pillar above the cell center
            // North-west corner: minX / minZ, beam starts just above the cell floor.
            double bcx = cell.minX - 0.5;
            double bcy = cell.minY;
            double bcz = cell.minZ - 0.5;
            DebugBeacon.render(bcx, bcy, bcz, col[0], col[1], col[2], pt, wt);
        }

        // ── VisualManager ghost-block overlays ──────────────────────────────────────
        for (GhostBlockDiff ghost : VisualManager.getGhosts()) {
            ghost.render(pt); // label (if any) is rendered at small scale inside GhostBlockDiff
        }

        // ── Per-cell visuals, pass 2: floating text + failure ghosts (after beacons) ───────────
        // Rendered after all beacons so beacon geometry never overwrites text pixels.
        for (CellRecord cell : cells) {
            GameTestInstance inst   = session.getLastInstance(cell.testId);
            GameTestStatus   status = inst != null ? inst.getStatus() : GameTestStatus.NOT_STARTED;
            if (status == GameTestStatus.NOT_STARTED) continue;

            double bcx = cell.minX - 0.5;
            double bcz = cell.minZ - 0.5;

            // Floating text label
            FloatingText.render(bcx, cell.minY + TEXT_Y_LIFT, bcz,
                buildLines(cell.testId, status, inst), pt);

            // Ghost block at the assertion-failure coordinate (if available).
            // Label: full failure message (above-cell line is ellipsized summary + coords).
            if (inst.hasFailPosition()) {
                String failLabel = null;
                if (inst.getFailureCause() != null) {
                    String m = inst.getFailureCause().getMessage();
                    if (m != null && !m.isEmpty()) {
                        failLabel = m;
                    }
                }
                new GhostBlockDiff(
                    inst.getFailX(), inst.getFailY(), inst.getFailZ(),
                    1.0f, 0.12f, 0.12f, failLabel).render(pt);
            }
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────

    private static float[] statusColor(GameTestStatus s) {
        return switch (s) {
            case PASSED -> COL_PASSED;
            case FAILED -> COL_FAILED;
            case TIMED_OUT -> COL_TIMEOUT;
            default -> COL_RUNNING;
        };
    }

    private static String[] buildLines(String testId, GameTestStatus status,
            GameTestInstance inst) {
        // Short name: strip "namespace:" prefix for readability
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
                        String.format("\u00a78%d %d %d\u00a7r",
                            inst.getFailX(), inst.getFailY(), inst.getFailZ()) };
                }
                return new String[] { name, statusLine, detail };
            }

            String fallback = "\u00a7cNon-assertion error - see log\u00a7r";
            if (hasPos) {
                return new String[] { name, statusLine, fallback,
                    String.format("\u00a78%d %d %d\u00a7r",
                        inst.getFailX(), inst.getFailY(), inst.getFailZ()) };
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
            int lim = inst.getDefinition().getTimeoutTicks();
            return base + String.format(" \u00a78%d/%d t\u00a7r", t, lim);
        }
        if (s == GameTestStatus.TIMED_OUT) {
            int lim = inst.getDefinition().getTimeoutTicks();
            return base + String.format(" \u00a78(after %d t)\u00a7r", lim);
        }
        return base;
    }
}
