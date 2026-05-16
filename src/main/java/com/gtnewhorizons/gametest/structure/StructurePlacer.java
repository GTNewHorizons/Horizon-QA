package com.gtnewhorizons.gametest.structure;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class StructurePlacer {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private StructurePlacer() {}

    public static void place(HybridStructureTemplate template, WorldServer world, int originX, int originY,
        int originZ) {

        HybridStructureTemplate.PaletteEntry[] palette = template.getPalette();
        int sizeX = template.getSizeX();
        int sizeY = template.getSizeY();
        int sizeZ = template.getSizeZ();

        // Flag 1 triggers onBlockAdded, which GT machine blocks need to bind the MetaTileEntity
        // to the BaseMetaTileEntity wrapper — without it isInvalid() returns true and setTileEntity
        // silently drops the TE, making getTileEntity return null in the NBT pass below.
        int notifyAll = 3;
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
                    world.setBlock(originX + x, originY + y, originZ + z, block, entry.meta, notifyAll);
                }
            }
        }

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    NBTTagCompound teNbt = template.getTileEntity(x, y, z);
                    if (teNbt == null) continue;

                    int wx = originX + x;
                    int wy = originY + y;
                    int wz = originZ + z;

                    TileEntity te = world.getTileEntity(wx, wy, wz);
                    if (te == null) {
                        LOG.warn(
                            "StructurePlacer: no TileEntity at ({},{},{}) after block placement "
                                + "— skipping NBT injection",
                            wx,
                            wy,
                            wz);
                        continue;
                    }

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
