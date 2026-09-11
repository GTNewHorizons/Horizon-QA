package com.gtnewhorizons.horizonqa.examples.tests;

import java.awt.Point;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Real screen lifecycle smoke tests. These holders must never load on the dedicated server. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientSmokeTests {

    private ClientSmokeTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 400)
    public static void clickEscapeAndReopen(GameTestHelper helper) {
        ClientTest client = ClientTest.attach(helper);
        helper.startSequence()
            .thenExecute("prepare real server fixture", () -> helper.setBlock("marker", Blocks.gold_block))
            .thenExecuteAsync("open test screen", 80, () -> client.run(c -> {
                if (c.screen() != null) throw new AssertionError("Previous test left a screen open");
                Minecraft.getMinecraft()
                    .displayGuiScreen(new ParentScreen());
            }))
            .thenExecuteAsync("click through screen input", 40, () -> client.click(0, c -> new Point(30, 30)))
            .thenWaitUntilAsync("child screen updated normally", 40, () -> client.run(c -> {
                ChildScreen screen = c.screen(ChildScreen.class);
                if (screen.updates == 0) throw new AssertionError("Child screen has not received updateScreen");
                if (!screen.released) throw new AssertionError("Child screen did not receive mouse release");
            }))
            .thenExecuteAsync("capture child frame", 40, () -> client.capture("child"))
            .thenExecuteAsync("Escape returns to parent", 40, () -> client.run(ClientTest::escape))
            .thenWaitUntilAsync(
                "parent accepts another interaction",
                40,
                () -> client.run(c -> c.screen(ParentScreen.class)))
            .thenExecuteAsync("reopen child", 40, () -> client.click(0, c -> new Point(30, 30)))
            .thenWaitUntilAsync("child reopened", 40, () -> client.run(c -> c.screen(ChildScreen.class)))
            .thenExecute("verify real server state", () -> helper.assertBlockPresent(Blocks.gold_block, "marker"))
            .thenSucceed();
    }

    @GameTest(template = "client_smoke", timeoutTicks = 200)
    public static void intentionalFailure(GameTestHelper helper) {
        ClientTest client = ClientTest.attach(helper);
        helper.startSequence()
            .thenExecuteAsync(
                "open test screen",
                80,
                () -> client.run(
                    c -> Minecraft.getMinecraft()
                        .displayGuiScreen(new ParentScreen())))
            .thenWaitUntilAsync("intentional missing child", 5, () -> client.run(c -> c.screen(ChildScreen.class)))
            .thenSucceed();
    }

    /** Verifies Shift keyboard events and polled state through a normal screen click. */
    @GameTest(template = "client_smoke", timeoutTicks = 200)
    public static void shiftClickReleasesModifier(GameTestHelper helper) {
        checkShiftClick(helper, false);
    }

    /** A throwing screen callback must not leave the modifier or mouse button held. */
    @GameTest(template = "client_smoke", timeoutTicks = 200)
    public static void shiftClickFailureReleasesModifier(GameTestHelper helper) {
        checkShiftClick(helper, true);
    }

    private static void checkShiftClick(GameTestHelper helper, boolean failOnPress) {
        ClientTest client = ClientTest.attach(helper);
        helper.startSequence()
            .thenExecuteAsync(
                "open modifier screen",
                80,
                () -> client.run(
                    c -> Minecraft.getMinecraft()
                        .displayGuiScreen(new ShiftScreen(failOnPress))))
            .thenExecuteAsync(
                "Shift click through real input",
                40,
                () -> client.shiftClick(0, c -> new Point(30, 30), c -> true)
                    .handle((ignored, error) -> {
                        if (failOnPress != (error != null))
                            throw new AssertionError("Unexpected Shift-click outcome", error);
                        if (error != null && !error.toString()
                            .contains("intentional shift callback failure")) {
                            throw new AssertionError("Wrong Shift-click failure", error);
                        }
                        return null;
                    }))
            .thenExecuteAsync("modifier and mouse released", 40, () -> client.run(c -> {
                ShiftScreen screen = c.screen(ShiftScreen.class);
                if (!screen.pressed) throw new AssertionError("Screen never received the mouse press");
                if (!failOnPress && (!screen.released || !screen.shiftReleased)) {
                    throw new AssertionError("Screen did not receive mouse and Shift release events");
                }
                if (GuiScreen.isShiftKeyDown() || Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Mouse.isButtonDown(0)) {
                    throw new AssertionError("Input remained held after Shift click");
                }
            }))
            .thenSucceed();
    }

    /** In a holder-wide run, proves the intentional failure releases the client before another scenario. */
    @GameTest(template = "client_smoke", timeoutTicks = 400)
    public static void zAfterFailure(GameTestHelper helper) {
        clickEscapeAndReopen(helper);
    }

    private static final class ShiftScreen extends GuiScreen {

        private final boolean failOnPress;
        private boolean shiftPressed;
        private boolean shiftReleased;
        private boolean pressed;
        private boolean released;

        private ShiftScreen(boolean failOnPress) {
            this.failOnPress = failOnPress;
        }

        @Override
        public void initGui() {
            buttonList.add(new GuiButton(0, 20, 20, 120, 20, "Shift click"));
        }

        @Override
        public void handleKeyboardInput() {
            if (Keyboard.getEventKey() == Keyboard.KEY_LSHIFT) {
                if (Keyboard.getEventKeyState()) shiftPressed = true;
                else shiftReleased = true;
            }
            super.handleKeyboardInput();
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (!shiftPressed || !GuiScreen.isShiftKeyDown() || !Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                throw new AssertionError("Shift event and polled state must precede the mouse press");
            }
            pressed = true;
            if (failOnPress) throw new IllegalStateException("intentional shift callback failure");
        }

        @Override
        protected void mouseMovedOrUp(int x, int y, int button) {
            if (button == 0) {
                if (!GuiScreen.isShiftKeyDown()) throw new AssertionError("Shift released before mouse release");
                released = true;
            }
        }
    }

    private static final class ParentScreen extends GuiScreen {

        private int renderedMouseX;
        private int renderedMouseY;

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            renderedMouseX = mouseX;
            renderedMouseY = mouseY;
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        public void initGui() {
            buttonList.add(new GuiButton(0, 20, 20, 120, 20, "Open child"));
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (renderedMouseX != 30 || renderedMouseY != 30) {
                throw new AssertionError(
                    "Click arrived before a normal frame at the target: " + renderedMouseX + "," + renderedMouseY);
            }
            if (!Mouse.isButtonDown(0)) throw new AssertionError("Polled button state is not pressed");
            if (Mouse.getEventButton() != 0 || !Mouse.getEventButtonState()) {
                throw new AssertionError("Mouse event state does not describe a left press");
            }
            mc.displayGuiScreen(new ChildScreen(this));
        }

    }

    private static final class ChildScreen extends GuiScreen {

        private final GuiScreen parent;
        private int updates;
        private boolean released;

        private ChildScreen(GuiScreen parent) {
            this.parent = parent;
        }

        @Override
        public void updateScreen() {
            updates++;
        }

        @Override
        protected void mouseMovedOrUp(int x, int y, int button) {
            if (button == 0) {
                if (Mouse.isButtonDown(0)) throw new AssertionError("Polled mouse state remained pressed");
                released = true;
            }
        }

        @Override
        protected void keyTyped(char character, int key) {
            if (key == Keyboard.KEY_ESCAPE) {
                if (!Keyboard.isKeyDown(key)) throw new AssertionError("Polled Escape state is not pressed");
                mc.displayGuiScreen(parent);
            }
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawRect(0, 0, width, height, 0xff204060);
            drawCenteredString(fontRendererObj, "Horizon-QA rendered child", width / 2, height / 2, 0xffffff);
        }

    }
}
