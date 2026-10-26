package com.gtnewhorizons.horizonqa.examples.tests;

import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.gtnewhorizons.horizonqa.api.client.ClientWindow;

import cpw.mods.fml.common.Loader;

/** Explicitly selected integration example requiring Offscreen and its SDL lwjgl3ify runtime */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientWindowTests {

    private ClientWindowTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 600)
    public static void offscreenWindowKeepsMainUsable(GameTestHelper helper) {
        requireOffscreen(helper);
        AtomicReference<CounterScreen> main = new AtomicReference<>();
        AtomicReference<CounterScreen> extra = new AtomicReference<>();
        AtomicReference<ClientWindow> window = new AtomicReference<>();
        ClientTarget increment = ClientTarget.of(
            "counter.increment",
            w -> w.screen(CounterScreen.class)
                .target());
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(100)
            .client("open independent counter screens", c -> {
                main.set(new CounterScreen("Main window"));
                extra.set(new CounterScreen("Offscreen window"));
                Minecraft.getMinecraft()
                    .displayGuiScreen(main.get());
                Object opened = open(extra::get);
                c.afterTest(() -> closeIfOpen(opened));
                int id = (Integer) invoke(opened, "getId");
                window.set(c.offscreenWindow(id));
            })
            .step("click main window")
            .click(increment)
            .async(
                "click Offscreen window",
                c -> window.get()
                    .click(0, increment))
            .awaitAsync(
                "Offscreen accepted its click",
                c -> window.get()
                    .run(w -> {
                        if (w.screen() != extra.get() || extra.get().clicks != 1) {
                            throw new AssertionError("Offscreen did not receive exactly one click");
                        }
                    }))
            .async(
                "main screen remained independent",
                c -> c.mainWindow()
                    .run(w -> {
                        if (w.screen() != main.get() || main.get().clicks != 1) {
                            throw new AssertionError("Offscreen input changed the main screen");
                        }
                    }))
            .async(
                "capture main window",
                c -> c.mainWindow()
                    .capture("main-counter"))
            .async(
                "capture Offscreen window",
                c -> window.get()
                    .capture("offscreen-counter"))
            .async(
                "resize Offscreen window",
                c -> window.get()
                    .resize(800, 600))
            .async(
                "click resized Offscreen window",
                c -> window.get()
                    .click(0, increment))
            .awaitAsync(
                "resized screen accepted its click",
                c -> window.get()
                    .run(w -> {
                        if (w.screen() != extra.get() || extra.get().clicks != 2) {
                            throw new AssertionError("Resize lost the screen or routed input incorrectly");
                        }
                    }))
            .async(
                "capture resized Offscreen window",
                c -> window.get()
                    .capture("offscreen-resized"))
            .async(
                "close test-owned Offscreen window",
                c -> window.get()
                    .close())
            .async(
                "main window remains usable",
                c -> c.mainWindow()
                    .click(0, increment))
            .async(
                "verify independent close",
                c -> c.mainWindow()
                    .run(w -> {
                        if (window.get()
                            .isOpen() || w.screen() != main.get()
                            || main.get().clicks != 2) {
                            throw new AssertionError("Closing Offscreen affected the main window");
                        }
                    }))
            .succeed();
    }

    @GameTest(template = "client_smoke", timeoutTicks = 600)
    public static void offscreenDragKeepsInputScoped(GameTestHelper helper) {
        requireOffscreen(helper);
        AtomicReference<CounterScreen> main = new AtomicReference<>();
        AtomicReference<CounterScreen> extra = new AtomicReference<>();
        AtomicReference<ClientWindow> window = new AtomicReference<>();
        AtomicReference<Point> endpoint = new AtomicReference<>();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(100)
            .client("open polling drag fixture beside main screen", c -> {
                main.set(new CounterScreen("Main window"));
                extra.set(new CounterScreen("Offscreen drag"));
                Minecraft.getMinecraft()
                    .displayGuiScreen(main.get());
                Object opened = open(extra::get);
                c.afterTest(() -> closeIfOpen(opened));
                window.set(c.offscreenWindow((Integer) invoke(opened, "getId")));
            })
            .async(
                "drag with window-local polled state",
                c -> window.get()
                    .drag(
                        0,
                        "offscreen.increment",
                        w -> extra.get()
                            .target(),
                        w -> {
                            Point point = new Point(extra.get().width - 30, extra.get().height - 30);
                            endpoint.set(point);
                            return point;
                        },
                        4))
            .client("verify drag frames and independent main input", c -> {
                if (extra.get().heldFrames < 4 || !endpoint.get()
                    .equals(extra.get().releasedAt))
                    throw new AssertionError("Offscreen drag lost held frames or its endpoint");
                if (main.get().clicks != 0 || main.get().pressed || Mouse.isButtonDown(0))
                    throw new AssertionError("Offscreen drag leaked into main input");
            })
            .succeed();
    }

    /** Expected optional failure. The report must retain the exception after the click changes state */
    @GameTest(template = "client_smoke", timeoutTicks = 300, required = false)
    public static void offscreenClickFailureIsReported(GameTestHelper helper) {
        checkHandlerFailure(helper, false);
    }

    /** Expected optional failure. The report must retain the exception after the key changes state */
    @GameTest(template = "client_smoke", timeoutTicks = 300, required = false)
    public static void offscreenKeyFailureIsReported(GameTestHelper helper) {
        checkHandlerFailure(helper, true);
    }

    private static void checkHandlerFailure(GameTestHelper helper, boolean keyboard) {
        requireOffscreen(helper);
        AtomicReference<FailingScreen> screen = new AtomicReference<>();
        AtomicReference<ClientWindow> window = new AtomicReference<>();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(100)
            .client("open throwing Offscreen fixture", c -> {
                screen.set(new FailingScreen());
                Object opened = open(screen::get);
                c.afterTest(() -> closeIfOpen(opened));
                window.set(c.offscreenWindow((Integer) invoke(opened, "getId")));
            })
            .async(
                "dispatch failing handler",
                c -> keyboard ? window.get()
                    .key(Keyboard.KEY_A, 'a')
                    : window.get()
                        .click(
                            0,
                            "throwing button",
                            w -> screen.get()
                                .target()))
            .client(
                "state changed before exception",
                c -> { if (!screen.get().changed) throw new AssertionError("Handler did not run"); })
            .succeed();
    }

    private static void requireOffscreen(GameTestHelper helper) {
        helper.assumeTrue(
            Arrays.stream(
                System.getProperty("horizonqa.tests", "")
                    .split(","))
                .map(String::trim)
                .anyMatch(helper.getTestId()::equals),
            "Select this Offscreen integration test by its exact ID in an SDL client profile");
        if (!Loader.isModLoaded("offscreen")) {
            throw new AssertionError("This selected test requires Offscreen 0.3 and its SDL lwjgl3ify runtime");
        }
    }

    private static final class FailingScreen extends GuiScreen {

        private boolean changed;

        private ClickTarget target() {
            return new ClickTarget(this, new Rectangle(0, 0, width, height), () -> true);
        }

        @Override
        protected void mouseClicked(int x, int y, int button) {
            changed = true;
            throw new IllegalStateException("Intentional Offscreen click failure after mutation");
        }

        @Override
        protected void keyTyped(char character, int key) {
            changed = true;
            throw new IllegalStateException("Intentional Offscreen key failure after mutation");
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }

    private static Object open(Supplier<GuiScreen> screen) {
        try {
            return Class.forName("com.sbancuz.offscreen.api.OffscreenAPI")
                .getMethod("open", Supplier.class)
                .invoke(null, screen);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Offscreen could not open the fixture", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unsupported Offscreen API", e);
        }
    }

    private static Object invoke(Object target, String method) {
        try {
            return target.getClass()
                .getMethod(method)
                .invoke(target);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Offscreen " + method + " failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unsupported Offscreen window API: " + method, e);
        }
    }

    private static void closeIfOpen(Object window) {
        if (!(Boolean) invoke(window, "isOpen")) return;
        try {
            Class.forName("com.sbancuz.offscreen.api.OffscreenAPI")
                .getMethod("close", Class.forName("com.sbancuz.offscreen.window.Window"))
                .invoke(null, window);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Offscreen fixture cleanup failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unsupported Offscreen close API", e);
        }
    }

    private static final class CounterScreen extends GuiScreen {

        private final String title;
        private GuiButton increment;
        private boolean hovered;
        private int clicks;
        private boolean pressed;
        private int heldFrames;
        private Point releasedAt;

        private CounterScreen(String title) {
            this.title = title;
        }

        @Override
        public void initGui() {
            buttonList.clear();
            increment = new GuiButton(0, width / 2 - 60, height / 2, 120, 20, "Increment");
            buttonList.add(increment);
        }

        private ClickTarget target() {
            if (increment == null) return null;
            return new ClickTarget(
                "increment",
                new Rectangle(increment.xPosition, increment.yPosition, increment.width, increment.height),
                () -> hovered);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            if (Mouse.isButtonDown(0) != pressed)
                throw new AssertionError("Polled button state belongs to another window: " + title);
            if (pressed) heldFrames++;
            drawDefaultBackground();
            drawCenteredString(fontRendererObj, title + ": " + clicks, width / 2, height / 2 - 25, 0xFFFFFF);
            hovered = increment.mousePressed(mc, mouseX, mouseY);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        protected void mouseClicked(int x, int y, int button) {
            if (button == 0) {
                if (!Mouse.isButtonDown(0)) throw new AssertionError("Hosted press is absent from polling");
                pressed = true;
            }
            super.mouseClicked(x, y, button);
        }

        @Override
        protected void mouseMovedOrUp(int x, int y, int button) {
            if (button == 0) {
                if (Mouse.isButtonDown(0)) throw new AssertionError("Hosted release is absent from polling");
                pressed = false;
                releasedAt = new Point(x, y);
            }
            super.mouseMovedOrUp(x, y, button);
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (button == increment) clicks++;
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }
}
