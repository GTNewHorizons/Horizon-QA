package com.gtnewhorizons.gametest.structure;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class StructurePlacer {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private StructurePlacer() {}

    public static void place(HybridStructureTemplate template, WorldServer world, int originX, int originY,
        int originZ) {

        ensureChunksLoaded(
            world,
            originX,
            originY,
            originZ,
            template.getSizeX(),
            template.getSizeY(),
            template.getSizeZ());

        HybridStructureTemplate.PaletteEntry[] palette = template.getPalette();
        int sizeX = template.getSizeX();
        int sizeY = template.getSizeY();
        int sizeZ = template.getSizeZ();

        int notifyClients = 2;
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    int idx = template.getPaletteIndex(x, y, z);
                    HybridStructureTemplate.PaletteEntry entry = palette[idx];
                    Block block = RegistryStringResolver.resolve(entry.name);
                    if (block == null) {
                        LOG.warn(
                            "StructurePlacer: unknown block '{}' — skipping position ({},{},{})",
                            entry.name,
                            originX + x,
                            originY + y,
                            originZ + z);
                        continue;
                    }
                    int wx = originX + x;
                    int wy = originY + y;
                    int wz = originZ + z;
                    world.setBlock(wx, wy, wz, block, entry.meta, notifyClients);

                    NBTTagCompound teNbt = template.getTileEntity(x, y, z);
                    if (teNbt == null && !block.hasTileEntity(entry.meta)) {
                        continue;
                    }

                    TileEntity te = ensureTileEntity(world, wx, wy, wz, block, entry.meta);
                    if (te == null) {
                        if (teNbt != null) {
                            LOG.warn(
                                "StructurePlacer: no TileEntity at ({},{},{}) after block placement "
                                    + "— skipping NBT injection",
                                wx,
                                wy,
                                wz);
                        }
                        continue;
                    }

                    if (teNbt != null) {
                        NBTTagCompound patchedNbt = (NBTTagCompound) teNbt.copy();
                        patchedNbt.setInteger("x", wx);
                        patchedNbt.setInteger("y", wy);
                        patchedNbt.setInteger("z", wz);

                        te.readFromNBT(patchedNbt);
                        world.markBlockForUpdate(wx, wy, wz);
                    }
                }
            }
        }
    }

    private static void ensureChunksLoaded(WorldServer world, int originX, int originY, int originZ, int sizeX,
        int sizeY, int sizeZ) {

        int chunkMinX = originX >> 4;
        int chunkMaxX = (originX + sizeX - 1) >> 4;
        int chunkMinZ = originZ >> 4;
        int chunkMaxZ = (originZ + sizeZ - 1) >> 4;

        if (world.getChunkProvider() instanceof ChunkProviderServer) {
            ChunkProviderServer cps = (ChunkProviderServer) world.getChunkProvider();
            for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                    cps.loadChunk(cx, cz);
                }
            }
        } else {
            for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                    world.getChunkFromChunkCoords(cx, cz);
                }
            }
        }
    }

    private static TileEntity ensureTileEntity(WorldServer world, int wx, int wy, int wz, Block block, int meta) {
        if (!block.hasTileEntity(meta)) {
            return null;
        }

        TileEntity te = world.getTileEntity(wx, wy, wz);
        if (te != null) {
            return te;
        }

        te = block.createTileEntity(world, meta);
        if (te == null) {
            LOG.warn(
                "StructurePlacer: block {} (meta {}) returned null from createTileEntity at ({},{},{})",
                RegistryStringResolver.getName(block),
                meta,
                wx,
                wy,
                wz);
            return null;
        }

        world.setTileEntity(wx, wy, wz, te);
        te = world.getTileEntity(wx, wy, wz);
        if (te == null) {
            Chunk chunk = world.getChunkFromChunkCoords(wx >> 4, wz >> 4);
            if (chunk != null) {
                chunk.func_150812_a(wx & 15, wy, wz & 15, block.createTileEntity(world, meta));
                te = world.getTileEntity(wx, wy, wz);
            }
        }
        return te;
    }
}
