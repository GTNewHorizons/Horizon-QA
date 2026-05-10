package com.gtnewhorizons.gametest.world;

import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;

public class GameTestWorldType extends WorldType {

    public static final String TYPE_NAME = "gtnhvvoid";

    public static final GameTestWorldType INSTANCE = new GameTestWorldType();

    private GameTestWorldType() {
        super(TYPE_NAME);
    }

    @Override
    public WorldChunkManager getChunkManager(World world) {
        return new VoidWorldChunkManager(world);
    }

    @Override
    public IChunkProvider getChunkGenerator(World world, String generatorOptions) {
        return new VoidChunkProvider(world);
    }

    @Override
    public int getSpawnFuzz() {
        return 1;
    }

    @Override
    public int getMinimumSpawnHeight(World world) {
        return 64;
    }
}
