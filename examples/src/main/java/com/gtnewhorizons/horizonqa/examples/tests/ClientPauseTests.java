package com.gtnewhorizons.horizonqa.examples.tests;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** A real pause screen must not stop client synchronization in unattended client-test mode. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientPauseTests {

    private ClientPauseTests() {}

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
