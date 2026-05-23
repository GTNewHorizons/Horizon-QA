package com.gtnewhorizons.horizonqa.structure;

import net.minecraft.block.Block;

import cpw.mods.fml.common.registry.GameData;

public final class RegistryStringResolver {

    private RegistryStringResolver() {}

    public static Block resolve(String registryName) {
        Object result = GameData.getBlockRegistry()
            .getObject(registryName);
        return result instanceof Block ? (Block) result : null;
    }

    public static String getName(Block block) {
        Object name = GameData.getBlockRegistry()
            .getNameForObject(block);
        return name != null ? name.toString() : null;
    }
}
