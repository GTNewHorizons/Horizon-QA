package com.gtnewhorizons.gametest.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

final class GridSweeper {

    private GridSweeper() {}

    static void clear(WorldServer world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (minY < 0) minY = 0;
        if (maxY > 255) maxY = 255;
        if (minX > maxX || minY > maxY || minZ > maxZ) return;

        int chunkMinX = minX >> 4;
        int chunkMaxX = maxX >> 4;
        int chunkMinZ = minZ >> 4;
        int chunkMaxZ = maxZ >> 4;

        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
                clearChunkRegion(chunk, cx, cz, minX, minY, minZ, maxX, maxY, maxZ);
            }
        }
    }

    private static void clearChunkRegion(Chunk chunk, int cx, int cz, int minX, int minY, int minZ, int maxX, int maxY,
        int maxZ) {

        int chunkBaseX = cx << 4;
        int chunkBaseZ = cz << 4;

        int localMinX = Math.max(0, minX - chunkBaseX);
        int localMaxX = Math.min(15, maxX - chunkBaseX);
        int localMinZ = Math.max(0, minZ - chunkBaseZ);
        int localMaxZ = Math.min(15, maxZ - chunkBaseZ);

        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        int sectionMin = minY >> 4;
        int sectionMax = maxY >> 4;

        boolean modified = false;
        for (int sy = sectionMin; sy <= sectionMax; sy++) {
            ExtendedBlockStorage section = sections[sy];
            if (section == null) continue;

            int sectionBaseY = sy << 4;
            int localMinY = Math.max(0, minY - sectionBaseY);
            int localMaxY = Math.min(15, maxY - sectionBaseY);

            NibbleArray blockLight = section.getBlocklightArray();
            NibbleArray skyLight = section.getSkylightArray();

            for (int lx = localMinX; lx <= localMaxX; lx++) {
                for (int lz = localMinZ; lz <= localMaxZ; lz++) {
                    for (int ly = localMinY; ly <= localMaxY; ly++) {
                        section.func_150818_a(lx, ly, lz, Blocks.air);
                        section.setExtBlockMetadata(lx, ly, lz, 0);
                        if (blockLight != null) blockLight.set(lx, ly, lz, 0);
                        if (skyLight != null) skyLight.set(lx, ly, lz, 15);
                    }
                }
            }
            modified = true;
        }

        Map<ChunkPosition, TileEntity> teMap = chunk.chunkTileEntityMap;
        if (!teMap.isEmpty()) {
            List<ChunkPosition> toRemove = new ArrayList<>();
            for (Map.Entry<ChunkPosition, TileEntity> entry : teMap.entrySet()) {
                TileEntity te = entry.getValue();
                if (te == null) continue;
                if (te.xCoord >= minX && te.xCoord <= maxX
                    && te.yCoord >= minY
                    && te.yCoord <= maxY
                    && te.zCoord >= minZ
                    && te.zCoord <= maxZ) {
                    toRemove.add(entry.getKey());
                }
            }
            for (ChunkPosition pos : toRemove) {
                TileEntity te = teMap.remove(pos);
                if (te != null) te.invalidate();
            }
            if (!toRemove.isEmpty()) modified = true;
        }

        if (modified) chunk.setChunkModified();
    }
}
