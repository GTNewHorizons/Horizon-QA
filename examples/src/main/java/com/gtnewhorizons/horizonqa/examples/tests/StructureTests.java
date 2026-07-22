package com.gtnewhorizons.horizonqa.examples.tests;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.examples.ExamplesMod;
import com.gtnewhorizons.horizonqa.structure.StructureExporter;

@GameTestHolder(ExamplesMod.MODID)
public class StructureTests {

    @GameTest(template = "single_stone", timeoutTicks = 20)
    public static void singleStonePresent(GameTestHelper helper) {
        helper.assertBlockPresent(Blocks.stone, "stone");
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

    @GameTest(timeoutTicks = 20)
    public static void exportedItemStacksUsePortableIdentity(GameTestHelper helper) {
        final File outputDirectory;
        try {
            outputDirectory = Files.createTempDirectory("horizonqa-export-")
                .toFile();
        } catch (IOException e) {
            throw new AssertionError("Could not create temporary export directory", e);
        }
        helper.afterTest(() -> deleteRecursively(outputDirectory));

        helper.setBlock(0, 0, 0, Blocks.chest);
        helper.startSequence()
            .thenIdle(1)
            .thenExecute(() -> {
                helper.insertItem(0, 0, 0, new ItemStack(Items.spawn_egg, 1, 93));
                try {
                    StructureExporter.ExportResult result = StructureExporter.export(
                        helper.getWorld(),
                        helper.getOriginX(),
                        helper.getOriginY(),
                        helper.getOriginZ(),
                        helper.getOriginX(),
                        helper.getOriginY(),
                        helper.getOriginZ(),
                        outputDirectory,
                        "portable_item");
                    if (!".snbt".equals(result.structureDataExtension())) {
                        helper.fail("Expected an SNBT export, got " + result.structureDataExtension());
                        return;
                    }
                    String snbt = new String(
                        Files.readAllBytes(new File(outputDirectory, "portable_item.snbt").toPath()),
                        StandardCharsets.UTF_8);
                    if (!snbt.contains("HorizonQAItemId: \"minecraft:spawn_egg\"")) {
                        helper.fail("Exported ItemStack did not contain its portable registry identity");
                    }
                } catch (IOException e) {
                    throw new AssertionError("Could not export structure", e);
                }
            })
            .thenSucceed();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete() && file.exists()) {
            throw new AssertionError("Could not delete temporary export path " + file);
        }
    }
}
