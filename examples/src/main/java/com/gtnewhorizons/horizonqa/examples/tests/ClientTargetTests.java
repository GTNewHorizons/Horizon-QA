package com.gtnewhorizons.horizonqa.examples.tests;

import java.awt.Point;
import java.awt.Rectangle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Exercises target discovery against layout changes through real screen input. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientTargetTests {

    private ClientTargetTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 200)
    public static void followsButtonAfterLayoutChanges(GameTestHelper helper) {
        checkMovingButton(helper, false);
    }

    @GameTest(template = "client_smoke", timeoutTicks = 200)
    public static void clicksWithoutRequiringStationaryBounds(GameTestHelper helper) {
        checkMovingButton(helper, true);
    }

    @GameTest(template = "client_smoke", timeoutTicks = 200)
    public static void waitsForElementToAppear(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(40)
            .withinTicks(80)
            .client(
                "open empty screen",
                c -> Minecraft.getMinecraft()
                    .displayGuiScreen(new AppearingScreen()))
            .click(
                ClientTarget.of(
                    "appearing.save",
                    c -> c.screen(AppearingScreen.class)
                        .target()))
            .client("appearing button received real input", c -> {
                AppearingScreen screen = c.screen(AppearingScreen.class);
                if (!screen.observedMissing || !screen.clicked)
                    throw new AssertionError("Missing target was not awaited");
            })
            .succeed();
    }

    @GameTest(template = "client_smoke", timeoutTicks = 200)
    public static void resolverFailureDoesNotDispatchInput(GameTestHelper helper) {
        IllegalStateException ambiguous = new IllegalStateException("Two controls match settings.save");
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(40)
            .withinTicks(80)
            .client(
                "open screen",
                c -> Minecraft.getMinecraft()
                    .displayGuiScreen(new MovingScreen(false)))
            .async(
                "ambiguous target fails immediately",
                client -> client.click(0, "settings.save", c -> { throw ambiguous; })
                    .handle((ignored, error) -> {
                        if (error != ambiguous)
                            throw new AssertionError("Original resolver failure was not preserved", error);
                        return null;
                    }))
            .client(
                "no input was dispatched",
                c -> {
                    if (c.screen(MovingScreen.class).clicked) throw new AssertionError("Ambiguous target was clicked");
                })
            .succeed();
    }

    private static void checkMovingButton(GameTestHelper helper, boolean continuous) {
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(40)
            .withinTicks(80)
            .client(
                "open changing layout",
                c -> Minecraft.getMinecraft()
                    .displayGuiScreen(new MovingScreen(continuous)))
            .click(
                ClientTarget.of(
                    "changing-layout.save",
                    c -> c.screen(MovingScreen.class)
                        .target()))
            .client("real button accepted click after moving", c -> {
                MovingScreen screen = c.screen(MovingScreen.class);
                if (!screen.moved || !screen.clicked) throw new AssertionError("Moving button was not clicked");
            })
            .succeed();
    }

    private static final class AppearingScreen extends GuiScreen {

        private GuiButton save;
        private boolean observedMissing;
        private boolean clicked;

        private ClickTarget target() {
            if (save == null) {
                observedMissing = true;
                return null;
            }
            return new ClickTarget(
                save,
                new Rectangle(save.xPosition, save.yPosition, save.width, save.height),
                () -> true);
        }

        @Override
        public void updateScreen() {
            // Supply the fixture only after a lookup has observed the missing control.
            if (observedMissing && save == null) {
                save = new GuiButton(0, width / 2 - 50, height / 2, 100, 20, "Save");
                buttonList.add(save);
            }
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (button == save) clicked = true;
        }
    }

    private static final class MovingScreen extends GuiScreen {

        private final boolean continuous;
        private GuiButton save;
        private boolean moved;
        private boolean clicked;
        private int pointerX;
        private int pointerY;

        private MovingScreen(boolean continuous) {
            this.continuous = continuous;
        }

        @Override
        public void initGui() {
            buttonList.clear();
            save = new GuiButton(0, width / 4, height / 2, 100, 20, "Save");
            buttonList.add(save);
        }

        private Point position() {
            return new Point(save.xPosition + save.width / 2, save.yPosition + save.height / 2);
        }

        private ClickTarget target() {
            return new ClickTarget(
                save,
                new Rectangle(save.xPosition, save.yPosition, save.width, save.height),
                this::ready);
        }

        private boolean ready() {
            return moved && pointerX >= save.xPosition
                && pointerX < save.xPosition + save.width
                && pointerY >= save.yPosition
                && pointerY < save.yPosition + save.height;
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            pointerX = mouseX;
            pointerY = mouseY;
            Point center = position();
            if (!moved && mouseX == center.x && mouseY == center.y) {
                save.xPosition = width * 3 / 4 - save.width;
                moved = true;
            } else if (moved && continuous && !clicked) {
                save.xPosition += save.xPosition < width / 2 ? 1 : -1;
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (button != save || !ready()) throw new AssertionError("Click did not reach the current save button");
            clicked = true;
        }
    }
}
