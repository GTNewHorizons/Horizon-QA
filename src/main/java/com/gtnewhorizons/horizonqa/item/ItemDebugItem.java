package com.gtnewhorizons.horizonqa.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class ItemDebugItem extends Item {

    public static ItemDebugItem INSTANCE;

    public ItemDebugItem() {
        setUnlocalizedName("horizonqa.debug.itm");
        setTextureName("horizonqa:debuggey");
        setMaxStackSize(64);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (!world.isRemote) world.playSoundEffect(
            player.posX,
            player.posY + 1.5,
            player.posZ,
            "mob.chicken.say2",
            1.0f,
            stack.stackSize != 64 ? 3.0f + stack.stackSize / 64f : .35f);
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        onItemUse(stack, player, world, 1, 2, 3, 4, 5, 6, 7);
        return stack;
    }

}
