package com.gtnewhorizons.horizonqa.examples.tests;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.gt.GTNHGameTestHelper;
import com.gtnewhorizons.horizonqa.api.gt.MaintenanceType;
import com.gtnewhorizons.horizonqa.api.gt.Multiblock;
import com.gtnewhorizons.horizonqa.examples.ExamplesMod;
import com.ruling_0.materiallib.api.MaterialLibAPI;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.TierEU;
import gregtech.api.enums.materials.Materials;
import gregtech.api.enums.materials.Shapes;
import gregtech.api.util.GTRecipeBuilder;

@GameTestHolder(ExamplesMod.MODID)
public class GTNHExampleTests {

    private GTNHExampleTests() {}

    @GameTest(template = "ebf", timeoutTicks = 1500, batch = "gtnh")
    public static void testTitaniumSmelting(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();
        Multiblock ebf = gtnh.multiblock(helper.pos("controller"));
        ebf.assertFormed();
        ebf.fixMaintenance();
        ebf.inputBus(0)
            .insert(
                MaterialLibAPI.getStack(Materials.Nickel, Shapes.dust, 1),
                MaterialLibAPI.getStack(Materials.Aluminium, Shapes.dust, 3))
            .programmedCircuit(0);
        ebf.energyHatch(0)
            .supply(TierEU.EV, 1, 900);
        ebf.runRecipe();
        ebf.outputs()
            .assertContains(MaterialLibAPI.getStack(Materials.NickelAluminide, Shapes.ingot, 4));
        helper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 1500, batch = "gtnh")
    public static void testTitaniumSmeltingImperative(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();

        TestPos controller = helper.pos("controller");
        TestPos energyHatch = helper.pos("energy_hatch");
        TestPos inputBus = helper.pos("input_bus");
        TestPos outputBus = helper.pos("output_bus");

        gtnh.assertMachineFormed(controller);
        gtnh.fixAllMaintenanceIssues(controller);

        helper.insertItem(inputBus, MaterialLibAPI.getStack(Materials.Nickel, Shapes.dust, 1));
        helper.insertItem(inputBus, MaterialLibAPI.getStack(Materials.Aluminium, Shapes.dust, 3));
        gtnh.insertProgrammedCircuit(inputBus, 0);

        gtnh.supplyEU(energyHatch, TierEU.EV, 1, 900);
        gtnh.runUntilMachineIdle(controller, 1500);

        gtnh.assertItemInBus(outputBus, MaterialLibAPI.getStack(Materials.NickelAluminide, Shapes.ingot, 4));

        helper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 20, batch = "gtnh")
    public static void maintenanceGatesRecipeEvenWithFullSupply(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();
        Multiblock ebf = gtnh.multiblock(helper.pos("controller"));

        ebf.assertFormed();
        ebf.inputBus(0)
            .insert(
                MaterialLibAPI.getStack(Materials.Nickel, Shapes.dust, 1),
                MaterialLibAPI.getStack(Materials.Aluminium, Shapes.dust, 3))
            .programmedCircuit(0);
        ebf.energyHatch(0)
            .supply(TierEU.EV, 1, 20);

        gtnh.assertMachineHasIssues(
            helper.pos("controller"),
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
        TestPos controller = helper.pos("controller");

        gtnh.assertMachineFormed(controller);
        gtnh.assertMachineHasIssues(controller, MaintenanceType.WRENCH);

        helper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 20, batch = "gtnh")
    public static void testEnergyHatchAcceptsEU(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();
        TestPos energyHatch = helper.pos("energy_hatch");

        gtnh.supplyEU(energyHatch, 512, 1, 100);
        gtnh.fastForwardTicks(100);
        gtnh.assertEUStored(energyHatch, 1);

        helper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 20, batch = "gtnh")
    public static void testFluidHatchFillAndAssert(GameTestHelper helper) {
        Multiblock ebf = helper.gtnh()
            .multiblock(helper.pos("controller"));
        ebf.assertFormed();

        ebf.inputHatch(0)
            .fill("nitrogen", 2000)
            .assertContains("nitrogen", 2000);
        IllegalArgumentException failure = helper.assertThrows(
            IllegalArgumentException.class,
            () -> ebf.inputHatch(0)
                .assertContains("missing_fluid", 1));
        helper.assertEquals("Unknown fluid registry name: missing_fluid", failure.getMessage());

        helper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 1500, batch = "gtnh")
    public static void testSyntheticRecipe(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();
        Multiblock ebf = gtnh.multiblock(helper.pos("controller"));
        ebf.assertFormed();
        ebf.fixMaintenance();

        GTRecipeBuilder synthetic = GTValues.RA.stdBuilder()
            .itemInputs(MaterialLibAPI.getStack(Materials.Lead, Shapes.dust, 1))
            .itemOutputs(MaterialLibAPI.getStack(Materials.Gold, Shapes.ingot, 1))
            .duration(200)
            .eut(TierEU.EV);

        gtnh.withTestRecipe(ebf, synthetic);
        ebf.inputBus(0)
            .insert(MaterialLibAPI.getStack(Materials.Lead, Shapes.dust, 1));
        ebf.energyHatch(0)
            .supply(TierEU.EV, 1, 300);
        ebf.runRecipe();
        ebf.outputs()
            .assertContains(MaterialLibAPI.getStack(Materials.Gold, Shapes.ingot, 1));

        helper.succeed();
    }

    @GameTest(template = "ebf", timeoutTicks = 1500, batch = "gtnh")
    public static void testParallelHelper(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();
        Multiblock ebf = gtnh.multiblock(helper.pos("controller"));
        ebf.assertFormed();
        ebf.fixMaintenance();

        GTRecipeBuilder synthetic = GTValues.RA.stdBuilder()
            .itemInputs(MaterialLibAPI.getStack(Materials.Lead, Shapes.dust, 1))
            .itemOutputs(MaterialLibAPI.getStack(Materials.Gold, Shapes.ingot, 1))
            .duration(10)
            .eut(TierEU.LV);

        gtnh.withTestRecipe(ebf, synthetic);
        ebf.inputBus(0)
            .insert(MaterialLibAPI.getStack(Materials.Lead, Shapes.dust, 8));
        ebf.energyHatch(0)
            .supply(TierEU.EV, 1, 300);
        ebf.runRecipe();
        ebf.outputs()
            .assertContains(MaterialLibAPI.getStack(Materials.Gold, Shapes.ingot, 1));

        helper.succeed();
    }

    @GameTest(template = "distillation_tower_4", timeoutTicks = 1500, batch = "gtnh")
    public static void testDistillationTowerOutputRouting(GameTestHelper helper) {
        GTNHGameTestHelper gtnh = helper.gtnh();
        Multiblock dt = gtnh.multiblock(helper.pos("controller"));
        dt.assertFormed();
        dt.fixMaintenance();

        GTRecipeBuilder synthetic = GTValues.RA.stdBuilder()
            .fluidInputs(MaterialUtils.gas(Materials.Helium, 120))
            .fluidOutputs(
                MaterialUtils.gas(Materials.Oxygen, 1000),
                MaterialUtils.gas(Materials.Hydrogen, 2000),
                MaterialUtils.gas(Materials.Nitrogen, 500),
                MaterialUtils.gas(Materials.Helium, 500))
            .duration(200)
            .eut(TierEU.EV);

        gtnh.withTestRecipe(dt, synthetic);
        dt.inputHatch(0)
            .fill("helium", 120);
        dt.energyHatch(0)
            .supply(TierEU.EV, 1, 300);
        dt.runRecipe();

        dt.outputHatch(0)
            .assertContains(MaterialUtils.gas(Materials.Oxygen, 1000));
        dt.outputHatch(1)
            .assertContains(MaterialUtils.gas(Materials.Hydrogen, 2000));
        dt.outputHatch(2)
            .assertContains(MaterialUtils.gas(Materials.Nitrogen, 500));
        dt.outputHatch(3)
            .assertContains(MaterialUtils.gas(Materials.Helium, 500));

        helper.succeed();
    }

    @GameTest(template = "ebf_no_coils", timeoutTicks = 60)
    public static void doesNotFormWithoutCoils(GameTestHelper helper) {
        Multiblock ebf = helper.gtnh()
            .multiblock(helper.pos("controller"));
        ebf.assertNeverForms("EBF formed without coils");
    }

    @GameTest(template = "cleanroom", timeoutTicks = 4600)
    public static void cleanroomEfficiencyClimbs(GameTestHelper helper) {
        Multiblock cleanroom = helper.gtnh()
            .multiblock(helper.pos("controller"));

        helper.succeedWhen(() -> cleanroom.getEfficiency() > 9000);
        helper.gtnh()
            .fastForwardTicks(4600);
    }
}
