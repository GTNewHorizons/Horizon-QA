package com.gtnewhorizons.horizonqa.examples;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.examples.tests.ClientBootstrapTests;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        if (!HorizonQAProperties.clientTestsEnabled()
            || !ClientBootstrapTests.SELECTION.equals(HorizonQAProperties.rawTests())) return;
        MinecraftForge.EVENT_BUS.register(new CustomMenuFixture());
    }

    public static final class CustomMenuFixture {

        @SubscribeEvent
        public void supplyCustomMenu(GuiOpenEvent opening) {
            if (!(opening.gui instanceof GuiMainMenu)) return;
            opening.gui = new GuiScreen() {

                @Override
                public void drawScreen(int mouseX, int mouseY, float partialTicks) {
                    drawDefaultBackground();
                    drawCenteredString(fontRendererObj, "HQA custom menu fixture", width / 2, height / 2, 0xffffff);
                }
            };
            ClientBootstrapTests.customMenuSupplied = true;
            MinecraftForge.EVENT_BUS.unregister(this);
        }
    }
}
