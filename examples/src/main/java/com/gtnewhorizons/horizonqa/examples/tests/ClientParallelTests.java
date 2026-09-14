package com.gtnewhorizons.horizonqa.examples.tests;

import java.awt.Rectangle;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Two launchers release this exact-selection case only after both clients have a live screen. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientParallelTests {

    private static final String SELECTION = "horizonqaexamples:ClientParallelTests.clickWhileAnotherClientIsRunning";

    private ClientParallelTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 12000)
    public static void clickWhileAnotherClientIsRunning(GameTestHelper helper) {
        helper
            .assumeTrue(SELECTION.equals(System.getProperty("horizonqa.tests")), "Select the parallel case explicitly");
        Path report = HorizonQAProperties.junitReportFile()
            .getAbsoluteFile()
            .getParentFile()
            .toPath();
        ClientTarget button = ClientTarget.of("parallel client button", client -> {
            ParallelScreen screen = client.screen(ParallelScreen.class);
            GuiButton target = screen.button;
            return new ClickTarget(target, new Rectangle(target.xPosition, target.yPosition, 120, 20), () -> true);
        });
        ClientTest.scenario(helper)
            .client("open independent screen", client -> {
                Minecraft.getMinecraft()
                    .displayGuiScreen(new ParallelScreen());
                try {
                    Files.createFile(report.resolve("client-ready"));
                } catch (IOException error) {
                    throw new UncheckedIOException(error);
                }
            })
            .withinTicks(11000)
            .awaitClient(
                "both clients are running",
                client -> {
                    if (!Files.exists(report.resolve("release-client")))
                        throw new AssertionError("Waiting for peer client");
                })
            .click(button)
            .awaitClient(
                "only this screen received one click",
                client -> {
                    if (client.screen(ParallelScreen.class).clicks != 1)
                        throw new AssertionError("Expected one local click");
                })
            .capture("parallel-client")
            .succeed();
    }

    private static final class ParallelScreen extends GuiScreen {

        private GuiButton button;
        private int clicks;

        @Override
        public void initGui() {
            button = new GuiButton(0, width / 2 - 60, height / 2 - 10, 120, 20, "Parallel client");
            buttonList.add(button);
        }

        @Override
        protected void actionPerformed(GuiButton clicked) {
            if (clicked != button) throw new AssertionError("Clicked an unexpected target");
            clicks++;
        }
    }
}
