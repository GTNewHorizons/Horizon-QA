package com.gtnewhorizons.horizonqa.examples.tests;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.gtnewhorizons.horizonqa.client.NativeInput;
import com.gtnewhorizons.horizonqa.internal.ReportedRun;

/** Intentional failure examples: select exact cases; these are not an all-pass suite. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientFailureTests {

    private static int originalScale = -1;
    private static boolean staleWorkRan;

    private ClientFailureTests() {}

    @GameTest(timeoutTicks = 160)
    public static void aRequiredFailure(GameTestHelper helper) {
        assertionFailure(helper);
    }

    @GameTest(required = false, timeoutTicks = 160)
    public static void bOptionalFailure(GameTestHelper helper) {
        assertionFailure(helper);
    }

    private static void assertionFailure(GameTestHelper helper) {
        ClientTest client = ClientTest.attach(helper);
        helper.startSequence()
            .thenExecuteAsync("mutate and fail with input held", 40, () -> client.run(c -> {
                originalScale = Minecraft.getMinecraft().gameSettings.guiScale;
                c.afterTest(() -> Minecraft.getMinecraft().gameSettings.guiScale = originalScale);
                Minecraft.getMinecraft().gameSettings.guiScale = 1;
                Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiScreen());
                c.holdKey(KeyEvent.VK_SHIFT);
                c.click(0, "unavailable target", ignored -> null);
                c.run(ignored -> staleWorkRan = true);
                throw new AssertionError("intentional client assertion failure");
            }))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 30)
    public static void cTimeoutWithInputHeld(GameTestHelper helper) {
        ClientTest client = ClientTest.attach(helper);
        helper.startSequence()
            .thenExecuteAsync("hold input", 40, () -> client.run(c -> {
                Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiScreen());
                c.holdKey(KeyEvent.VK_SHIFT);
            }))
            .thenExecuteAsync(
                "unavailable target times out",
                60,
                () -> client.click(
                    0,
                    "never ready",
                    c -> new ClickTarget(c.screen(), new Rectangle(30, 30, 10, 10), () -> false)))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 160)
    public static void dCancellation(GameTestHelper helper) {
        ClientTest client = ClientTest.attach(helper);
        helper.startSequence()
            .thenExecuteAsync(
                "cancel from the client thread",
                40,
                () -> client.run(c -> ReportedRun.requestAbort("intentional client cancellation")))
            .thenSucceed();
    }

    @GameTest(required = false, timeoutTicks = 160)
    public static void eRenderCrashIsInfrastructure(GameTestHelper helper) {
        ClientTest client = ClientTest.attach(helper);
        helper.startSequence()
            .thenExecuteAsync(
                "open crashing real renderer",
                40,
                () -> client.run(
                    c -> Minecraft.getMinecraft()
                        .displayGuiScreen(new CrashingScreen())))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 160)
    public static void fWorldUnloadIsInfrastructure(GameTestHelper helper) {
        ClientTest client = ClientTest.attach(helper);
        helper.startSequence()
            .thenExecuteAsync(
                "unload the world",
                40,
                () -> client.run(
                    c -> Minecraft.getMinecraft()
                        .loadWorld(null)))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 160)
    public static void gCancellationWhileWorldUnloads(GameTestHelper helper) {
        ClientTest client = ClientTest.attach(helper);
        helper.startSequence()
            .thenExecuteAsync("cancel then stop server during teardown", 40, () -> client.run(c -> {
                ReportedRun.requestAbort("intentional cancellation while world unloads");
                Minecraft.getMinecraft()
                    .loadWorld(null);
            }))
            .thenSucceed();
    }

    @GameTest(template = "client_chest", required = false, timeoutTicks = 160)
    public static void hServerTickCrashIsInfrastructure(GameTestHelper helper) {
        ClientTest client = ClientTest.attach(helper);
        TestPos chest = helper.absolute("chest");
        TileEntity original = helper.getWorld()
            .getTileEntity(chest.x(), chest.y(), chest.z());
        helper.afterTest(
            () -> helper.getWorld()
                .setTileEntity(chest.x(), chest.y(), chest.z(), original));
        helper.startSequence()
            .thenExecuteAsync("hold input before server crash", 40, () -> client.run(c -> {
                Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiScreen());
                c.holdKey(KeyEvent.VK_SHIFT);
            }))
            .thenExecute(
                "fail in the actual world tick",
                () -> helper.getWorld()
                    .setTileEntity(chest.x(), chest.y(), chest.z(), new CrashingTile()));
    }

    @GameTest(timeoutTicks = 160)
    public static void zCleanupAfterFailure(GameTestHelper helper) {
        ClientTest client = ClientTest.attach(helper);
        helper.startSequence()
            .thenWaitUntilAsync("previous case fully cleaned", 40, () -> client.run(c -> {
                if (c.screen() != null) throw new AssertionError("Screen leaked from previous case");
                if (!NativeInput.released()) throw new AssertionError("Input remained pressed");
                if (staleWorkRan) throw new AssertionError("Cancelled queued work executed");
                if (originalScale >= 0 && Minecraft.getMinecraft().gameSettings.guiScale != originalScale) {
                    throw new AssertionError("Client settings were not restored");
                }
            }))
            .thenSucceed();
    }

    private static final class CrashingScreen extends GuiScreen {

        @Override
        public void drawScreen(int x, int y, float partialTicks) {
            throw new IllegalStateException("intentional real render crash");
        }
    }

    private static final class CrashingTile extends TileEntity {

        @Override
        public void updateEntity() {
            if (!worldObj.isRemote) throw new IllegalStateException("intentional real server world tick crash");
        }
    }
}
