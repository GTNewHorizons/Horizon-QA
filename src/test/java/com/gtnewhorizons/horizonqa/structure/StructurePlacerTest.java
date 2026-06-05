package com.gtnewhorizons.horizonqa.structure;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class StructurePlacerTest {

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
}
