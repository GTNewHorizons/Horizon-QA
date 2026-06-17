package com.gtnewhorizons.horizonqa.structure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;

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
}
