package com.gtnewhorizons.gametest.world;

import java.util.Collections;
import java.util.List;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

public class VoidChunkProvider implements IChunkProvider {

    private final World worldObj;
    private final Chunk[] chunkCache = new Chunk[256];

    public VoidChunkProvider(World world) {
        this.worldObj = world;
    }

    @Override
    public Chunk provideChunk(int chunkX, int chunkZ) {
        int index = cacheIndex(chunkX, chunkZ);
        Chunk cached = chunkCache[index];
        if (cached != null && cached.xPosition == chunkX && cached.zPosition == chunkZ) {
            return cached;
        }

        Chunk chunk = new Chunk(worldObj, chunkX, chunkZ);
        chunk.generateSkylightMap();
        BiomeGenBase[] biomes = worldObj.getWorldChunkManager()
            .loadBlockGeneratorData(null, chunkX << 4, chunkZ << 4, 16, 16);
        byte[] abyte = chunk.getBiomeArray();
        for (int i = 0; i < abyte.length; ++i) {
            abyte[i] = (byte) biomes[i].biomeID;
        }
        chunk.generateSkylightMap();
        chunkCache[index] = chunk;
        return chunk;
    }

    @Override
    public Chunk loadChunk(int chunkX, int chunkZ) {
        return provideChunk(chunkX, chunkZ);
    }

    @Override
    public void populate(IChunkProvider unused, int chunkX, int chunkZ) {}

    @Override
    public boolean saveChunks(boolean writeAllChunks, IProgressUpdate progressCallback) {
        return true;
    }

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public String makeString() {
        return "GameTestVoid";
    }

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType unused, int wx, int wy, int wz) {
        return Collections.emptyList();
    }

    @Override
    public ChunkPosition func_147416_a(World worldIn, String structureName, int x, int y, int z) {
        return null;
    }

    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    @Override
    public void recreateStructures(int cx, int cz) {}

    @Override
    public boolean chunkExists(int x, int z) {
        return true;
    }

    @Override
    public void saveExtraData() {}

    private static int cacheIndex(int chunkX, int chunkZ) {
        return (chunkX * 31 + chunkZ) & (256 - 1);
    }
}
