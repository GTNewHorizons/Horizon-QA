package com.gtnewhorizons.gametest.visual;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.gametest.item.ItemGameTestWand;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Left-click interception for the GameTest Wand: stores Pos1 and cancels breaking the clicked
 * block while holding the wand (creative or survival).
 *
 * <p>
 * Register on the Forge {@code EVENT_BUS}. The selection box is drawn on the client by
 * {@link SelectionOutlineClientRenderer}, registered from
 * {@link com.gtnewhorizons.gametest.ClientProxy}.
 */
public class SelectionBoxRenderer {

    // ---- Left-click input (Forge event bus) ----

    /**
     * Intercepts left-click-on-block when holding the wand: stores Pos1 and cancels the
     * block-break event so no block is damaged. Fires server-side in both creative and survival.
     */
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
        if (event.entityPlayer.worldObj.isRemote) return;

        EntityPlayer player = event.entityPlayer;
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemGameTestWand)) return;

        ItemGameTestWand.setPos1(held, player, event.x, event.y, event.z);
        event.setCanceled(true);
    }
}
