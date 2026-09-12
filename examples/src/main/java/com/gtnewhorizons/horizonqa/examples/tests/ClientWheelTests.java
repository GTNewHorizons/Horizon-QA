package com.gtnewhorizons.horizonqa.examples.tests;

import java.awt.Rectangle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Real wheel input follows a moving GUI target and preserves native signed deltas. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientWheelTests {

    private ClientWheelTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void wheelFollowsMovingTargetAndPreservesDelta(GameTestHelper helper) {
        ClientTarget wheelPanel = ClientTarget.of(
            "wheel panel",
            c -> c.screen(WheelScreen.class)
                .target());
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(40)
            .withinTicks(80)
            .client(
                "open moving wheel target",
                c -> Minecraft.getMinecraft()
                    .displayGuiScreen(new WheelScreen()))
            .scroll(wheelPanel, -120)
            .client("one negative wheel event arrived", c -> {
                WheelScreen screen = c.screen(WheelScreen.class);
                if (!screen.moved || screen.events != 1 || screen.lastDelta != -120)
                    throw new AssertionError("Wrong first wheel event");
                assertReleased();
            })
            .scroll(wheelPanel, 240)
            .client("one positive wheel event arrived", c -> {
                WheelScreen screen = c.screen(WheelScreen.class);
                if (screen.events != 2 || screen.lastDelta != 240)
                    throw new AssertionError("Wheel sign or event count changed");
                assertReleased();
            })
            .succeed();
    }

    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void wheelHandlerFailureClearsInputAndAllowsNextScroll(GameTestHelper helper) {
        ClientTarget wheelPanel = ClientTarget.of(
            "wheel panel",
            c -> c.screen(WheelScreen.class)
                .target());
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(40)
            .withinTicks(80)
            .client("open throwing wheel target", c -> {
                WheelScreen screen = new WheelScreen();
                screen.fail = true;
                Minecraft.getMinecraft()
                    .displayGuiScreen(screen);
            })
            .async(
                "wheel handler failure is preserved",
                client -> client.scroll(
                    120,
                    "throwing wheel panel",
                    c -> c.screen(WheelScreen.class)
                        .target())
                    .handle((ignored, error) -> {
                        if (!(error instanceof IllegalStateException)
                            || !"Intentional wheel handler failure".equals(error.getMessage())) {
                            throw new AssertionError("Original wheel failure was not preserved", error);
                        }
                        return null;
                    }))
            .client("failed dispatch cleared input", c -> {
                assertReleased();
                c.screen(WheelScreen.class).fail = false;
            })
            .scroll(wheelPanel, -120)
            .client("recovered wheel event arrived", c -> {
                WheelScreen screen = c.screen(WheelScreen.class);
                if (screen.events != 2 || screen.lastDelta != -120) throw new AssertionError("Scroll did not recover");
                assertReleased();
            })
            .succeed();
    }

    private static void assertReleased() {
        if (Mouse.getDWheel() != 0) throw new AssertionError("Wheel delta remained pending");
        for (int button = 0; button < Mouse.getButtonCount(); button++) {
            if (Mouse.isButtonDown(button)) throw new AssertionError("Wheel dispatch left a mouse button held");
        }
    }

    private static final class WheelScreen extends GuiScreen {

        private Rectangle area;
        private boolean moved;
        private int mouseX;
        private int mouseY;
        private int events;
        private int lastDelta;
        private boolean fail;

        @Override
        public void initGui() {
            area = new Rectangle(width / 4, height / 2, 80, 40);
        }

        private ClickTarget target() {
            return new ClickTarget(this, area, () -> moved && area.contains(mouseX, mouseY));
        }

        @Override
        public void drawScreen(int x, int y, float partialTicks) {
            mouseX = x;
            mouseY = y;
            if (!moved && area.contains(x, y)) {
                area.x = width * 3 / 4 - area.width;
                moved = true;
            }
            drawRect(area.x, area.y, area.x + area.width, area.y + area.height, 0xff204060);
            drawCenteredString(fontRendererObj, "Wheel events: " + events, width / 2, height / 4, 0xffffff);
            super.drawScreen(x, y, partialTicks);
        }

        @Override
        public void handleMouseInput() {
            int delta = Mouse.getEventDWheel();
            if (delta != 0) {
                if (!moved || !area.contains(mouseX, mouseY))
                    throw new AssertionError("Wheel reached stale target coordinates");
                if (Mouse.getEventButton() != -1 || Mouse.getEventButtonState())
                    throw new AssertionError("Wheel event pressed a mouse button");
                events++;
                lastDelta = delta;
                if (fail) throw new IllegalStateException("Intentional wheel handler failure");
                if (Mouse.getDWheel() != delta) throw new AssertionError("Event and polled wheel state disagree");
            }
            super.handleMouseInput();
        }
    }
}
