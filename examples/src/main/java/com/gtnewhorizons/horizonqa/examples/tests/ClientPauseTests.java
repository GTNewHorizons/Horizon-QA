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
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(40)
            .server("equip a real written book", () -> WorldInputTests.equipBook(helper))
            .withinTicks(80)
            .awaitClient("book reached client", c -> {
                ItemStack held = Minecraft.getMinecraft().thePlayer.getHeldItem();
                if (held == null || held.getItem() != Items.written_book)
                    throw new AssertionError("Book not synchronized");
            })
            .withinTicks(160)
            .awaitClient(
                "game window is inactive",
                c -> {
                    if (Display.isActive()) throw new AssertionError("Regression requires an inactive game window");
                })
            .client("enable focus-loss pause and close screen like teardown", c -> {
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
            })
            .capture("inactive-world")
            .client("inactive renderer left world input available", c -> {
                if (Display.isActive()) throw new AssertionError("Window became active during regression");
                if (!Minecraft.getMinecraft().gameSettings.pauseOnLostFocus)
                    throw new AssertionError("Setting was reset");
                if (c.screen() != null) throw new AssertionError("Automatic pause reopened after screen cleanup");
            })
            .withinTicks(60)
            .useHeldItem()
            .client("normal input opens book", c -> c.screen(GuiScreenBook.class))
            .escape()
            .client(
                "explicit pause remains available",
                c -> Minecraft.getMinecraft()
                    .displayInGameMenu())
            .capture("explicit-pause")
            .client("Escape closes explicit pause", c -> {
                c.screen(GuiIngameMenu.class);
                c.escape();
            })
            .capture("world-after-escape")
            .client(
                "explicit pause stayed closed",
                c -> { if (c.screen() != null) throw new AssertionError("Pause reopened after Escape"); })
            .succeed();
    }

    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void serverInventorySynchronizesWhilePauseMenuRemainsOpen(GameTestHelper helper) {
        if (helper.getWorld().playerEntities.size() != 1) throw new AssertionError("Expected one real test player");
        EntityPlayer player = (EntityPlayer) helper.getWorld().playerEntities.get(0);
        ItemStack previous = player.inventory.getStackInSlot(8);
        helper.afterTest(() -> {
            player.inventory.setInventorySlotContents(8, previous);
            player.inventoryContainer.detectAndSendChanges();
        });
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(40)
            .server("prepare synchronized baseline", () -> setSlot(player, Items.stick))
            .withinTicks(80)
            .awaitClient("baseline reached client", c -> assertSlot(Items.stick))
            .client(
                "open real pause menu",
                c -> Minecraft.getMinecraft()
                    .displayInGameMenu())
            .capture("pause-menu")
            .client("pause menu is active", c -> c.screen(GuiIngameMenu.class))
            .server("server changes inventory while menu is open", () -> setSlot(player, Items.diamond))
            .withinTicks(80)
            .awaitClient("server update arrives without closing menu", c -> {
                c.screen(GuiIngameMenu.class);
                assertSlot(Items.diamond);
            })
            .escape()
            .awaitClient(
                "returned to world",
                c -> { if (c.screen() != null) throw new AssertionError("Pause menu did not respond to Escape"); })
            .succeed();
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
