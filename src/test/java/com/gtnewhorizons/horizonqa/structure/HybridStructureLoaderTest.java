package com.gtnewhorizons.horizonqa.structure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

public class HybridStructureLoaderTest {

    @Test
    public void missingTemplateThrowsTemplateException() {
        IOException error = assertThrows(
            TemplateException.class,
            () -> HybridStructureLoader.load("horizonqatest:missing"));

        assertTrue(
            error.getMessage()
                .contains("Structure template resource not found"));
    }

    @Test
    public void malformedTemplateThrowsTemplateException() {
        IOException error = assertThrows(
            TemplateException.class,
            () -> HybridStructureLoader.load("horizonqatest:malformed"));

        assertTrue(
            error.getMessage()
                .contains("Malformed template 'horizonqatest:malformed'"));
    }

    @Test
    public void unreadableExistingTilesNbtThrowsTemplateException() {
        IOException error = assertThrows(
            TemplateException.class,
            () -> HybridStructureLoader.load("horizonqatest:unreadable"));

        assertTrue(
            error.getMessage()
                .contains("unreadable legacy tile entity data"));
        assertTrue(
            error.getMessage()
                .contains("unreadable_tiles.nbt"));
    }

    @Test
    public void snbtDataLoadsTilesAndEntities() throws Exception {
        HybridStructureTemplate template = HybridStructureLoader.load("horizonqatest:snbt");

        NBTTagCompound tile = template.getTileEntity(0, 0, 0);
        assertEquals("TestTile", tile.getString("id"));

        NBTTagList entities = template.getEntities();
        assertEquals(1, entities.tagCount());
        assertEquals(
            "Item",
            entities.getCompoundTagAt(0)
                .getString("id"));
    }

    @Test
    public void malformedSnbtThrowsTemplateException() {
        IOException error = assertThrows(
            TemplateException.class,
            () -> HybridStructureLoader.load("horizonqatest:bad_snbt"));

        assertTrue(
            error.getMessage()
                .contains("unreadable structure data"));
        assertTrue(
            error.getMessage()
                .contains("bad_snbt.snbt"));
    }

    @Test
    public void generatedSnbtRoundTripsCombinedStructureData() throws Exception {
        NBTTagCompound tileData = new NBTTagCompound();
        NBTTagCompound tile = new NBTTagCompound();
        tile.setString("id", "TestTile");
        tile.setInteger("x", 0);
        tile.setInteger("y", 0);
        tile.setInteger("z", 0);
        tileData.setTag("0,0,0", tile);

        NBTTagCompound entityData = new NBTTagCompound();
        NBTTagList entities = new NBTTagList();
        NBTTagCompound entity = new NBTTagCompound();
        entity.setString("id", "Item");
        entity.setString("CustomName", "");
        entities.appendTag(entity);
        entityData.setTag("entities", entities);

        String snbt = StructureNbt.toSnbt(StructureNbt.combine(tileData, entityData));
        assertTrue(snbt.contains("tiles:"));
        assertTrue(snbt.contains("id: \"TestTile\""));
        assertTrue(snbt.contains("CustomName: \"\""));

        StructureNbt.StructureData structureData = StructureNbt.splitCombined(
            StructureNbt.parseSnbt(snbt, "horizonqatest:generated", "generated.snbt"),
            "horizonqatest:generated",
            "generated.snbt");

        assertEquals(
            "TestTile",
            structureData.tileData()
                .getCompoundTag("0,0,0")
                .getString("id"));
        assertEquals(
            1,
            structureData.entityData()
                .getTagList("entities", 10)
                .tagCount());
        NBTTagCompound loadedEntity = structureData.entityData()
            .getTagList("entities", 10)
            .getCompoundTagAt(0);
        assertEquals("", loadedEntity.getString("CustomName"));
        assertFalse("\"\"".equals(loadedEntity.getString("CustomName")));
    }

    @Test
    public void generatedSnbtEscapesQuotedStringValues() throws Exception {
        NBTTagCompound structureData = new NBTTagCompound();
        NBTTagList entities = new NBTTagList();
        NBTTagCompound entity = new NBTTagCompound();
        entity.setString("id", "Item");
        entity.setString("CustomName", "Bob \"The Test\"");
        entities.appendTag(entity);
        structureData.setTag("entities", entities);

        String snbt = StructureNbt.toSnbt(structureData);
        assertTrue(snbt.contains("CustomName: \"Bob \\\"The Test\\\"\""));
        assertTrue(StructureNbt.isLosslessSnbt(structureData, snbt));
        assertNotNull(StructureNbt.toSnbtIfLossless(structureData));

        NBTTagCompound parsed = StructureNbt.parseSnbt(snbt, "horizonqatest:generated", "generated.snbt");
        assertEquals(
            "Bob \"The Test\"",
            parsed.getTagList("entities", 10)
                .getCompoundTagAt(0)
                .getString("CustomName"));
    }

    @Test
    public void unsafeSnbtKeysRequireBinaryStructureData() {
        NBTTagCompound structureData = new NBTTagCompound();
        NBTTagList tiles = new NBTTagList();
        NBTTagCompound tile = new NBTTagCompound();
        NBTTagCompound forgeData = new NBTTagCompound();
        forgeData.setString("modid:foo", "bar");
        tile.setString("id", "TestTile");
        tile.setInteger("x", 0);
        tile.setInteger("y", 0);
        tile.setInteger("z", 0);
        tile.setTag("ForgeData", forgeData);
        tiles.appendTag(tile);
        structureData.setTag("tiles", tiles);

        String snbt = StructureNbt.toSnbt(structureData);
        assertFalse(StructureNbt.isLosslessSnbt(structureData, snbt));
        assertNull(StructureNbt.toSnbtIfLossless(structureData));
    }
}
