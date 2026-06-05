package com.gtnewhorizons.horizonqa.structure;

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
        try {
            placeInternal("unknown", template, world, originX, originY, originZ, false);
        } catch (TemplateException e) {
            LOG.warn("StructurePlacer: {}", e.getMessage());
        }
    }

    public static void placeStrict(String templateName, HybridStructureTemplate template, WorldServer world,
        int originX, int originY, int originZ) throws TemplateException {
        placeInternal(templateName, template, world, originX, originY, originZ, true);
    }

    private static void placeInternal(String templateName, HybridStructureTemplate template, WorldServer world,
        int originX, int originY, int originZ, boolean strict) throws TemplateException {

        HybridStructureTemplate.PaletteEntry[] palette = template.getPalette();
        Block[] resolvedPalette = resolvePalette(templateName, palette, strict);
        int sizeX = template.getSizeX();
        int sizeY = template.getSizeY();
        int sizeZ = template.getSizeZ();

        ensureChunksLoaded(world, originX, originY, originZ, sizeX, sizeY, sizeZ);

        int notifyClients = 2;
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    int idx = template.getPaletteIndex(x, y, z);
                    HybridStructureTemplate.PaletteEntry entry = palette[idx];
                    Block block = resolvedPalette[idx];
                    if (block == null) {
                        continue;
                    }
                    int wx = originX + x;
                    int wy = originY + y;
                    int wz = originZ + z;
                    try {
                        world.setBlock(wx, wy, wz, block, entry.meta, notifyClients);
                    } catch (RuntimeException e) {
                        throw new TemplateException(
                            "Failed to place block '" + entry.name
                                + "' from template '"
                                + templateName
                                + "' at ("
                                + wx
                                + ","
                                + wy
                                + ","
                                + wz
                                + "): "
                                + errorMessage(e),
                            e);
                    }

                    NBTTagCompound teNbt = template.getTileEntity(x, y, z);
                    if (teNbt == null && !block.hasTileEntity(entry.meta)) {
                        continue;
                    }

                    TileEntity te = ensureTileEntity(world, wx, wy, wz, block, entry, strict);
                    if (te == null) {
                        if (teNbt != null) {
                            handleTemplateError(
                                strict,
                                "No TileEntity at (" + wx
                                    + ","
                                    + wy
                                    + ","
                                    + wz
                                    + ") after placing block '"
                                    + entry.name
                                    + "' from template '"
                                    + templateName
                                    + "'; cannot inject tile entity NBT",
                                null);
                        }
                        continue;
                    }

                    if (teNbt != null) {
                        NBTTagCompound patchedNbt = (NBTTagCompound) teNbt.copy();
                        patchedNbt.setInteger("x", wx);
                        patchedNbt.setInteger("y", wy);
                        patchedNbt.setInteger("z", wz);

                        try {
                            te.readFromNBT(patchedNbt);
                            world.markBlockForUpdate(wx, wy, wz);
                        } catch (RuntimeException e) {
                            throw new TemplateException(
                                "Failed to inject tile entity NBT for template '" + templateName
                                    + "' at ("
                                    + wx
                                    + ","
                                    + wy
                                    + ","
                                    + wz
                                    + "): "
                                    + errorMessage(e),
                                e);
                        }
                    }
                }
            }
        }
    }

    private static Block[] resolvePalette(String templateName, HybridStructureTemplate.PaletteEntry[] palette,
        boolean strict) throws TemplateException {
        Block[] blocks = new Block[palette.length];
        for (int i = 0; i < palette.length; i++) {
            HybridStructureTemplate.PaletteEntry entry = palette[i];
            Block block = RegistryStringResolver.resolve(entry.name);
            if (block == null) {
                handleTemplateError(
                    strict,
                    "Unknown block '" + entry.name + "' in template '" + templateName + "' at palette index " + i,
                    null);
            }
            blocks[i] = block;
        }
        return blocks;
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

    private static TileEntity ensureTileEntity(WorldServer world, int wx, int wy, int wz, Block block,
        HybridStructureTemplate.PaletteEntry entry, boolean strict) throws TemplateException {
        if (!block.hasTileEntity(entry.meta)) {
            return null;
        }

        TileEntity te = getTileEntity(world, wx, wy, wz, entry, strict);
        if (te != null) {
            return te;
        }

        te = createTileEntity(world, block, entry, wx, wy, wz, strict);
        if (te == null) {
            return null;
        }

        try {
            world.setTileEntity(wx, wy, wz, te);
        } catch (RuntimeException e) {
            handleTemplateError(
                strict,
                "Failed to attach TileEntity for block '" + entry.name
                    + "' at ("
                    + wx
                    + ","
                    + wy
                    + ","
                    + wz
                    + ") with World#setTileEntity: "
                    + errorMessage(e),
                e);
            return null;
        }
        te = getTileEntity(world, wx, wy, wz, entry, strict);
        if (te == null) {
            Chunk chunk = getChunk(world, wx, wz, entry, strict);
            TileEntity fallbackTe = createTileEntity(world, block, entry, wx, wy, wz, strict);
            if (chunk != null && fallbackTe != null) {
                try {
                    chunk.func_150812_a(wx & 15, wy, wz & 15, fallbackTe);
                } catch (RuntimeException e) {
                    handleTemplateError(
                        strict,
                        "Failed to attach TileEntity for block '" + entry.name
                            + "' at ("
                            + wx
                            + ","
                            + wy
                            + ","
                            + wz
                            + ") with Chunk#func_150812_a: "
                            + errorMessage(e),
                        e);
                    return null;
                }
                te = getTileEntity(world, wx, wy, wz, entry, strict);
            }
        }
        if (te == null) {
            handleTemplateError(
                strict,
                "Could not attach TileEntity for block '" + entry.name
                    + "' (meta "
                    + entry.meta
                    + ") at ("
                    + wx
                    + ","
                    + wy
                    + ","
                    + wz
                    + ")",
                null);
        }
        return te;
    }

    private static TileEntity getTileEntity(WorldServer world, int wx, int wy, int wz,
        HybridStructureTemplate.PaletteEntry entry, boolean strict) throws TemplateException {
        try {
            return world.getTileEntity(wx, wy, wz);
        } catch (RuntimeException e) {
            handleTemplateError(
                strict,
                "Failed to inspect TileEntity for block '" + entry.name
                    + "' at ("
                    + wx
                    + ","
                    + wy
                    + ","
                    + wz
                    + "): "
                    + errorMessage(e),
                e);
            return null;
        }
    }

    private static TileEntity createTileEntity(WorldServer world, Block block,
        HybridStructureTemplate.PaletteEntry entry, int wx, int wy, int wz, boolean strict) throws TemplateException {
        try {
            TileEntity te = block.createTileEntity(world, entry.meta);
            if (te != null) {
                return te;
            }
            handleTemplateError(
                strict,
                "Block '" + entry.name
                    + "' (meta "
                    + entry.meta
                    + ") returned null from createTileEntity at ("
                    + wx
                    + ","
                    + wy
                    + ","
                    + wz
                    + ")",
                null);
            return null;
        } catch (RuntimeException e) {
            handleTemplateError(
                strict,
                "Failed to create TileEntity for block '" + entry.name
                    + "' (meta "
                    + entry.meta
                    + ") at ("
                    + wx
                    + ","
                    + wy
                    + ","
                    + wz
                    + "): "
                    + errorMessage(e),
                e);
            return null;
        }
    }

    private static Chunk getChunk(WorldServer world, int wx, int wz, HybridStructureTemplate.PaletteEntry entry,
        boolean strict) throws TemplateException {
        try {
            return world.getChunkFromChunkCoords(wx >> 4, wz >> 4);
        } catch (RuntimeException e) {
            handleTemplateError(
                strict,
                "Failed to resolve chunk for TileEntity block '" + entry.name
                    + "' at ("
                    + wx
                    + ","
                    + wz
                    + "): "
                    + errorMessage(e),
                e);
            return null;
        }
    }

    private static void handleTemplateError(boolean strict, String message, Throwable cause) throws TemplateException {
        if (strict) {
            if (cause == null) {
                throw new TemplateException(message);
            }
            throw new TemplateException(message, cause);
        }
        LOG.warn("StructurePlacer: {}", message);
    }

    private static String errorMessage(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass()
            .getName() : message;
    }
}
