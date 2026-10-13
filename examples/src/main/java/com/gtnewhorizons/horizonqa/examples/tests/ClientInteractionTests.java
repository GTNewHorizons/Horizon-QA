package com.gtnewhorizons.horizonqa.examples.tests;

import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.util.concurrent.CompletionException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.AfterBatch;
import com.gtnewhorizons.horizonqa.api.annotation.BeforeBatch;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.gtnewhorizons.horizonqa.client.NativeInput;

/** Native GUI/input and authoritative gameplay checks; this holder must never load on a dedicated server. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientInteractionTests {

    private static final ClientTarget CHEST_SLOT = ClientTarget.of("chest slot", ClientInteractionTests::chestSlot);
    private static boolean batchOpen;
    private static int originalGuiScale = -1;
    private static int originalWindowWidth;
    private static int originalWindowHeight;
    private static Runnable assertPlayerRestored;

    @BeforeBatch("client")
    public static void beforeBatch() {
        if (batchOpen) throw new IllegalStateException("Previous batch leaked state");
        batchOpen = true;
    }

    @AfterBatch("client")
    public static void afterBatch() {
        if (!batchOpen) throw new IllegalStateException("Batch lifecycle did not run");
        batchOpen = false;
        originalGuiScale = -1;
        originalWindowWidth = 0;
        originalWindowHeight = 0;
        assertPlayerRestored = null;
    }

    private ClientInteractionTests() {}

    @GameTest(batch = "client", timeoutTicks = 400)
    public static void aNativeButton(GameTestHelper helper) {
        if (!batchOpen) throw new AssertionError("BeforeBatch did not run on the server thread");
        AssertionError resolverFailure = new AssertionError("intentional live target failure");
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(40)
            .client("open real screen and check previous cleanup", c -> {
                if (c.screen() != null) throw new AssertionError("Previous test leaked a screen");
                Minecraft mc = Minecraft.getMinecraft();
                int scale = mc.gameSettings.guiScale;
                originalGuiScale = scale;
                originalWindowWidth = mc.displayWidth;
                originalWindowHeight = mc.displayHeight;
                c.afterTest(() -> mc.gameSettings.guiScale = scale);
                mc.gameSettings.guiScale = 2;
                Minecraft.getMinecraft()
                    .displayGuiScreen(new ButtonScreen());
            })
            .async(
                "failed gesture releases Shift before recovery",
                c -> c.shiftClick("failing target", ignored -> { throw resolverFailure; })
                    .handle((ignored, error) -> {
                        while (error instanceof CompletionException) error = error.getCause();
                        if (error != resolverFailure) {
                            if (error != null) throw new CompletionException(error);
                            throw new AssertionError("Expected original target failure");
                        }
                        return null;
                    }))
            .awaitClient(
                "failed gesture released its modifier",
                c -> {
                    if (!NativeInput.released()) throw new AssertionError("Failed Shift-click left input pressed");
                })
            .shiftClick(ClientTarget.of("one-pixel button", ClientInteractionTests::button), 200)
            .awaitClient("normal input handler received one Shift-click and release", c -> {
                ButtonScreen screen = c.screen(ButtonScreen.class);
                if (screen.clicks != 1 || !screen.released || !screen.shiftPressed || screen.updates == 0) {
                    throw new AssertionError(
                        "Expected one native Shift-click, release and screen updates; clicks=" + screen.clicks);
                }
                if (!NativeInput.released()) throw new AssertionError("Shift-click completed with input still held");
            })
            .doubleClick(ClientTarget.of("one-pixel button", ClientInteractionTests::button), 200)
            .awaitClient("normal input handler receives both clicks", c -> {
                if (c.screen(ButtonScreen.class).clicks != 3 || !NativeInput.released())
                    throw new AssertionError("Expected two additional native button clicks with released input");
            })
            .scroll(ClientTarget.of("native wheel area", ClientInteractionTests::dragArea), -1)
            .awaitClient(
                "normal screen handler receives wheel input",
                c -> {
                    if (c.screen(ButtonScreen.class).wheel <= 0)
                        throw new AssertionError("Expected native wheel-up input");
                })
            .drag(ClientTarget.of("native drag area", ClientInteractionTests::dragArea), c -> new Point(80, 60), 8, 200)
            .awaitClient("normal screen handler receives drag and release", c -> {
                ButtonScreen screen = c.screen(ButtonScreen.class);
                if (screen.dragDistance < 30 || !NativeInput.released()) throw new AssertionError(
                    "Expected a native drag with released input; distance=" + screen.dragDistance);
            })
            .resizeWindow(801, 601, 200)
            .awaitClient("normal resize updates the client framebuffer", c -> {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.displayWidth != 801 || mc.displayHeight != 601) throw new AssertionError(
                    "Expected resized framebuffer, observed " + mc.displayWidth + "x" + mc.displayHeight);
            })
            .capture("button")
            .escape(200)
            .awaitClient(
                "screen closed normally",
                c -> { if (c.screen() != null) throw new AssertionError("Escape did not close the screen"); })
            .succeed();
    }

    @GameTest(batch = "client", template = "client_chest", timeoutTicks = 400)
    public static void bChestGameplay(GameTestHelper helper) {
        if (!batchOpen) throw new AssertionError("BeforeBatch did not run on the server thread");
        EntityPlayerMP player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
        NBTTagList inventory = player.inventory.writeToNBT(new NBTTagList());
        ItemStack cursor = player.inventory.getItemStack();
        double x = player.posX;
        double y = player.posY;
        double z = player.posZ;
        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;
        GameType mode = player.theItemInWorldManager.getGameType();
        int selectedSlot = player.inventory.currentItem;
        helper.afterTest(() -> {
            player.closeScreen();
            player.inventory.readFromNBT(inventory);
            player.inventory.setItemStack(cursor);
            player.inventory.currentItem = selectedSlot;
            player.setGameType(mode);
            player.rotationYaw = yaw;
            player.rotationPitch = pitch;
            player.setPositionAndUpdate(x, y, z);
            player.sendContainerToPlayer(player.inventoryContainer);
        });
        assertPlayerRestored = () -> {
            NBTTagList observedInventory = player.inventory.writeToNBT(new NBTTagList());
            if (!inventory.equals(observedInventory)) {
                throw new AssertionError(
                    "Expected original player inventory " + inventory + ", observed " + observedInventory);
            }
            if (!ItemStack.areItemStacksEqual(cursor, player.inventory.getItemStack())) {
                throw new AssertionError(
                    "Expected original cursor " + cursor + ", observed " + player.inventory.getItemStack());
            }
            if (player.inventory.currentItem != selectedSlot || player.theItemInWorldManager.getGameType() != mode) {
                throw new AssertionError(
                    "Expected selected slot " + selectedSlot
                        + " and game mode "
                        + mode
                        + ", observed "
                        + player.inventory.currentItem
                        + " and "
                        + player.theItemInWorldManager.getGameType());
            }
        };
        TestPos chest = helper.absolute("chest");
        TestPos standing = helper.absolute("standing");
        player.setGameType(GameType.CREATIVE);
        player.inventory.clearInventory(null, -1);
        player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.dirt));
        player.inventory.currentItem = 0;
        player.setPositionAndUpdate(standing.x() + 0.5, standing.y(), standing.z() + 0.5);
        helper.setSlot("chest", 0, new ItemStack(Items.diamond, 3));
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(60)
            .awaitClient("fixture and player synchronized", 100, c -> {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.theWorld.getBlock(chest.x(), chest.y(), chest.z()) != Blocks.chest
                    || Math.abs(mc.thePlayer.posX - standing.x() - 0.5) > 0.2
                    || Math.abs(mc.thePlayer.boundingBox.minY - standing.y()) > 0.2
                    || Math.abs(mc.thePlayer.posZ - standing.z() - 0.5) > 0.2
                    || !mc.thePlayer.capabilities.isCreativeMode
                    || mc.thePlayer.isSneaking()
                    || mc.thePlayer.getHeldItem() == null
                    || mc.thePlayer.getHeldItem()
                        .getItem() != Item.getItemFromBlock(Blocks.dirt)) {
                    throw new AssertionError(
                        "Waiting for real fixture/player packets: block="
                            + mc.theWorld.getBlock(chest.x(), chest.y(), chest.z())
                            + " player="
                            + mc.thePlayer.posX
                            + ","
                            + mc.thePlayer.boundingBox.minY
                            + ","
                            + mc.thePlayer.posZ
                            + " expected feet="
                            + standing);
                }
            })
            .client("aim at the synchronized chest", c -> {
                Minecraft.getMinecraft().thePlayer.rotationYaw = 180;
                Minecraft.getMinecraft().thePlayer.rotationPitch = 29.3F;
            })
            .awaitClient("rendered chest ray", c -> {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectType.BLOCK
                    || mc.objectMouseOver.blockX != chest.x()
                    || mc.objectMouseOver.blockY != chest.y()
                    || mc.objectMouseOver.blockZ != chest.z()) {
                    throw new AssertionError("Waiting for actual rendered chest ray: " + mc.objectMouseOver);
                }
            })
            .useHeldItem(40)
            .awaitScreen(GuiChest.class, 80)
            .awaitClient("real chest inventory synchronized", 80, c -> {
                GuiChest screen = c.screen(GuiChest.class);
                ItemStack stack = screen.inventorySlots.getSlot(0)
                    .getStack();
                if (stack == null || stack.getItem() != Items.diamond || stack.stackSize != 3) {
                    throw new AssertionError("Expected three diamonds from the server chest");
                }
            })
            .rightClick(CHEST_SLOT)
            .awaitServer("server chest split by GUI packet", () -> {
                helper.assertInventoryCount("chest", new ItemStack(Items.diamond), 1);
                ItemStack stack = player.inventory.getItemStack();
                if (stack == null || stack.getItem() != Items.diamond || stack.stackSize != 2) {
                    throw new AssertionError("Expected authoritative cursor stack of two diamonds");
                }
            })
            .click(CHEST_SLOT)
            .awaitServer("server chest restored and cursor emptied", () -> {
                helper.assertInventoryCount("chest", new ItemStack(Items.diamond), 3);
                if (player.inventory.getItemStack() != null)
                    throw new AssertionError("Expected empty cursor after returning diamonds");
            })
            .succeed();
    }

    @GameTest(batch = "client", timeoutTicks = 160)
    public static void zSuccessiveInteraction(GameTestHelper helper) {
        if (!batchOpen) throw new AssertionError("BeforeBatch did not run on the server thread");
        if (assertPlayerRestored != null) assertPlayerRestored.run();
        ClientTest.scenario(helper)
            .client("next session observes completed cleanup", 40, c -> {
                if (c.screen() != null) throw new AssertionError("Previous test leaked a screen");
                if (!NativeInput.released()) throw new AssertionError("Previous test left input pressed");
                if (originalGuiScale >= 0 && Minecraft.getMinecraft().gameSettings.guiScale != originalGuiScale) {
                    throw new AssertionError(
                        "Expected original GUI scale " + originalGuiScale
                            + ", observed "
                            + Minecraft.getMinecraft().gameSettings.guiScale);
                }
                if (originalWindowWidth > 0
                    && (Display.getWidth() != originalWindowWidth || Display.getHeight() != originalWindowHeight
                        || Minecraft.getMinecraft().displayWidth != originalWindowWidth
                        || Minecraft.getMinecraft().displayHeight != originalWindowHeight))
                    throw new AssertionError("Previous test leaked its native window size");
            })
            .succeed();
    }

    private static ClickTarget button(ClientTest client) {
        ButtonScreen screen = client.screen(ButtonScreen.class);
        GuiButton button = screen.button;
        return new ClickTarget(button, new Rectangle(button.xPosition, button.yPosition, 1, 1), () -> {
            if (screen.renderedX != button.xPosition || screen.renderedY != button.yPosition) return false;
            if (!screen.rescaled) {
                screen.rescaled = true;
                Minecraft mc = Minecraft.getMinecraft();
                mc.gameSettings.guiScale = 1;
                ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
                screen.setWorldAndResolution(mc, resolution.getScaledWidth(), resolution.getScaledHeight());
                return false;
            }
            return true;
        });
    }

    private static ClickTarget chestSlot(ClientTest client) {
        GuiContainer screen = client.screen(GuiChest.class);
        Slot slot = screen.inventorySlots.getSlot(0);
        // The consumer owns vanilla container targeting; names cover development and packaged SRG fields.
        int left = offset(screen, "guiLeft", "field_147003_i");
        int top = offset(screen, "guiTop", "field_147009_r");
        return new ClickTarget(
            slot,
            new Rectangle(left + slot.xDisplayPosition, top + slot.yDisplayPosition, 16, 16),
            () -> true);
    }

    private static ClickTarget dragArea(ClientTest client) {
        ButtonScreen screen = client.screen(ButtonScreen.class);
        return new ClickTarget(
            screen,
            new Rectangle(20, 20, 1, 1),
            () -> screen.renderedX == 20 && screen.renderedY == 20);
    }

    private static int offset(GuiContainer screen, String developmentName, String runtimeName) {
        try {
            Field field = FieldUtils.getField(screen.getClass(), developmentName, true);
            if (field == null) field = FieldUtils.getField(screen.getClass(), runtimeName, true);
            if (field == null) throw new IllegalStateException("Cannot resolve vanilla container bounds");
            return field.getInt(screen);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot resolve vanilla container bounds", error);
        }
    }

    private static final class ButtonScreen extends GuiScreen {

        private int clicks;
        private boolean shiftPressed;
        private boolean released;
        private boolean rescaled;
        private int updates;
        private int renderedX;
        private int renderedY;
        private GuiButton button;
        private int wheel;
        private Point dragStart;
        private double dragDistance;

        @Override
        public void initGui() {
            if (button == null) button = new GuiButton(0, width / 2 - 60, height / 2, 120, 20, "Native input");
            buttonList.add(button);
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            clicks++;
            shiftPressed = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        }

        @Override
        protected void mouseMovedOrUp(int x, int y, int button) {
            if (button == 0) released = true;
            super.mouseMovedOrUp(x, y, button);
        }

        @Override
        public void handleMouseInput() {
            wheel += Mouse.getEventDWheel();
            super.handleMouseInput();
        }

        @Override
        protected void mouseClicked(int x, int y, int button) {
            if (button == 0) dragStart = new Point(x, y);
            super.mouseClicked(x, y, button);
        }

        @Override
        protected void mouseClickMove(int x, int y, int button, long elapsed) {
            if (button == 0 && dragStart != null) dragDistance = Math.max(dragDistance, dragStart.distance(x, y));
            super.mouseClickMove(x, y, button, elapsed);
        }

        @Override
        public void updateScreen() {
            updates++;
        }

        @Override
        public void drawScreen(int x, int y, float partialTicks) {
            renderedX = x;
            renderedY = y;
            drawDefaultBackground();
            super.drawScreen(x, y, partialTicks);
        }
    }
}
