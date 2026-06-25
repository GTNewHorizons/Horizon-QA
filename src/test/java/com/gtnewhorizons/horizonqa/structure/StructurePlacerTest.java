package com.gtnewhorizons.horizonqa.structure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

public class StructurePlacerTest {

    @Test
    public void placedSizeSwapsHorizontalAxesForQuarterTurns() {
        HybridStructureTemplate template = template(2, 1, 3);

        assertEquals(2, StructurePlacer.placedSizeX(template, 0));
        assertEquals(3, StructurePlacer.placedSizeZ(template, 0));
        assertEquals(3, StructurePlacer.placedSizeX(template, 1));
        assertEquals(2, StructurePlacer.placedSizeZ(template, 1));
        assertEquals(2, StructurePlacer.placedSizeX(template, 2));
        assertEquals(3, StructurePlacer.placedSizeZ(template, 2));
        assertEquals(3, StructurePlacer.placedSizeX(template, 3));
        assertEquals(2, StructurePlacer.placedSizeZ(template, 3));
    }

    @Test
    public void rotationMapsSourceCoordinatesIntoRotatedBounds() {
        assertRotated(0, 0, 0, 0, 0);
        assertRotated(0, 1, 2, 1, 2);

        assertRotated(1, 0, 0, 2, 0);
        assertRotated(1, 1, 0, 2, 1);
        assertRotated(1, 0, 2, 0, 0);
        assertRotated(1, 1, 2, 0, 1);

        assertRotated(2, 0, 0, 1, 2);
        assertRotated(2, 1, 2, 0, 0);

        assertRotated(3, 0, 0, 0, 1);
        assertRotated(3, 1, 0, 0, 0);
        assertRotated(3, 0, 2, 2, 1);
        assertRotated(3, 1, 2, 2, 0);
    }

    @Test
    public void exportRelativizesEntityCoordinatesAndStripsUuid() {
        NBTTagCompound entityNbt = new NBTTagCompound();
        entityNbt.setTag("Pos", doubleList(12.5D, 65.0D, 33.25D));
        entityNbt.setInteger("TileX", 12);
        entityNbt.setInteger("TileY", 65);
        entityNbt.setInteger("TileZ", 33);
        entityNbt.setShort("xTile", (short) 13);
        entityNbt.setShort("yTile", (short) 66);
        entityNbt.setShort("zTile", (short) 34);
        entityNbt.setLong("UUIDMost", 1L);
        entityNbt.setLong("UUIDLeast", 2L);

        StructureExporter.relativizeEntityNbt(entityNbt, 10, 64, 30);

        NBTTagList pos = entityNbt.getTagList("Pos", 6);
        assertEquals(2.5D, pos.func_150309_d(0), 0.0D);
        assertEquals(1.0D, pos.func_150309_d(1), 0.0D);
        assertEquals(3.25D, pos.func_150309_d(2), 0.0D);
        assertEquals(2, entityNbt.getInteger("TileX"));
        assertEquals(1, entityNbt.getInteger("TileY"));
        assertEquals(3, entityNbt.getInteger("TileZ"));
        assertEquals(3, entityNbt.getShort("xTile"));
        assertEquals(2, entityNbt.getShort("yTile"));
        assertEquals(4, entityNbt.getShort("zTile"));
        assertFalse(entityNbt.hasKey("UUIDMost"));
        assertFalse(entityNbt.hasKey("UUIDLeast"));
    }

    @Test
    public void placementRotatesEntityNbtIntoWorldCoordinates() {
        NBTTagCompound sourceNbt = new NBTTagCompound();
        sourceNbt.setString("id", "Item");
        sourceNbt.setTag("Pos", doubleList(0.5D, 1.0D, 2.5D));
        sourceNbt.setTag("Motion", doubleList(1.0D, 2.0D, 3.0D));
        sourceNbt.setTag("Rotation", floatList(15.0F, 10.0F));
        sourceNbt.setInteger("TileX", 0);
        sourceNbt.setInteger("TileY", 1);
        sourceNbt.setInteger("TileZ", 2);
        sourceNbt.setShort("xTile", (short) 1);
        sourceNbt.setShort("yTile", (short) 1);
        sourceNbt.setShort("zTile", (short) 0);
        sourceNbt.setByte("Direction", (byte) 0);
        sourceNbt.setByte("Dir", (byte) 2);

        NBTTagCompound placedNbt = StructurePlacer.entityNbtForPlacement(sourceNbt, 2, 3, 10, 64, 30, 1);

        NBTTagList pos = placedNbt.getTagList("Pos", 6);
        assertEquals(10.5D, pos.func_150309_d(0), 0.0D);
        assertEquals(65.0D, pos.func_150309_d(1), 0.0D);
        assertEquals(30.5D, pos.func_150309_d(2), 0.0D);

        NBTTagList motion = placedNbt.getTagList("Motion", 6);
        assertEquals(-3.0D, motion.func_150309_d(0), 0.0D);
        assertEquals(2.0D, motion.func_150309_d(1), 0.0D);
        assertEquals(1.0D, motion.func_150309_d(2), 0.0D);

        NBTTagList rotation = placedNbt.getTagList("Rotation", 5);
        assertEquals(105.0F, rotation.func_150308_e(0), 0.0F);
        assertEquals(10.0F, rotation.func_150308_e(1), 0.0F);

        assertEquals(10, placedNbt.getInteger("TileX"));
        assertEquals(65, placedNbt.getInteger("TileY"));
        assertEquals(30, placedNbt.getInteger("TileZ"));
        assertEquals(12, placedNbt.getShort("xTile"));
        assertEquals(65, placedNbt.getShort("yTile"));
        assertEquals(31, placedNbt.getShort("zTile"));
        assertEquals(1, placedNbt.getByte("Direction"));
        assertEquals(1, placedNbt.getByte("Dir"));

        NBTTagList sourcePos = sourceNbt.getTagList("Pos", 6);
        assertEquals(0.5D, sourcePos.func_150309_d(0), 0.0D);
    }

    @Test
    public void strictPlacementRejectsUnknownBlocksBeforePlacing() {
        HybridStructureTemplate.PaletteEntry[] palette = {
            new HybridStructureTemplate.PaletteEntry("horizonqatest:missing_block", 0) };
        int[][][] blockData = new int[1][1][1];
        HybridStructureTemplate template = new HybridStructureTemplate(
            1,
            1,
            1,
            palette,
            new char[] { 'x' },
            blockData,
            new NBTTagCompound());

        TemplateException error = assertThrows(
            TemplateException.class,
            () -> StructurePlacer.placeStrict("horizonqatest:unknown_block", template, null, 0, 0, 0));

        assertTrue(
            error.getMessage()
                .contains("Unknown block 'horizonqatest:missing_block'"));
    }

    @Test
    public void verticalBoundsAllowTemplateEndingAtBuildLimit() throws Exception {
        StructurePlacer.validateVerticalBounds("horizonqatest:tall", 252, 4);
    }

    @Test
    public void verticalBoundsRejectTemplateAboveBuildLimit() {
        TemplateException error = assertThrows(
            TemplateException.class,
            () -> StructurePlacer.validateVerticalBounds("horizonqatest:tall", 253, 4));

        assertTrue(
            error.getMessage()
                .contains("would occupy Y=253..256"));
        assertTrue(
            error.getMessage()
                .contains("outside build height 0..255"));
    }

    @Test
    public void verticalBoundsRejectTemplateBelowBuildLimit() {
        TemplateException error = assertThrows(
            TemplateException.class,
            () -> StructurePlacer.validateVerticalBounds("horizonqatest:tall", -1, 1));

        assertTrue(
            error.getMessage()
                .contains("would occupy Y=-1..-1"));
    }

    private static void assertRotated(int rotation, int sourceX, int sourceZ, int expectedX, int expectedZ) {
        assertEquals(expectedX, StructurePlacer.rotatedLocalX(sourceX, sourceZ, 2, 3, rotation));
        assertEquals(expectedZ, StructurePlacer.rotatedLocalZ(sourceX, sourceZ, 2, 3, rotation));
    }

    private static HybridStructureTemplate template(int sizeX, int sizeY, int sizeZ) {
        HybridStructureTemplate.PaletteEntry[] palette = {
            new HybridStructureTemplate.PaletteEntry("minecraft:air", 0) };
        return new HybridStructureTemplate(
            sizeX,
            sizeY,
            sizeZ,
            palette,
            new char[] { HybridStructureTemplate.AIR_KEY },
            new int[sizeX][sizeY][sizeZ],
            new NBTTagCompound());
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
}
