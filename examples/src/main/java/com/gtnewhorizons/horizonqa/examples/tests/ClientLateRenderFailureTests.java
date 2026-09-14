package com.gtnewhorizons.horizonqa.examples.tests;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Select this exact test alone. A render failure after success must produce an infrastructure exit, never a pass. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientLateRenderFailureTests {

    private ClientLateRenderFailureTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 400)
    public static void renderFailureAfterSuccessPreservesCauseAndCleansOnce(GameTestHelper helper) {
        helper.assumeTrue(
            helper.getTestId()
                .equals(System.getProperty("horizonqa.tests")),
            "Select this destructive lifecycle test by its exact ID in a separate client process");
        AtomicBoolean armed = new AtomicBoolean();
        AtomicInteger cleanups = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("Intentional render failure after success");
        helper.afterTest(() -> {
            helper.assertEquals(1, cleanups.get(), "Client cleanup must execute exactly once");
            helper.recordDiagnostic("lateRenderCleanupCount=" + cleanups.get());
        });
        ClientTest.scenario(helper)
            .client("install late-failing screen", client -> {
                client.afterTest(cleanups::incrementAndGet);
                Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiScreen() {

                        @Override
                        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
                            if (armed.get()) throw failure;
                            drawDefaultBackground();
                        }
                    });
            })
            .server("complete before the next render", () -> {
                helper.succeed();
                armed.set(true);
            })
            .succeed();
    }
}
