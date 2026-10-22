package com.gtnewhorizons.horizonqa;

import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizons.horizonqa.internal.InteractiveTestSession;
import com.gtnewhorizons.horizonqa.visual.GameTestOverlayRenderer;
import com.gtnewhorizons.horizonqa.visual.SelectionOutlineClientRenderer;
import com.gtnewhorizons.horizonqa.visual.VisualManager;
import com.gtnewhorizons.horizonqa.visual.WandHudOverlay;
import com.gtnewhorizons.horizonqa.visual.WandLabelInput;
import com.gtnewhorizons.horizonqa.visual.WandLabelRenderer;
import com.gtnewhorizons.horizonqa.visual.editor.WandFreecamController;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    private com.gtnewhorizons.horizonqa.client.ClientTestRuntime clientTests;

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        if (HorizonQAProperties.clientTestsEnabled()) {
            clientTests = new com.gtnewhorizons.horizonqa.client.ClientTestRuntime(() -> startTests(null));
            FMLCommonHandler.instance()
                .bus()
                .register(clientTests);
        }
        if (!HorizonQAProperties.interactiveFeaturesEnabled()) return;
        WandLabelInput.registerKeyBinding();
        WandFreecamController.registerKeyBinding();
        FMLCommonHandler.instance()
            .bus()
            .register(new WandLabelInput());
        FMLCommonHandler.instance()
            .bus()
            .register(WandFreecamController.INSTANCE);
        MinecraftForge.EVENT_BUS.register(WandFreecamController.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new SelectionOutlineClientRenderer());
        MinecraftForge.EVENT_BUS.register(new GameTestOverlayRenderer());
        MinecraftForge.EVENT_BUS.register(new WandHudOverlay());
        MinecraftForge.EVENT_BUS.register(new WandLabelRenderer());
        InteractiveTestSession.onClearAllCallback = VisualManager::clearAll;
    }

    @Override
    public void finishRun(com.gtnewhorizons.horizonqa.report.RunResult result) {
        if (clientTests != null) clientTests.finish(result);
        else super.finishRun(result);
    }
}
