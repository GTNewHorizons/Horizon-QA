package com.gtnewhorizons.horizonqa.examples.tests;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.gtnewhorizons.horizonqa.client.NativeInput;
import com.gtnewhorizons.horizonqa.examples.ExamplesMod;
import com.gtnewhorizons.horizonqa.internal.GameTestRunner;

/** Compares the same real furnace at normal/accelerated speed, then checks native input and cleanup. */
@GameTestHolder(value = ExamplesMod.MODID, clientOnly = true)
public final class ClientAccelerationTests {

    private ClientAccelerationTests() {}

    @GameTest(template = "client_smelter", timeoutTicks = 400)
    public static void aNormalSmelting(GameTestHelper helper) {
        smelt(helper, 1);
    }

    @GameTest(template = "client_smelter", timeoutTicks = 400)
    public static void bAcceleratedSmelting(GameTestHelper helper) {
        smelt(helper, 10);
    }

    private static void smelt(GameTestHelper helper, int multiplier) {
        TileEntityFurnace[] furnace = new TileEntityFurnace[1];
        long[] started = new long[2];
        ClientTest.scenario(helper)
            .server("prepare the empty furnace and player floor", () -> {
                assertNormalRate();
                EntityPlayerMP player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
                double x = player.posX, y = player.posY, z = player.posZ;
                double motionX = player.motionX, motionY = player.motionY, motionZ = player.motionZ;
                float fallDistance = player.fallDistance;
                helper.afterTest(() -> {
                    if (furnace[0] != null) {
                        for (int slot = 0; slot < furnace[0].getSizeInventory(); slot++)
                            furnace[0].setInventorySlotContents(slot, null);
                    }
                    helper.setBlock("furnace", Blocks.air);
                    player.setPositionAndUpdate(x, y, z);
                    player.motionX = motionX;
                    player.motionY = motionY;
                    player.motionZ = motionZ;
                    player.fallDistance = fallDistance;
                    helper.assertBlockPresent(Blocks.air, "furnace");
                    assertNormalRate();
                });
                TestPos standing = helper.absolute("standing");
                player.motionX = player.motionY = player.motionZ = 0;
                player.fallDistance = 0;
                player.setPositionAndUpdate(standing.x() + 0.5, standing.y(), standing.z() + 0.5);
                helper.setBlock("furnace", Blocks.furnace);
                furnace[0] = helper.assertTileEntityPresent(TileEntityFurnace.class, "furnace");
            })
            .client(
                "open a normal screen before simulation",
                c -> Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiScreen()))
            .server("supply one cobblestone and coal", () -> {
                helper.setSlot("furnace", 0, new ItemStack(Blocks.cobblestone));
                helper.setSlot("furnace", 1, new ItemStack(Items.coal));
                started[0] = System.nanoTime();
                started[1] = helper.getWorld()
                    .getTotalWorldTime();
            })
            .awaitServerAccelerated("furnace produced stone", 300, multiplier, () -> {
                ItemStack output = furnace[0].getStackInSlot(2);
                if (output == null || output.getItem() != Item.getItemFromBlock(Blocks.stone) || output.stackSize != 1)
                    throw new AssertionError("Expected one stone from real furnace ticks, observed " + output);
            })
            .server("normal speed restored before client continuation", () -> {
                assertNormalRate();
                long ticks = helper.getWorld()
                    .getTotalWorldTime() - started[1];
                if (ticks < 200) throw new AssertionError("Furnace must receive real world ticks; observed " + ticks);
                helper.recordDiagnostic(
                    String.format(
                        Locale.ROOT,
                        "furnace multiplier=%d simulationTicks=%d wallSeconds=%.3f",
                        multiplier,
                        ticks,
                        (System.nanoTime() - started[0]) / 1_000_000_000.0));
            })
            .escape(60)
            .awaitClient("normal Escape processing after accelerated wait", 60, c -> {
                if (c.screen() != null || !NativeInput.released())
                    throw new AssertionError("Expected closed screen and released native input");
            })
            .succeed();
    }

    @GameTest(timeoutTicks = 160)
    public static void zSuccessiveCleanup(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .server("previous server scope ended", ClientAccelerationTests::assertNormalRate)
            .awaitClient("previous client fully cleaned", c -> {
                if (c.screen() != null || !NativeInput.released())
                    throw new AssertionError("Previous accelerated test leaked its screen or input");
            })
            .succeed();
    }

    private static void assertNormalRate() {
        if (GameTestRunner.tickMultiplier() != 1)
            throw new AssertionError("Server acceleration leaked outside its wait");
    }
}
