package com.gtnewhorizons.gametest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.gametest.internal.GameTestChunkLoader;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = GameTestMod.MODID, version = Tags.VERSION, name = GameTestMod.NAME, acceptedMinecraftVersions = "[1.7.10]")
public class GameTestMod {

    public static final String MODID = "gametest";
    public static final String NAME = "Horizon QA";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance(GameTestMod.MODID)
    public static GameTestMod instance;

    public static final GameTestChunkLoader CHUNK_LOADER = new GameTestChunkLoader();

    @SidedProxy(
        clientSide = "com.gtnewhorizons.gametest.ClientProxy",
        serverSide = "com.gtnewhorizons.gametest.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
