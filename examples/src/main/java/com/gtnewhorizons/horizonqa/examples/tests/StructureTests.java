package com.gtnewhorizons.horizonqa.examples.tests;

import com.gtnewhorizons.horizonqa.examples.ExamplesMod;
import net.minecraft.init.Blocks;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

@GameTestHolder(ExamplesMod.MODID)
public class StructureTests {

    @GameTest(template = "single_stone", timeoutTicks = 20)
    public static void singleStonePresent(GameTestHelper helper) {
        helper.assertBlockPresent(Blocks.stone, 0, 0, 0);
        helper.succeed();
    }

    @GameTest(template = "stone_platform", timeoutTicks = 20)
    public static void stonePlatformAllBlocks(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.assertBlockPresent(Blocks.stone, x, 0, z);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "single_stone", timeoutTicks = 20)
    public static void aboveTemplateIsAir(GameTestHelper helper) {
        helper.assertBlockAbsent(Blocks.stone, 0, 1, 0);
        helper.succeed();
    }
}
