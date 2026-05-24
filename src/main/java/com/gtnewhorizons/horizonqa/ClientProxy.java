package com.gtnewhorizons.horizonqa;

import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizons.horizonqa.internal.InteractiveTestSession;
import com.gtnewhorizons.horizonqa.visual.GameTestOverlayRenderer;
import com.gtnewhorizons.horizonqa.visual.SelectionOutlineClientRenderer;
import com.gtnewhorizons.horizonqa.visual.VisualManager;
import com.gtnewhorizons.horizonqa.visual.WandHudOverlay;

import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new SelectionOutlineClientRenderer());
        MinecraftForge.EVENT_BUS.register(new GameTestOverlayRenderer());
        MinecraftForge.EVENT_BUS.register(new WandHudOverlay());
        InteractiveTestSession.onClearAllCallback = VisualManager::clearAll;
    }
}
