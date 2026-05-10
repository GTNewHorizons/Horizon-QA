package com.gtnewhorizons.gametest.examples.tests;

import com.gtnewhorizons.gametest.api.GameTestHelper;
import com.gtnewhorizons.gametest.api.TestPos;
import com.gtnewhorizons.gametest.api.annotation.GameTest;
import com.gtnewhorizons.gametest.api.annotation.GameTestHolder;
import com.gtnewhorizons.gametest.api.gt.GTNHGameTestHelper;
import com.gtnewhorizons.gametest.api.gt.ItemMatcher;
import com.gtnewhorizons.gametest.api.gt.MaintenanceType;
import com.gtnewhorizons.gametest.api.gt.Multiblock;

import gregtech.api.enums.Materials;
import gregtech.api.enums.TierEU;

@GameTestHolder(value = "gametestexamples")
public class GTNHExampleTests {

    @GameTest(template = "ebf", timeoutTicks = 1500, batch = "gtnh")
    public static void testTitaniumSmelting(GameTestHelper baseHelper) {
        GTNHGameTestHelper helper = baseHelper.gtnh();
        Multiblock multi = helper.multiblock(new TestPos(1, 0, 0));
        multi.assertFormed();
        multi.fixMaintenance();
        multi.inputBus(0).insert(Materials.Nickel.getDust(1), Materials.Aluminium.getDust(3));
        helper.insertProgrammedCircuit(new TestPos(1, 0, 1), 0);
        helper.supplyEU(new TestPos(0, 0, 0), TierEU.EV, 1, 900);
        multi.runRecipe();
        multi.outputs().assertContains(ItemMatcher.of(Materials.NickelAluminide.getIngots(4)));
        baseHelper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 1500, batch = "gtnh")
    public static void testTitaniumSmeltingImperative(GameTestHelper baseHelper) {
        GTNHGameTestHelper helper = baseHelper.gtnh();

        TestPos controller = new TestPos(1, 0, 0);
        TestPos energyHatch = new TestPos(0, 0, 0);
        TestPos inputBus = new TestPos(1, 0, 1);
        TestPos outputBus = new TestPos(1, 0, 2);

        helper.assertMachineFormed(controller);
        helper.fixAllMaintenanceIssues(controller);

        baseHelper.insertItem(inputBus, Materials.Nickel.getDust(1));
        baseHelper.insertItem(inputBus, Materials.Aluminium.getDust(3));
        helper.insertProgrammedCircuit(inputBus, 0);

        helper.supplyEU(energyHatch, TierEU.EV, 1, 900);
        helper.runUntilMachineIdle(controller, 1500);

        helper.assertItemInBus(outputBus, Materials.NickelAluminide.getIngots(4));

        baseHelper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 20, batch = "gtnh", required = false)
    public static void testMaintenanceIssueDetection(GameTestHelper baseHelper) {
        GTNHGameTestHelper helper = baseHelper.gtnh();
        TestPos controller = new TestPos(1, 0, 0);

        helper.assertMachineFormed(controller);
        helper.assertMachineHasIssues(controller, MaintenanceType.WRENCH);

        baseHelper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 20, batch = "gtnh")
    public static void testEnergyHatchAcceptsEU(GameTestHelper baseHelper) {
        GTNHGameTestHelper helper = baseHelper.gtnh();
        TestPos energyHatch = new TestPos(0, 0, 0);

        helper.supplyEU(energyHatch, 512, 1, 100);
        helper.fastForwardTicks(100);
        helper.assertEUStored(energyHatch, 1);

        baseHelper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 20, batch = "gtnh")
    public static void testFluidHatchFillAndAssert(GameTestHelper baseHelper) {
        GTNHGameTestHelper helper = baseHelper.gtnh();
        TestPos inputBus = new TestPos(2, 0, 0);

        helper.fillHatch(inputBus, "nitrogen", 2000);
        helper.assertFluidInHatch(inputBus, "nitrogen", 2000);

        baseHelper.succeed();
    }
}
