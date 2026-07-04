package com.gtnewhorizons.horizonqa.structure;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class StructurePlacer {

    private static final Logger LOG = LogManager.getLogger("GameTest");
    private static final TileEntityNbtRotator NO_TILE_ENTITY_NBT_ROTATION = (nbt, rotation) -> {};
    private static final int MIN_BUILD_Y = 0;
    private static final int MAX_BUILD_Y = 255;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_ANY_NUMERIC = 99;

    private StructurePlacer() {}

    @FunctionalInterface
    public interface TileEntityNbtRotator {

        void rotate(NBTTagCompound nbt, int rotation);
    }

    public static void place(HybridStructureTemplate template, WorldServer world, int originX, int originY,
        int originZ) {
        place(template, world, originX, originY, originZ, 0);
    }

    public static void place(HybridStructureTemplate template, WorldServer world, int originX, int originY, int originZ,
        int rotation) {
        place(template, world, originX, originY, originZ, rotation, NO_TILE_ENTITY_NBT_ROTATION);
    }

    public static void place(HybridStructureTemplate template, WorldServer world, int originX, int originY, int originZ,
        int rotation, TileEntityNbtRotator tileNbtRotator) {
        try {
            placeInternal("unknown", template, world, originX, originY, originZ, rotation, tileNbtRotator, false);
        } catch (TemplateException e) {
            LOG.warn("StructurePlacer: {}", e.getMessage());
        }
    }

    public static void placeStrict(String templateName, HybridStructureTemplate template, WorldServer world,
        int originX, int originY, int originZ) throws TemplateException {
        placeStrict(templateName, template, world, originX, originY, originZ, 0);
    }

    public static void placeStrict(String templateName, HybridStructureTemplate template, WorldServer world,
        int originX, int originY, int originZ, int rotation) throws TemplateException {
        placeStrict(templateName, template, world, originX, originY, originZ, rotation, NO_TILE_ENTITY_NBT_ROTATION);
    }

    public static void placeStrict(String templateName, HybridStructureTemplate template, WorldServer world,
        int originX, int originY, int originZ, int rotation, TileEntityNbtRotator tileNbtRotator)
        throws TemplateException {
        placeInternal(templateName, template, world, originX, originY, originZ, rotation, tileNbtRotator, true);
    }

    public static int placedSizeX(HybridStructureTemplate template, int rotation) {
        if (template == null) {
            return 0;
        }
        return placedSizeX(template.getSizeX(), template.getSizeZ(), normalizeRotation(rotation));
    }

    public static int placedSizeZ(HybridStructureTemplate template, int rotation) {
        if (template == null) {
            return 0;
        }
        return placedSizeZ(template.getSizeX(), template.getSizeZ(), normalizeRotation(rotation));
    }

    public static void validateVerticalBounds(String templateName, int originY, int sizeY) throws TemplateException {
        long maxY = (long) originY + sizeY - 1L;
        if (sizeY <= 0 || originY < MIN_BUILD_Y || maxY > MAX_BUILD_Y) {
            throw new TemplateException(
                "Template '" + templateName
                    + "' with height "
                    + sizeY
                    + " at origin Y="
                    + originY
                    + " would occupy Y="
                    + originY
                    + ".."
                    + maxY
                    + ", outside build height "
                    + MIN_BUILD_Y
                    + ".."
                    + MAX_BUILD_Y
                    + ". Lower -Dhorizonqa.gridOrigin or use a shorter template.");
        }
    }

    private static void placeInternal(String templateName, HybridStructureTemplate template, WorldServer world,
        int originX, int originY, int originZ, int rotation, TileEntityNbtRotator tileNbtRotator, boolean strict)
        throws TemplateException {

        int rotationSteps = normalizeRotation(rotation);
        TileEntityNbtRotator rotator = tileNbtRotator != null ? tileNbtRotator : NO_TILE_ENTITY_NBT_ROTATION;

        HybridStructureTemplate.PaletteEntry[] palette = template.getPalette();
        Block[] resolvedPalette = resolvePalette(templateName, palette, strict);
        int sizeX = template.getSizeX();
        int sizeY = template.getSizeY();
        int sizeZ = template.getSizeZ();
        int placedSizeX = placedSizeX(sizeX, sizeZ, rotationSteps);
        int placedSizeZ = placedSizeZ(sizeX, sizeZ, rotationSteps);

        validateVerticalBounds(templateName, originY, sizeY);
        ensureChunksLoaded(world, originX, originZ, placedSizeX, placedSizeZ);

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
                    int wx = originX + rotatedLocalX(x, z, sizeX, sizeZ, rotationSteps);
                    int wy = originY + y;
                    int wz = originZ + rotatedLocalZ(x, z, sizeX, sizeZ, rotationSteps);
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
                    if (teNbt != null || block.hasTileEntity(entry.meta)) {
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
                        } else if (teNbt != null) {
                            NBTTagCompound patchedNbt = (NBTTagCompound) teNbt.copy();
                            patchedNbt.setInteger("x", wx);
                            patchedNbt.setInteger("y", wy);
                            patchedNbt.setInteger("z", wz);
                            rotator.rotate(patchedNbt, rotationSteps);

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

                    rotatePlacedBlock(block, world, wx, wy, wz, entry.name, rotationSteps, strict);
                }
            }
        }

        spawnEntities(templateName, template, world, originX, originY, originZ, rotationSteps, strict);
    }

    public static int rotatedLocalX(int x, int z, int sizeX, int sizeZ, int rotation) {
        return switch (rotation) {
            case 1 -> sizeZ - 1 - z;
            case 2 -> sizeX - 1 - x;
            case 3 -> z;
            default -> x;
        };
    }

    public static int rotatedLocalZ(int x, int z, int sizeX, int sizeZ, int rotation) {
        return switch (rotation) {
            case 1 -> x;
            case 2 -> sizeZ - 1 - z;
            case 3 -> sizeX - 1 - x;
            default -> z;
        };
    }

    static double rotatedLocalDoubleX(double x, double z, int sizeX, int sizeZ, int rotation) {
        return switch (rotation) {
            case 1 -> sizeZ - z;
            case 2 -> sizeX - x;
            case 3 -> z;
            default -> x;
        };
    }

    static double rotatedLocalDoubleZ(double x, double z, int sizeX, int sizeZ, int rotation) {
        return switch (rotation) {
            case 1 -> x;
            case 2 -> sizeZ - z;
            case 3 -> sizeX - x;
            default -> z;
        };
    }

    static NBTTagCompound entityNbtForPlacement(NBTTagCompound sourceNbt, int sizeX, int sizeZ, int originX,
        int originY, int originZ, int rotation) {
        NBTTagCompound entityNbt = (NBTTagCompound) sourceNbt.copy();
        patchPosition(entityNbt, sizeX, sizeZ, originX, originY, originZ, rotation);
        patchMotion(entityNbt, rotation);
        patchRotation(entityNbt, rotation);
        patchIntegerBlockTriple(
            entityNbt,
            "TileX",
            "TileY",
            "TileZ",
            sizeX,
            sizeZ,
            originX,
            originY,
            originZ,
            rotation);
        patchShortBlockTriple(entityNbt, "xTile", "yTile", "zTile", sizeX, sizeZ, originX, originY, originZ, rotation);
        patchHangingDirection(entityNbt, rotation);
        return entityNbt;
    }

    private static void spawnEntities(String templateName, HybridStructureTemplate template, WorldServer world,
        int originX, int originY, int originZ, int rotation, boolean strict) throws TemplateException {
        NBTTagList entities = template.getEntities();
        for (int i = 0; i < entities.tagCount(); i++) {
            NBTTagCompound entityNbt = entityNbtForPlacement(
                entities.getCompoundTagAt(i),
                template.getSizeX(),
                template.getSizeZ(),
                originX,
                originY,
                originZ,
                rotation);
            String entityId = entityNbt.getString("id");
            Entity entity;
            try {
                entity = EntityList.createEntityFromNBT(entityNbt, world);
            } catch (RuntimeException e) {
                throw new TemplateException(
                    "Failed to create entity '" + entityId
                        + "' from template '"
                        + templateName
                        + "' at entity index "
                        + i
                        + ": "
                        + errorMessage(e),
                    e);
            }

            if (entity == null) {
                handleTemplateError(
                    strict,
                    "Unknown entity '" + entityId + "' in template '" + templateName + "' at entity index " + i,
                    null);
                continue;
            }
            entity.dimension = world.provider.dimensionId;

            try {
                if (!world.spawnEntityInWorld(entity)) {
                    handleTemplateError(
                        strict,
                        "World refused to spawn entity '" + entityId
                            + "' from template '"
                            + templateName
                            + "' at entity index "
                            + i,
                        null);
                }
            } catch (RuntimeException e) {
                throw new TemplateException(
                    "Failed to spawn entity '" + entityId
                        + "' from template '"
                        + templateName
                        + "' at entity index "
                        + i
                        + ": "
                        + errorMessage(e),
                    e);
            }
        }
    }

    private static void patchPosition(NBTTagCompound entityNbt, int sizeX, int sizeZ, int originX, int originY,
        int originZ, int rotation) {
        NBTTagList pos = entityNbt.getTagList("Pos", TAG_DOUBLE);
        if (pos.tagCount() < 3) {
            return;
        }
        double localX = pos.func_150309_d(0);
        double localY = pos.func_150309_d(1);
        double localZ = pos.func_150309_d(2);
        entityNbt.setTag(
            "Pos",
            doubleList(
                originX + rotatedLocalDoubleX(localX, localZ, sizeX, sizeZ, rotation),
                originY + localY,
                originZ + rotatedLocalDoubleZ(localX, localZ, sizeX, sizeZ, rotation)));
    }

    private static void patchMotion(NBTTagCompound entityNbt, int rotation) {
        NBTTagList motion = entityNbt.getTagList("Motion", TAG_DOUBLE);
        if (motion.tagCount() < 3) {
            return;
        }
        double localX = motion.func_150309_d(0);
        double localY = motion.func_150309_d(1);
        double localZ = motion.func_150309_d(2);
        double rotatedX = switch (rotation) {
            case 1 -> -localZ;
            case 2 -> -localX;
            case 3 -> localZ;
            default -> localX;
        };
        double rotatedZ = switch (rotation) {
            case 1 -> localX;
            case 2 -> -localZ;
            case 3 -> -localX;
            default -> localZ;
        };
        entityNbt.setTag("Motion", doubleList(rotatedX, localY, rotatedZ));
    }

    private static void patchRotation(NBTTagCompound entityNbt, int rotation) {
        NBTTagList rotationList = entityNbt.getTagList("Rotation", TAG_FLOAT);
        if (rotationList.tagCount() < 2) {
            return;
        }
        float yaw = rotationList.func_150308_e(0);
        float pitch = rotationList.func_150308_e(1);
        entityNbt.setTag("Rotation", floatList(yaw + rotation * 90.0F, pitch));
    }

    private static void patchIntegerBlockTriple(NBTTagCompound nbt, String xKey, String yKey, String zKey, int sizeX,
        int sizeZ, int originX, int originY, int originZ, int rotation) {
        if (!nbt.hasKey(xKey) || !nbt.hasKey(yKey) || !nbt.hasKey(zKey)) {
            return;
        }
        int localX = nbt.getInteger(xKey);
        int localY = nbt.getInteger(yKey);
        int localZ = nbt.getInteger(zKey);
        nbt.setInteger(xKey, originX + rotatedLocalX(localX, localZ, sizeX, sizeZ, rotation));
        nbt.setInteger(yKey, originY + localY);
        nbt.setInteger(zKey, originZ + rotatedLocalZ(localX, localZ, sizeX, sizeZ, rotation));
    }

    private static void patchShortBlockTriple(NBTTagCompound nbt, String xKey, String yKey, String zKey, int sizeX,
        int sizeZ, int originX, int originY, int originZ, int rotation) {
        if (!nbt.hasKey(xKey) || !nbt.hasKey(yKey) || !nbt.hasKey(zKey)) {
            return;
        }
        int localX = nbt.getShort(xKey);
        int localY = nbt.getShort(yKey);
        int localZ = nbt.getShort(zKey);
        nbt.setShort(xKey, (short) (originX + rotatedLocalX(localX, localZ, sizeX, sizeZ, rotation)));
        nbt.setShort(yKey, (short) (originY + localY));
        nbt.setShort(zKey, (short) (originZ + rotatedLocalZ(localX, localZ, sizeX, sizeZ, rotation)));
    }

    private static void patchHangingDirection(NBTTagCompound entityNbt, int rotation) {
        if (rotation == 0) {
            return;
        }

        if (entityNbt.hasKey("Direction", TAG_ANY_NUMERIC)) {
            int direction = rotateHorizontalDirection(entityNbt.getByte("Direction"), rotation);
            entityNbt.setByte("Direction", (byte) direction);
            if (entityNbt.hasKey("Dir", TAG_ANY_NUMERIC)) {
                entityNbt.setByte("Dir", (byte) legacyDirFromDirection(direction));
            }
        } else if (entityNbt.hasKey("Dir", TAG_ANY_NUMERIC)) {
            int direction = directionFromLegacyDir(entityNbt.getByte("Dir"));
            entityNbt.setByte("Dir", (byte) legacyDirFromDirection(rotateHorizontalDirection(direction, rotation)));
        }
    }

    private static int rotateHorizontalDirection(int direction, int rotation) {
        return (direction + rotation) & 3;
    }

    private static int directionFromLegacyDir(int dir) {
        return switch (dir & 3) {
            case 0 -> 2;
            case 1 -> 1;
            case 2 -> 0;
            default -> 3;
        };
    }

    private static int legacyDirFromDirection(int direction) {
        return switch (direction & 3) {
            case 0 -> 2;
            case 1 -> 1;
            case 2 -> 0;
            default -> 3;
        };
    }

    private static NBTTagList doubleList(double x, double y, double z) {
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagDouble(x));
        list.appendTag(new NBTTagDouble(y));
        list.appendTag(new NBTTagDouble(z));
        return list;
    }

    private static NBTTagList floatList(float yaw, float pitch) {
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagFloat(yaw));
        list.appendTag(new NBTTagFloat(pitch));
        return list;
    }

    private static int placedSizeX(int sizeX, int sizeZ, int rotation) {
        return (rotation & 1) == 0 ? sizeX : sizeZ;
    }

    private static int placedSizeZ(int sizeX, int sizeZ, int rotation) {
        return (rotation & 1) == 0 ? sizeZ : sizeX;
    }

    private static int normalizeRotation(int rotation) {
        if (rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("Structure rotation must be between 0 and 3: " + rotation);
        }
        return rotation;
    }

    private static void rotatePlacedBlock(Block block, WorldServer world, int wx, int wy, int wz, String blockName,
        int rotation, boolean strict) throws TemplateException {
        if (rotation == 0) {
            return;
        }
        for (int i = 0; i < rotation; i++) {
            try {
                block.rotateBlock(world, wx, wy, wz, ForgeDirection.UP);
            } catch (RuntimeException e) {
                handleTemplateError(
                    strict,
                    "Failed to rotate block '" + blockName
                        + "' at ("
                        + wx
                        + ","
                        + wy
                        + ","
                        + wz
                        + "): "
                        + errorMessage(e),
                    e);
                return;
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

    private static void ensureChunksLoaded(WorldServer world, int originX, int originZ, int sizeX, int sizeZ) {

        int chunkMinX = originX >> 4;
        int chunkMaxX = (originX + sizeX - 1) >> 4;
        int chunkMinZ = originZ >> 4;
        int chunkMaxZ = (originZ + sizeZ - 1) >> 4;

        if (world.getChunkProvider() instanceof ChunkProviderServer cps) {
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
