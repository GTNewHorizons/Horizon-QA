package com.gtnewhorizons.horizonqa.world;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManagerHell;

public class VoidWorldChunkManager extends WorldChunkManagerHell {

    public VoidWorldChunkManager(World ignored) {
        super(BiomeGenBase.plains, 0.5F);
    }
}
