package com.gtnewhorizons.horizonqa.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockDebugIInventory extends BlockContainer {

    public static BlockDebugIInventory INSTANCE;

    public BlockDebugIInventory() {
        super(Material.sponge);
        setBlockTextureName("horizonqa:debug_iinv");
        setCreativeTab(CreativeTabs.tabInventory);
        setHardness(1.0f);
        setResistance(6f);
        setBlockName("Debug IInventory");
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileDebugIInventory();
    }
}
