package com.gtnewhorizons.horizonqa.structure;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public final class HybridStructureTemplate {

    public static final class PaletteEntry {

        public final String name;
        public final int meta;
        public final String label;

        public PaletteEntry(String name, int meta) {
            this(name, meta, null);
        }

        public PaletteEntry(String name, int meta, String label) {
            this.name = name;
            this.meta = meta;
            this.label = label;
        }
    }

    public static final char AIR_KEY = '.';

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final PaletteEntry[] palette;
    private final char[] paletteKeys;
    private final int[][][] blockData;
    private final NBTTagCompound tileData;
    private final NBTTagCompound entityData;

    public HybridStructureTemplate(int sizeX, int sizeY, int sizeZ, PaletteEntry[] palette, char[] paletteKeys,
        int[][][] blockData, NBTTagCompound tileData) {
        this(sizeX, sizeY, sizeZ, palette, paletteKeys, blockData, tileData, null);
    }

    public HybridStructureTemplate(int sizeX, int sizeY, int sizeZ, PaletteEntry[] palette, char[] paletteKeys,
        int[][][] blockData, NBTTagCompound tileData, NBTTagCompound entityData) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = palette;
        this.paletteKeys = paletteKeys;
        this.blockData = blockData;
        this.tileData = tileData != null ? tileData : new NBTTagCompound();
        this.entityData = entityData != null ? entityData : new NBTTagCompound();
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public PaletteEntry[] getPalette() {
        return palette;
    }

    public char[] getPaletteKeys() {
        return paletteKeys;
    }

    public int getPaletteIndex(int x, int y, int z) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) return 0;
        return blockData[x][y][z];
    }

    public NBTTagCompound getTileEntity(int x, int y, int z) {
        String key = x + "," + y + "," + z;
        if (!tileData.hasKey(key)) return null;
        return tileData.getCompoundTag(key);
    }

    public NBTTagList getEntities() {
        return entityData.getTagList("entities", 10);
    }
}
