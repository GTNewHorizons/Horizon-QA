package com.gtnewhorizons.gametest;

import com.gtnewhorizons.gametest.core.InteractiveTestSession;
import com.gtnewhorizons.gametest.visual.GameTestOverlayRenderer;
import com.gtnewhorizons.gametest.visual.SelectionOutlineClientRenderer;
import com.gtnewhorizons.gametest.visual.VisualManager;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new SelectionOutlineClientRenderer());
        MinecraftForge.EVENT_BUS.register(new GameTestOverlayRenderer());
        // Wire up the clear-all callback so VisualManager is only referenced client-side.
        InteractiveTestSession.onClearAllCallback = VisualManager::clearAll;
    }
}
