package com.gtnewhorizons.horizonqa.visual;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class SelectionBoxRenderer {

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
        if (event.entityPlayer.worldObj.isRemote) return;

        EntityPlayer player = event.entityPlayer;
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemHorizonWand)) return;

        ItemHorizonWand.setPos1(held, player, event.x, event.y, event.z);
        event.setCanceled(true);
    }
}
