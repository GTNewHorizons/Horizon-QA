package com.gtnewhorizons.horizonqa.examples.tests;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.opengl.Display;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Exercises real window events without replacing the active screen or directly resizing Minecraft. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientResizeTests {

    private ClientResizeTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 400)
    public static void resizesExistingWindowAndRestoresDimensions(GameTestHelper helper) {
        AtomicReference<Point> original = new AtomicReference<>();
        AtomicReference<GuiScreen> screen = new AtomicReference<>();
        ClientTest.scenario(helper)
            .client("open screen", c -> {
                original.set(new Point(Display.getWidth(), Display.getHeight()));
                GuiScreen opened = new GuiScreen();
                screen.set(opened);
                Minecraft.getMinecraft()
                    .displayGuiScreen(opened);
            })
            .resizeWindow(960, 540)
            .client("same screen follows native resize", c -> {
                if (c.screen() != screen.get()) throw new AssertionError("Resize replaced the screen");
                if (Minecraft.getMinecraft().displayWidth != 960 || Minecraft.getMinecraft().displayHeight != 540) {
                    throw new AssertionError("Minecraft did not observe the real window resize");
                }
            })
            .capture("resized")
            .async("restore original dimensions", c -> c.resizeWindow(original.get().x, original.get().y))
            .client(
                "restoration preserved screen",
                c -> { if (c.screen() != screen.get()) throw new AssertionError("Restoration replaced the screen"); })
            .succeed();
    }
}
