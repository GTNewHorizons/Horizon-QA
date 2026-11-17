package com.gtnewhorizons.horizonqa.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;

public class BlockDebugInvInterface extends Block {

    public static BlockDebugInvInterface INSTANCE;

    public BlockDebugInvInterface() {
        super(Material.gourd);
        setBlockTextureName("horizonqa:debug_invinter");
        setCreativeTab(CreativeTabs.tabInventory);
        setHardness(1.0f);
        setResistance(6f);
        setBlockName("Debug Inventory Interface");
    }
}
