package com.gtnewhorizons.horizonqa.examples.tests;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;

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

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.AfterBatch;
import com.gtnewhorizons.horizonqa.api.annotation.BeforeBatch;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Native GUI/input and authoritative gameplay checks; this holder must never load on a dedicated server. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientInteractionTests {

    private static boolean batchOpen;

    @BeforeBatch("client")
    public static void beforeBatch() {
        if (batchOpen) throw new IllegalStateException("Previous batch leaked state");
        batchOpen = true;
    }

    @AfterBatch("client")
    public static void afterBatch() {
        if (!batchOpen) throw new IllegalStateException("Batch lifecycle did not run");
        batchOpen = false;
    }

    private ClientInteractionTests() {}

    @GameTest(batch = "client", timeoutTicks = 400)
    public static void aNativeButton(GameTestHelper helper) {
        if (!batchOpen) throw new AssertionError("BeforeBatch did not run on the server thread");
        ClientTest client = ClientTest.attach(helper);
        helper.startSequence()
            .thenExecuteAsync("open real screen and check previous cleanup", 40, () -> client.run(c -> {
                if (c.screen() != null) throw new AssertionError("Previous test leaked a screen");
                Minecraft mc = Minecraft.getMinecraft();
                int scale = mc.gameSettings.guiScale;
                c.afterTest(() -> mc.gameSettings.guiScale = scale);
                mc.gameSettings.guiScale = 2;
                Minecraft.getMinecraft()
                    .displayGuiScreen(new ButtonScreen());
                c.holdKey(KeyEvent.VK_SHIFT);
            }))
            .thenExecuteAsync("native button input after hover frame", 200, () -> client.click(0, "button", c -> {
                ButtonScreen screen = c.screen(ButtonScreen.class);
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
            }))
            .thenWaitUntilAsync("normal input handler received click", 40, () -> client.run(c -> {
                ButtonScreen screen = c.screen(ButtonScreen.class);
                if (!screen.clicked || !screen.released || !screen.shiftPressed || screen.updates == 0) {
                    throw new AssertionError("Expected native Shift-click, release and screen updates");
                }
                c.releaseKey(KeyEvent.VK_SHIFT);
            }))
            .thenExecuteAsync("capture normal rendered screen", 40, () -> client.capture("button"))
            .thenExecuteAsync("Escape through native keyboard", 200, () -> client.key(KeyEvent.VK_ESCAPE))
            .thenWaitUntilAsync(
                "screen closed normally",
                40,
                () -> client
                    .run(c -> { if (c.screen() != null) throw new AssertionError("Escape did not close the screen"); }))
            .thenSucceed();
    }

    @GameTest(batch = "client", template = "client_chest", timeoutTicks = 400)
    public static void bChestGameplay(GameTestHelper helper) {
        if (!batchOpen) throw new AssertionError("BeforeBatch did not run on the server thread");
        ClientTest client = ClientTest.attach(helper);
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
        TestPos chest = helper.absolute("chest");
        TestPos standing = helper.absolute("standing");
        player.setGameType(GameType.CREATIVE);
        player.inventory.clearInventory(null, -1);
        player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.dirt));
        player.inventory.currentItem = 0;
        player.setPositionAndUpdate(standing.x() + 0.5, standing.y(), standing.z() + 0.5);
        helper.setSlot("chest", 0, new ItemStack(Items.diamond, 3));
        helper.startSequence()
            .thenWaitUntilAsync("fixture and player synchronized", 100, () -> client.run(c -> {
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
                mc.thePlayer.rotationYaw = 180;
                mc.thePlayer.rotationPitch = 29.3F;
                if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectType.BLOCK
                    || mc.objectMouseOver.blockX != chest.x()
                    || mc.objectMouseOver.blockY != chest.y()
                    || mc.objectMouseOver.blockZ != chest.z()) {
                    throw new AssertionError("Waiting for actual rendered chest ray: " + mc.objectMouseOver);
                }
            }))
            .thenExecuteAsync("right click chest with configured use binding", 40, client::useItem)
            .thenWaitUntilAsync("real chest window and inventory synchronized", 80, () -> client.run(c -> {
                GuiChest screen = c.screen(GuiChest.class);
                ItemStack stack = screen.inventorySlots.getSlot(0)
                    .getStack();
                if (stack == null || stack.getItem() != Items.diamond || stack.stackSize != 3) {
                    throw new AssertionError("Expected three diamonds from the server chest");
                }
            }))
            .thenExecuteAsync(
                "take stack through native container input",
                60,
                () -> client.click(0, "chest slot", c -> chestSlot(c)))
            .thenWaitUntil("server chest emptied by GUI packet", 60, () -> {
                helper.assertInventoryEmpty("chest");
                ItemStack stack = player.inventory.getItemStack();
                if (stack == null || stack.getItem() != Items.diamond || stack.stackSize != 3) {
                    throw new AssertionError("Expected authoritative cursor stack of three diamonds");
                }
            })
            .thenExecuteAsync(
                "return stack through native container input",
                60,
                () -> client.click(0, "chest slot", c -> chestSlot(c)))
            .thenWaitUntil(
                "server chest restored",
                60,
                () -> helper.assertInventoryCount("chest", new ItemStack(Items.diamond), 3))
            .thenSucceed();
    }

    @GameTest(batch = "client", timeoutTicks = 400)
    public static void zSuccessiveInteraction(GameTestHelper helper) {
        aNativeButton(helper);
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

        private boolean clicked;
        private boolean shiftPressed;
        private boolean released;
        private boolean rescaled;
        private int updates;
        private int renderedX;
        private int renderedY;
        private GuiButton button;

        @Override
        public void initGui() {
            if (button == null) button = new GuiButton(0, width / 2 - 60, height / 2, 120, 20, "Native input");
            buttonList.add(button);
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            clicked = true;
            shiftPressed = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        }

        @Override
        protected void mouseMovedOrUp(int x, int y, int button) {
            if (button == 0) released = true;
            super.mouseMovedOrUp(x, y, button);
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
