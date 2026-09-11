package com.gtnewhorizons.horizonqa.examples.tests;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;

/** Real world input must pass through Forge before invoking vanilla item behavior. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class WorldInputTests {

    private WorldInputTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void opensBookThroughMouseInput(GameTestHelper helper) {
        checkBookInput(helper, -99, Outcome.OPEN_BOOK);
    }

    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void opensBookThroughConfiguredKeyboardInput(GameTestHelper helper) {
        checkBookInput(helper, Keyboard.KEY_RCONTROL, Outcome.OPEN_BOOK);
    }

    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void forgeCancellationPreventsItemUse(GameTestHelper helper) {
        checkBookInput(helper, -99, Outcome.CANCEL_PRESS);
    }

    /** Intentional required failure. Client teardown still verifies input release before subsequent tests. */
    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void intentionalInputFailureChecksRelease(GameTestHelper helper) {
        checkBookInput(helper, -99, Outcome.THROW_ON_PRESS);
    }

    private static void checkBookInput(GameTestHelper helper, int binding, Outcome outcome) {
        ClientTest client = ClientTest.attach(helper);
        InputProbe probe = new InputProbe(binding, outcome);
        var sequence = helper.startSequence()
            .thenExecute("equip a real written book", () -> equipBook(helper))
            .thenWaitUntilAsync("held book synchronized", 80, () -> client.run(c -> {
                ItemStack held = Minecraft.getMinecraft().thePlayer.getHeldItem();
                if (held == null || held.getItem() != Items.written_book)
                    throw new AssertionError("Book not synchronized");
            }))
            .thenExecuteAsync("observe normal world input", 40, () -> client.run(c -> {
                Minecraft mc = Minecraft.getMinecraft();
                if (c.screen() != null) throw new AssertionError("Expected world without GUI before item use");
                float pitch = mc.thePlayer.rotationPitch;
                mc.thePlayer.rotationPitch = -90;
                int previousBinding = mc.gameSettings.keyBindUseItem.getKeyCode();
                mc.gameSettings.keyBindUseItem.setKeyCode(binding);
                KeyBinding.resetKeyBindingArrayAndHash();
                MinecraftForge.EVENT_BUS.register(probe);
                FMLCommonHandler.instance()
                    .bus()
                    .register(probe);
                c.afterTest(() -> {
                    MinecraftForge.EVENT_BUS.unregister(probe);
                    FMLCommonHandler.instance()
                        .bus()
                        .unregister(probe);
                    mc.gameSettings.keyBindUseItem.setKeyCode(previousBinding);
                    KeyBinding.resetKeyBindingArrayAndHash();
                    mc.thePlayer.rotationPitch = pitch;
                });
                c.afterTest(probe::assertReleased);
            }))
            .thenExecuteAsync("use held item through normal input", 60, client::useHeldItem)
            .thenExecuteAsync("normal input was observed and respected", 40, () -> client.run(c -> {
                if (probe.presses != 1) throw new AssertionError("Expected one input press, got " + probe.presses);
                if (outcome == Outcome.CANCEL_PRESS) {
                    if (c.screen() != null) throw new AssertionError("Cancelled input still opened a GUI");
                } else {
                    c.screen(GuiScreenBook.class);
                }
                probe.assertReleased();
            }));
        if (outcome == Outcome.OPEN_BOOK) {
            sequence.thenExecuteAsync(
                "world input rejects an active GUI",
                40,
                () -> client.useHeldItem()
                    .handle((ignored, error) -> {
                        Throwable cause = error;
                        while (cause instanceof java.util.concurrent.CompletionException) cause = cause.getCause();
                        if (!(cause instanceof IllegalStateException) || !cause.getMessage()
                            .contains("no active screen")) {
                            throw new AssertionError("Expected active-screen rejection", error);
                        }
                        return null;
                    }));
        }
        sequence.thenSucceed();
    }

    private static void equipBook(GameTestHelper helper) {
        if (helper.getWorld().playerEntities.size() != 1) throw new AssertionError("Expected one real test player");
        EntityPlayer player = (EntityPlayer) helper.getWorld().playerEntities.get(0);
        int slot = player.inventory.currentItem;
        ItemStack previous = player.inventory.getStackInSlot(slot);
        helper.afterTest(() -> {
            player.inventory.setInventorySlotContents(slot, previous);
            player.inventoryContainer.detectAndSendChanges();
        });
        NBTTagList pages = new NBTTagList();
        pages.appendTag(new NBTTagString("Normal input reached a vanilla written book."));
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", "Horizon input test");
        tag.setString("author", "Horizon QA");
        tag.setTag("pages", pages);
        ItemStack book = new ItemStack(Items.written_book);
        book.setTagCompound(tag);
        player.inventory.setInventorySlotContents(slot, book);
        player.inventoryContainer.detectAndSendChanges();
    }

    private enum Outcome {
        OPEN_BOOK,
        CANCEL_PRESS,
        THROW_ON_PRESS
    }

    public static final class InputProbe {

        private final int binding;
        private final Outcome outcome;
        private int presses;

        private InputProbe(int binding, Outcome outcome) {
            this.binding = binding;
            this.outcome = outcome;
        }

        private void assertReleased() {
            boolean down = binding < 0 ? Mouse.isButtonDown(binding + 100) : Keyboard.isKeyDown(binding);
            if (down || Minecraft.getMinecraft().gameSettings.keyBindUseItem.getIsKeyPressed()) {
                throw new AssertionError("Use-item input remained held");
            }
        }

        @SubscribeEvent
        public void onMouse(MouseEvent event) {
            if (binding >= 0 || event.button != binding + 100 || !event.buttonstate) return;
            presses++;
            if (outcome == Outcome.CANCEL_PRESS) event.setCanceled(true);
            if (outcome == Outcome.THROW_ON_PRESS) throw new IllegalStateException("Intentional world input failure");
        }

        @SubscribeEvent
        public void onKey(InputEvent.KeyInputEvent event) {
            if (binding > 0 && Keyboard.getEventKey() == binding && Keyboard.getEventKeyState()) presses++;
        }
    }
}
