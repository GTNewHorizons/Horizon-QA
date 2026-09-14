package com.gtnewhorizons.horizonqa.examples.tests;

import net.minecraft.client.Minecraft;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Destructive lifecycle examples. Select one exact test ID and expect an infrastructure exit code of 2. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientLifecycleTests {

    private ClientLifecycleTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 400)
    public static void clientLinkageFailureAbortsRun(GameTestHelper helper) {
        requireExplicitSelection(helper);
        ClientTest.scenario(helper)
            .client(
                "trigger a client linkage failure",
                client -> { throw new NoClassDefFoundError("Intentional client lifecycle failure"); })
            .succeed();
    }

    @GameTest(template = "client_smoke", timeoutTicks = 400)
    public static void worldUnloadAbortsRun(GameTestHelper helper) {
        requireExplicitSelection(helper);
        ClientTest.scenario(helper)
            .client(
                "unload the active test world",
                client -> Minecraft.getMinecraft()
                    .loadWorld(null))
            .succeed();
    }

    private static void requireExplicitSelection(GameTestHelper helper) {
        helper.assumeTrue(
            helper.getTestId()
                .equals(System.getProperty("horizonqa.tests")),
            "Select this destructive lifecycle test by its exact ID in a separate client process");
    }
}
