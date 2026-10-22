package com.gtnewhorizons.horizonqa.examples.tests;

import java.awt.Rectangle;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.gtnewhorizons.horizonqa.api.client.ClientWindow;

import cpw.mods.fml.common.Loader;

/** Explicitly selected integration example requiring Offscreen and its SDL lwjgl3ify runtime. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientWindowTests {

    private ClientWindowTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 600)
    public static void offscreenWindowKeepsMainUsable(GameTestHelper helper) {
        helper.assumeTrue(
            helper.getTestId()
                .equals(System.getProperty("horizonqa.tests")),
            "Select this Offscreen integration test by its exact ID in an SDL client profile");
        if (!Loader.isModLoaded("offscreen")) {
            throw new AssertionError("This selected test requires Offscreen 0.3 and its SDL lwjgl3ify runtime");
        }
        AtomicReference<CounterScreen> main = new AtomicReference<>();
        AtomicReference<CounterScreen> extra = new AtomicReference<>();
        AtomicReference<ClientWindow> window = new AtomicReference<>();
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
            .async(
                "click main window",
                c -> c.mainWindow()
                    .click(
                        0,
                        "main.increment",
                        w -> main.get()
                            .target()))
            .async(
                "click Offscreen window",
                c -> window.get()
                    .click(
                        0,
                        "offscreen.increment",
                        w -> extra.get()
                            .target()))
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
                    .click(
                        0,
                        "offscreen.increment",
                        w -> extra.get()
                            .target()))
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
                    .click(
                        0,
                        "main.increment",
                        w -> main.get()
                            .target()))
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
            drawDefaultBackground();
            drawCenteredString(fontRendererObj, title + ": " + clicks, width / 2, height / 2 - 25, 0xFFFFFF);
            hovered = increment.mousePressed(mc, mouseX, mouseY);
            super.drawScreen(mouseX, mouseY, partialTicks);
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
