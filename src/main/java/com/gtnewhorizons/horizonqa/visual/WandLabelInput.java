package com.gtnewhorizons.horizonqa.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;

public final class WandLabelInput {

    public static final KeyBinding LABEL_KEY = new KeyBinding(
        "key.horizonqa.label",
        Keyboard.KEY_L,
        "key.categories.horizonqa");

    public static void registerKeyBinding() {
        ClientRegistry.registerKeyBinding(LABEL_KEY);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.END) return;
        while (LABEL_KEY.isPressed()) {
            openLabelPrompt();
        }
    }

    private static void openLabelPrompt() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.currentScreen != null) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemHorizonWand)) return;

        int[] target = ItemHorizonWand.getTargetedPosition(mc.thePlayer);
        String existing = ItemHorizonWand.getLabelAt(held, target[0], target[1], target[2]);
        mc.displayGuiScreen(new WandLabelPrompt(target[0], target[1], target[2], existing));
    }
}
