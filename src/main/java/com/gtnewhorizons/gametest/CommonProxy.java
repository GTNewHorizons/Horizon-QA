package com.gtnewhorizons.gametest;

import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizons.gametest.command.GameTestCommand;
import com.gtnewhorizons.gametest.internal.GameTestBatchRunner;
import com.gtnewhorizons.gametest.internal.GameTestRegistry;
import com.gtnewhorizons.gametest.internal.InteractiveTestSession;
import com.gtnewhorizons.gametest.item.ItemGameTestWand;
import com.gtnewhorizons.gametest.visual.SelectionBoxRenderer;
import com.gtnewhorizons.gametest.world.GameTestWorldType;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        GameTestMod.LOG.info(Config.greeting);
        GameTestMod.LOG.info("I am " + GameTestMod.NAME + " at version " + Tags.VERSION);
        GameTestMod.LOG.info("Mode (-D{}): {}", GameTestJvmFlags.PROPERTY, GameTestJvmFlags.isEnabled());
        if (GameTestJvmFlags.isEnabled()) {
            GameTestMod.LOG.info(
                "Void world registered as '{}' (Forge id {}).",
                GameTestWorldType.INSTANCE.getWorldTypeName(),
                GameTestWorldType.INSTANCE.getWorldTypeID());
        }

        ForgeChunkManager.setForcedChunkLoadingCallback(GameTestMod.instance, GameTestMod.CHUNK_LOADER);
        GameTestRegistry.setAsmData(event.getAsmData());

        ItemGameTestWand.INSTANCE = new ItemGameTestWand();
        GameRegistry.registerItem(ItemGameTestWand.INSTANCE, "wand");

        MinecraftForge.EVENT_BUS.register(new SelectionBoxRenderer());
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        InteractiveTestSession.reset();
        event.registerServerCommand(new GameTestCommand());

        GameTestMod.LOG.info("Discovering tests...");
        GameTestRegistry.discoverTests();

        if (!GameTestJvmFlags.isEnabled()) return;

        if (GameTestRegistry.getAllTests()
            .isEmpty()) {
            GameTestMod.LOG.warn("No tests found. Nothing to run.");
            return;
        }

        GameTestMod.LOG.info(
            "Starting {} test(s) in CI mode.",
            GameTestRegistry.getAllTests()
                .size());
        GameTestBatchRunner batchRunner = new GameTestBatchRunner(
            GameTestRegistry.getAllTests(),
            GameTestRegistry.getBeforeBatchMethods(),
            GameTestRegistry.getAfterBatchMethods());
        batchRunner.start();
    }
}
