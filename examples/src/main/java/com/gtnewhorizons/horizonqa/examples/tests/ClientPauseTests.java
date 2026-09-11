package com.gtnewhorizons.horizonqa.examples.tests;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.Display;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** A real pause screen must not stop client synchronization in unattended client-test mode. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientPauseTests {

    private ClientPauseTests() {}

    /** Run with an inactive, visible game window to exercise the renderer's real focus-loss path. */
    @GameTest(template = "client_smoke", timeoutTicks = 400)
    public static void inactiveWindowAllowsWorldInputAfterScreenCleanup(GameTestHelper helper) {
        ClientTest client = ClientTest.attach(helper);
        helper.startSequence()
            .thenExecute("equip a real written book", () -> WorldInputTests.equipBook(helper))
            .thenWaitUntilAsync("book reached client", 80, () -> client.run(c -> {
                ItemStack held = Minecraft.getMinecraft().thePlayer.getHeldItem();
                if (held == null || held.getItem() != Items.written_book)
                    throw new AssertionError("Book not synchronized");
            }))
            .thenWaitUntilAsync(
                "game window is inactive",
                160,
                () -> client.run(
                    c -> {
                        if (Display.isActive()) throw new AssertionError("Regression requires an inactive game window");
                    }))
            .thenExecuteAsync("enable focus-loss pause and close screen like teardown", 40, () -> client.run(c -> {
                Minecraft mc = Minecraft.getMinecraft();
                boolean previous = mc.gameSettings.pauseOnLostFocus;
                float pitch = mc.thePlayer.rotationPitch;
                c.afterTest(() -> {
                    mc.gameSettings.pauseOnLostFocus = previous;
                    mc.thePlayer.rotationPitch = pitch;
                });
                mc.gameSettings.pauseOnLostFocus = true;
                mc.thePlayer.rotationPitch = -90;
                mc.displayInGameMenu();
                mc.displayGuiScreen(null);
            }))
            .thenExecuteAsync("render world after screen cleanup", 40, () -> client.capture("inactive-world"))
            .thenExecuteAsync("inactive renderer left world input available", 40, () -> client.run(c -> {
                if (Display.isActive()) throw new AssertionError("Window became active during regression");
                if (!Minecraft.getMinecraft().gameSettings.pauseOnLostFocus)
                    throw new AssertionError("Setting was reset");
                if (c.screen() != null) throw new AssertionError("Automatic pause reopened after screen cleanup");
            }))
            .thenExecuteAsync("use book after screen cleanup", 60, client::useHeldItem)
            .thenExecuteAsync("normal input opens book", 40, () -> client.run(c -> c.screen(GuiScreenBook.class)))
            .thenExecuteAsync("close book through Escape", 40, () -> client.run(ClientTest::escape))
            .thenExecuteAsync(
                "explicit pause remains available",
                40,
                () -> client.run(
                    c -> Minecraft.getMinecraft()
                        .displayInGameMenu()))
            .thenExecuteAsync("render explicit pause", 40, () -> client.capture("explicit-pause"))
            .thenExecuteAsync("Escape closes explicit pause", 40, () -> client.run(c -> {
                c.screen(GuiIngameMenu.class);
                c.escape();
            }))
            .thenExecuteAsync("render world after Escape", 40, () -> client.capture("world-after-escape"))
            .thenExecuteAsync(
                "explicit pause stayed closed",
                40,
                () -> client
                    .run(c -> { if (c.screen() != null) throw new AssertionError("Pause reopened after Escape"); }))
            .thenSucceed();
    }

    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void serverInventorySynchronizesWhilePauseMenuRemainsOpen(GameTestHelper helper) {
        ClientTest client = ClientTest.attach(helper);
        if (helper.getWorld().playerEntities.size() != 1) throw new AssertionError("Expected one real test player");
        EntityPlayer player = (EntityPlayer) helper.getWorld().playerEntities.get(0);
        ItemStack previous = player.inventory.getStackInSlot(8);
        helper.afterTest(() -> {
            player.inventory.setInventorySlotContents(8, previous);
            player.inventoryContainer.detectAndSendChanges();
        });
        helper.startSequence()
            .thenExecute("prepare synchronized baseline", () -> setSlot(player, Items.stick))
            .thenWaitUntilAsync("baseline reached client", 80, () -> client.run(c -> assertSlot(Items.stick)))
            .thenExecuteAsync(
                "open real pause menu",
                40,
                () -> client.run(
                    c -> Minecraft.getMinecraft()
                        .displayInGameMenu()))
            .thenExecuteAsync("capture rendered pause menu", 40, () -> client.capture("pause-menu"))
            .thenExecuteAsync("pause menu is active", 40, () -> client.run(c -> c.screen(GuiIngameMenu.class)))
            .thenExecute("server changes inventory while menu is open", () -> setSlot(player, Items.diamond))
            .thenWaitUntilAsync("server update arrives without closing menu", 80, () -> client.run(c -> {
                c.screen(GuiIngameMenu.class);
                assertSlot(Items.diamond);
            }))
            .thenExecuteAsync("Escape closes the real menu", 40, () -> client.run(ClientTest::escape))
            .thenWaitUntilAsync(
                "returned to world",
                40,
                () -> client.run(
                    c -> { if (c.screen() != null) throw new AssertionError("Pause menu did not respond to Escape"); }))
            .thenSucceed();
    }

    private static void setSlot(EntityPlayer player, Item item) {
        player.inventory.setInventorySlotContents(8, new ItemStack(item));
        player.inventoryContainer.detectAndSendChanges();
    }

    private static void assertSlot(Item expected) {
        ItemStack stack = Minecraft.getMinecraft().thePlayer.inventory.getStackInSlot(8);
        if (stack == null || stack.getItem() != expected)
            throw new AssertionError("Server inventory update has not reached client");
    }
}
