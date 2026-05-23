package com.gtnewhorizons.horizonqa.examples.tests;

import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.gt.GTNHGameTestHelper;
import com.gtnewhorizons.horizonqa.api.gt.MaintenanceType;
import com.gtnewhorizons.horizonqa.api.gt.Multiblock;

import com.gtnewhorizons.horizonqa.examples.ExamplesMod;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTRecipeBuilder;

@GameTestHolder(ExamplesMod.MODID)
public class GTNHExampleTests {

    @GameTest(template = "ebf", timeoutTicks = 1500, batch = "gtnh")
    public static void testTitaniumSmelting(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();
        Multiblock ebf = gtnh.multiblock(at(1, 0, 0));
        ebf.assertFormed();
        ebf.fixMaintenance();
        ebf.inputBus(0)
            .insert(Materials.Nickel.getDust(1), Materials.Aluminium.getDust(3))
            .programmedCircuit(0);
        ebf.energyHatch(0)
            .supply(TierEU.EV, 1, 900);
        ebf.runRecipe();
        ebf.outputs()
            .assertContains(Materials.NickelAluminide.getIngots(4));
        helper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 1500, batch = "gtnh")
    public static void testTitaniumSmeltingImperative(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();

        TestPos controller = new TestPos(1, 0, 0);
        TestPos energyHatch = new TestPos(0, 0, 0);
        TestPos inputBus = new TestPos(1, 0, 1);
        TestPos outputBus = new TestPos(1, 0, 2);

        gtnh.assertMachineFormed(controller);
        gtnh.fixAllMaintenanceIssues(controller);

        helper.insertItem(inputBus, Materials.Nickel.getDust(1));
        helper.insertItem(inputBus, Materials.Aluminium.getDust(3));
        gtnh.insertProgrammedCircuit(inputBus, 0);

        gtnh.supplyEU(energyHatch, TierEU.EV, 1, 900);
        gtnh.runUntilMachineIdle(controller, 1500);

        gtnh.assertItemInBus(outputBus, Materials.NickelAluminide.getIngots(4));

        helper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 20, batch = "gtnh")
    public static void maintenanceGatesRecipeEvenWithFullSupply(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();
        Multiblock ebf = gtnh.multiblock(at(1, 0, 0));

        ebf.assertFormed();
        ebf.inputBus(0)
            .insert(Materials.Nickel.getDust(1), Materials.Aluminium.getDust(3))
            .programmedCircuit(0);
        ebf.energyHatch(0)
            .supply(TierEU.EV, 1, 20);

        gtnh.assertMachineHasIssues(
            at(1, 0, 0),
            MaintenanceType.WRENCH,
            MaintenanceType.SCREWDRIVER,
            MaintenanceType.SOFT_MALLET,
            MaintenanceType.HARD_HAMMER,
            MaintenanceType.SOLDERING_TOOL,
            MaintenanceType.CROWBAR);

        helper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 20, batch = "gtnh", required = false)
    public static void testMaintenanceIssueDetection(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();
        TestPos controller = new TestPos(1, 0, 0);

        gtnh.assertMachineFormed(controller);
        gtnh.assertMachineHasIssues(controller, MaintenanceType.WRENCH);

        helper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 20, batch = "gtnh")
    public static void testEnergyHatchAcceptsEU(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();
        TestPos energyHatch = new TestPos(0, 0, 0);

        gtnh.supplyEU(energyHatch, 512, 1, 100);
        gtnh.fastForwardTicks(100);
        gtnh.assertEUStored(energyHatch, 1);

        helper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 20, batch = "gtnh")
    public static void testFluidHatchFillAndAssert(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();
        TestPos inputBus = new TestPos(2, 0, 0);

        gtnh.fillHatch(inputBus, "nitrogen", 2000);
        gtnh.assertFluidInHatch(inputBus, "nitrogen", 2000);

        helper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 1500, batch = "gtnh")
    public static void testSyntheticRecipe(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();
        Multiblock ebf = gtnh.multiblock(at(1, 0, 0));
        ebf.assertFormed();
        ebf.fixMaintenance();

        GTRecipeBuilder synthetic = GTValues.RA.stdBuilder()
            .itemInputs(Materials.Lead.getDust(1))
            .itemOutputs(Materials.Gold.getIngots(1))
            .duration(200)
            .eut(TierEU.EV);

        gtnh.withTestRecipe(ebf, synthetic);
        ebf.inputBus(0)
            .insert(Materials.Lead.getDust(1));
        ebf.energyHatch(0)
            .supply(TierEU.EV, 1, 300);
        ebf.runRecipe();
        ebf.outputs()
            .assertContains(Materials.Gold.getIngots(1));

        helper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 1500, batch = "gtnh")
    public static void testParallelHelper(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();
        Multiblock ebf = gtnh.multiblock(at(1, 0, 0));
        ebf.assertFormed();
        ebf.fixMaintenance();

        GTRecipeBuilder synthetic = GTValues.RA.stdBuilder()
            .itemInputs(Materials.Lead.getDust(1))
            .itemOutputs(Materials.Gold.getIngots(1))
            .duration(10)
            .eut(TierEU.LV);

        gtnh.withTestRecipe(ebf, synthetic);
        ebf.inputBus(0)
            .insert(Materials.Lead.getDust(8));
        ebf.energyHatch(0)
            .supply(TierEU.EV, 1, 300);
        ebf.runRecipe();
        ebf.outputs()
            .assertContains(Materials.Gold.getIngots(1));

        helper.succeed();
    }

    @GameTest(template = "distillation_tower_4", timeoutTicks = 1500, batch = "gtnh")
    public static void testDistillationTowerOutputRouting(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();
        Multiblock dt = gtnh.multiblock(at(1, 0, 2));
        dt.assertFormed();
        dt.fixMaintenance();

        GTRecipeBuilder synthetic = GTValues.RA.stdBuilder()
            .fluidInputs(Materials.Helium.getGas(120))
            .fluidOutputs(
                Materials.Oxygen.getGas(1000),
                Materials.Hydrogen.getGas(2000),
                Materials.Nitrogen.getGas(500),
                Materials.Helium.getGas(500))
            .duration(200)
            .eut(TierEU.EV);

        gtnh.withTestRecipe(dt, synthetic);
        dt.inputHatch(0)
            .fill(Materials.Helium.getGas(120));
        dt.energyHatch(0)
            .supply(TierEU.EV, 1, 300);
        dt.runRecipe();

        dt.outputHatch(0)
            .assertContains(Materials.Oxygen.getGas(1000));
        dt.outputHatch(1)
            .assertContains(Materials.Hydrogen.getGas(2000));
        dt.outputHatch(2)
            .assertContains(Materials.Nitrogen.getGas(500));
        dt.outputHatch(3)
            .assertContains(Materials.Helium.getGas(500));

        helper.succeed();
    }

    @GameTest(template = "ebf_no_coils", timeoutTicks = 60)
    public static void doesNotFormWithoutCoils(GameTestHelper helper) {
        Multiblock ebf = helper.gtnh()
            .multiblock(at(1, 0, 0));
        helper.onEachTick(() -> helper.assertFalse(ebf.isFormed(), "EBF formed without coils"));
        helper.succeedAtTimeout();
    }

    @GameTest(template = "cleanroom", timeoutTicks = 4600)
    public static void cleanroomEfficiencyClimbs(GameTestHelper helper) {
        Multiblock cleanroom = helper.gtnh()
            .multiblock(at(2, 4, 2));

        helper.succeedWhen(() -> cleanroom.getEfficiency() > 9000);
        helper.gtnh()
            .fastForwardTicks(4600);
    }
}
