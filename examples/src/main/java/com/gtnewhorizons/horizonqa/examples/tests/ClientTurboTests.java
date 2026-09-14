package com.gtnewhorizons.horizonqa.examples.tests;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;

import org.apache.logging.log4j.LogManager;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientScenario;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.gtnewhorizons.horizonqa.internal.GameTestRunner;

/** Scoped acceleration exercises the real furnace lifecycle and returns to normal client operations. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientTurboTests {

    private ClientTurboTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 600)
    public static void furnaceSmeltsDuringAcceleratedWait(GameTestHelper helper) {
        long[] elapsed = new long[2];
        ClientScenario scenario = ClientTest.scenario(helper);
        smelt(helper, scenario, 1, elapsed, 0);
        smelt(helper, scenario, 10, elapsed, 1);
        scenario.server("compare real smelting duration", () -> {
            LogManager.getLogger("GameTest")
                .info("Furnace wall time: 1x={} ms, 10x={} ms", elapsed[0] / 1_000_000, elapsed[1] / 1_000_000);
            helper.assertTrue(
                elapsed[1] < elapsed[0] / 2,
                "10x smelting should take less than half the baseline wall time");
        })
            .capture("after-smelting")
            .server("normal rate after render", ClientTurboTests::assertNormalRate)
            .succeed();
    }

    private static void smelt(GameTestHelper helper, ClientScenario scenario, int multiplier, long[] elapsed,
        int index) {
        long[] started = new long[2];
        scenario.server("supply furnace", () -> {
            assertNormalRate();
            helper.setBlock("marker", Blocks.air);
            helper.setBlock("marker", Blocks.furnace);
            TileEntityFurnace furnace = helper.assertTileEntityPresent(TileEntityFurnace.class, "marker");
            furnace.setInventorySlotContents(0, new ItemStack(Blocks.iron_ore));
            furnace.setInventorySlotContents(1, new ItemStack(Items.coal));
            started[0] = helper.getWorld()
                .getTotalWorldTime();
            started[1] = System.nanoTime();
        })
            .withinTicks(300)
            .awaitServerAccelerated("furnace produces iron at " + multiplier + "x", multiplier, () -> {
                ItemStack result = helper.assertTileEntityPresent(TileEntityFurnace.class, "marker")
                    .getStackInSlot(2);
                if (result == null) throw new AssertionError("Furnace has not smelted the ore");
                if (result.getItem() != Items.iron_ingot) throw new AssertionError("Unexpected furnace output");
                if (GameTestRunner.tickMultiplier() != multiplier) throw new AssertionError("Unexpected tick rate");
                if (helper.getWorld()
                    .getTotalWorldTime() - started[0] < 200) {
                    throw new AssertionError("Smelting did not consume real world ticks");
                }
            })
            .server("normal rate after wait", () -> {
                elapsed[index] = System.nanoTime() - started[1];
                assertNormalRate();
            });
    }

    @GameTest(template = "client_smoke", timeoutTicks = 100, required = false)
    public static void acceleratedTimeoutRestoresRateBeforeCleanup(GameTestHelper helper) {
        helper.afterTest(ClientTurboTests::assertNormalRate);
        ClientTest.scenario(helper)
            .withinTicks(5)
            .awaitServerAccelerated("intentional timeout", 10, () -> { throw new AssertionError("never ready"); })
            .succeed();
    }

    private static void assertNormalRate() {
        if (GameTestRunner.tickMultiplier() != 1) throw new AssertionError("Acceleration leaked outside its wait");
    }
}
