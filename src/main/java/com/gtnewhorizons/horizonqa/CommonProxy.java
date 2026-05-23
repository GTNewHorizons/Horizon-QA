package com.gtnewhorizons.horizonqa;

import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizons.horizonqa.command.HorizonQACommand;
import com.gtnewhorizons.horizonqa.internal.GameTestBatchRunner;
import com.gtnewhorizons.horizonqa.internal.GameTestRegistry;
import com.gtnewhorizons.horizonqa.internal.InteractiveTestSession;
import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;
import com.gtnewhorizons.horizonqa.visual.SelectionBoxRenderer;
import com.gtnewhorizons.horizonqa.world.GameTestWorldType;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        HorizonQAMod.LOG.info(Config.greeting);
        HorizonQAMod.LOG.info("I am " + HorizonQAMod.NAME + " at version " + Tags.VERSION);
        HorizonQAMod.LOG.info("Mode (-D{}): {}", GameTestJvmFlags.PROPERTY, GameTestJvmFlags.isEnabled());
        if (GameTestJvmFlags.isEnabled()) {
            HorizonQAMod.LOG.info(
                "Void world registered as '{}' (Forge id {}).",
                GameTestWorldType.INSTANCE.getWorldTypeName(),
                GameTestWorldType.INSTANCE.getWorldTypeID());
        }

        ForgeChunkManager.setForcedChunkLoadingCallback(HorizonQAMod.instance, HorizonQAMod.CHUNK_LOADER);
        GameTestRegistry.setAsmData(event.getAsmData());

        ItemHorizonWand.INSTANCE = new ItemHorizonWand();
        GameRegistry.registerItem(ItemHorizonWand.INSTANCE, "wand");

        MinecraftForge.EVENT_BUS.register(new SelectionBoxRenderer());
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        InteractiveTestSession.reset();
        event.registerServerCommand(new HorizonQACommand());

        HorizonQAMod.LOG.info("Discovering tests...");
        GameTestRegistry.discoverTests();

        if (!GameTestJvmFlags.isEnabled()) return;

        if (GameTestRegistry.getAllTests()
            .isEmpty()) {
            HorizonQAMod.LOG.warn("No tests found. Nothing to run.");
            return;
        }

        HorizonQAMod.LOG.info(
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
