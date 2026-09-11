package com.gtnewhorizons.horizonqa.examples.tests;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.concurrent.CompletionException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Native drag dispatch must retain its press across frames and clear it on every exit. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientDragTests {

    private ClientDragTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void holdsButtonAcrossRenderedMovementAndReleasesAtEndpoint(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(40)
            .client(
                "open drag target",
                c -> Minecraft.getMinecraft()
                    .displayGuiScreen(new DragScreen(false, false)))
            .withinTicks(80)
            .drag(
                ClientTarget.of(
                    "drag handle",
                    c -> c.screen(DragScreen.class)
                        .target()))
            .to(
                c -> c.screen(DragScreen.class)
                    .endpoint())
            .overFrames(4)
            .client("native drag reached plain endpoint", c -> {
                DragScreen screen = c.screen(DragScreen.class);
                if (screen.presses != 1 || screen.releases != 1 || screen.heldFrames < 4 || screen.moves == 0)
                    throw new AssertionError("Drag did not span rendered frames with a single press and release");
                if (!screen.endpoint()
                    .equals(screen.releasedAt)) throw new AssertionError("Wrong release point");
                assertReleased();
            })
            .succeed();
    }

    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void handlerFailureReleasesButton(GameTestHelper helper) {
        checkInterruptedDrag(helper, false);
    }

    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void replacementScreenDoesNotInheritHeldButton(GameTestHelper helper) {
        checkInterruptedDrag(helper, true);
    }

    private static void checkInterruptedDrag(GameTestHelper helper, boolean replaceScreen) {
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(40)
            .client(
                "open interrupted drag target",
                c -> Minecraft.getMinecraft()
                    .displayGuiScreen(new DragScreen(!replaceScreen, replaceScreen)))
            .withinTicks(80)
            .async(
                "drag interruption is reported",
                client -> client.drag(
                    0,
                    "interrupted handle",
                    c -> c.screen(DragScreen.class)
                        .target(),
                    c -> c.screen(DragScreen.class)
                        .endpoint(),
                    4)
                    .handle((ignored, error) -> {
                        Throwable cause = error;
                        while (cause instanceof CompletionException) cause = cause.getCause();
                        String expected = replaceScreen ? "Screen changed during drag"
                            : "Intentional drag handler failure";
                        if (!(cause instanceof IllegalStateException) || !expected.equals(cause.getMessage()))
                            throw new AssertionError("Original drag interruption was not preserved", error);
                        return null;
                    }))
            .client("input cleared before next action", c -> {
                assertReleased();
                Minecraft.getMinecraft()
                    .displayGuiScreen(new DragScreen(false, false));
            })
            .withinTicks(80)
            .drag(
                ClientTarget.of(
                    "recovered handle",
                    c -> c.screen(DragScreen.class)
                        .target()))
            .to(
                c -> c.screen(DragScreen.class)
                    .endpoint())
            .overFrames(2)
            .client("recovered drag released input", c -> assertReleased())
            .succeed();
    }

    private static void assertReleased() {
        if (Mouse.isButtonDown(0)) throw new AssertionError("Drag left the mouse button held");
    }

    private static final class DragScreen extends GuiScreen {

        private final boolean fail;
        private final boolean replace;
        private final Rectangle handle = new Rectangle(30, 30, 60, 30);
        private int renderedX;
        private int renderedY;
        private int presses;
        private int releases;
        private int heldFrames;
        private int moves;
        private boolean interrupted;
        private Point releasedAt;

        private DragScreen(boolean fail, boolean replace) {
            this.fail = fail;
            this.replace = replace;
        }

        private ClickTarget target() {
            return new ClickTarget(this, handle, () -> handle.contains(renderedX, renderedY));
        }

        private Point endpoint() {
            return new Point(width - 40, height - 40);
        }

        @Override
        public void drawScreen(int x, int y, float partialTicks) {
            renderedX = x;
            renderedY = y;
            if (presses > 0 && releases == 0 && !interrupted) {
                if (!Mouse.isButtonDown(0)) throw new AssertionError("Polled drag state was lost between frames");
                heldFrames++;
            }
            drawRect(handle.x, handle.y, handle.x + handle.width, handle.y + handle.height, 0xff204060);
            super.drawScreen(x, y, partialTicks);
        }

        @Override
        protected void mouseClicked(int x, int y, int button) {
            if (button != 0 || !handle.contains(x, y) || !Mouse.isButtonDown(button))
                throw new AssertionError("Drag started outside its ready handle");
            presses++;
        }

        @Override
        protected void mouseClickMove(int x, int y, int button, long elapsed) {
            if (button != 0 || !Mouse.isButtonDown(button)) throw new AssertionError("Drag motion lost its press");
            moves++;
            if (fail) {
                interrupted = true;
                throw new IllegalStateException("Intentional drag handler failure");
            }
            if (replace) {
                mc.displayGuiScreen(new GuiScreen() {});
                assertReleased();
            }
        }

        @Override
        protected void mouseMovedOrUp(int x, int y, int button) {
            if (button == 0) {
                assertReleased();
                releases++;
                releasedAt = new Point(x, y);
            }
        }
    }
}
