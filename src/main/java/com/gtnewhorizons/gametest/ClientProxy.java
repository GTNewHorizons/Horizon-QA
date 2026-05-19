package com.gtnewhorizons.gametest;

import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizons.gametest.internal.InteractiveTestSession;
import com.gtnewhorizons.gametest.visual.GameTestOverlayRenderer;
import com.gtnewhorizons.gametest.visual.SelectionOutlineClientRenderer;
import com.gtnewhorizons.gametest.visual.VisualManager;

import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new SelectionOutlineClientRenderer());
        MinecraftForge.EVENT_BUS.register(new GameTestOverlayRenderer());
        InteractiveTestSession.onClearAllCallback = VisualManager::clearAll;
    }
}
