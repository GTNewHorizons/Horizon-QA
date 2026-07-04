package com.gtnewhorizons.horizonqa;

import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizons.horizonqa.internal.InteractiveTestSession;
import com.gtnewhorizons.horizonqa.visual.GameTestOverlayRenderer;
import com.gtnewhorizons.horizonqa.visual.SelectionOutlineClientRenderer;
import com.gtnewhorizons.horizonqa.visual.VisualManager;
import com.gtnewhorizons.horizonqa.visual.WandLabelInput;
import com.gtnewhorizons.horizonqa.visual.WandLabelRenderer;
import com.gtnewhorizons.horizonqa.visual.WandHudOverlay;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        if (!HorizonQAProperties.interactiveFeaturesEnabled()) return;
        WandLabelInput.registerKeyBinding();
        FMLCommonHandler.instance()
            .bus()
            .register(new WandLabelInput());
        MinecraftForge.EVENT_BUS.register(new SelectionOutlineClientRenderer());
        MinecraftForge.EVENT_BUS.register(new GameTestOverlayRenderer());
        MinecraftForge.EVENT_BUS.register(new WandHudOverlay());
        MinecraftForge.EVENT_BUS.register(new WandLabelRenderer());
        InteractiveTestSession.onClearAllCallback = VisualManager::clearAll;
    }
}
