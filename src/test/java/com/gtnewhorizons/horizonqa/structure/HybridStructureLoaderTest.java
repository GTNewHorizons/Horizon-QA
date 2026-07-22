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
    public void missingFormatVersionDefaultsToItemFreeLegacyFormat() throws Exception {
        HybridStructureTemplate template = HybridStructureLoader.load("horizonqatest:missing_version");

        assertEquals(
            0,
            template.getEntities()
                .tagCount());
    }

    @Test
    public void legacyNumericItemStackIsRejectedByDefault() {
        TemplateException error = assertThrows(
            TemplateException.class,
            () -> HybridStructureLoader.load("horizonqatest:legacy_numeric_stack"));

        assertTrue(
            error.getMessage()
                .contains("format_version 1"));
        assertTrue(
            error.getMessage()
                .contains("unsafe numeric ItemStack ID"));
        assertTrue(
            error.getMessage()
                .contains("$.entities[0].Item"));
        assertTrue(
            error.getMessage()
                .contains("-Dhorizonqa.allowLegacyNumericItemIds=true"));
    }

    @Test
    public void trustedLegacyNumericItemStackStaysRuntimeNativeForMigration() throws Exception {
        HybridStructureTemplate template = HybridStructureLoader.load("horizonqatest:legacy_numeric_stack", true);

        NBTTagCompound item = template.getEntities()
            .getCompoundTagAt(0)
            .getCompoundTag("Item");
        assertTrue(item.hasKey("id", 99));
        assertFalse(item.hasKey("id", 8));
        assertEquals(383, item.getShort("id"));
        assertEquals(1, item.getByte("Count"));
        assertEquals(93, item.getShort("Damage"));
    }

    @Test
    public void currentFormatPreservesUnmarkedNumericItemLikeData() throws Exception {
        HybridStructureTemplate template = HybridStructureLoader.load("horizonqatest:current_numeric_stack");

        NBTTagCompound modData = template.getEntities()
            .getCompoundTagAt(0)
            .getCompoundTag("ModData");
        assertEquals(383, modData.getShort("id"));
        assertEquals(1, modData.getByte("Count"));
        assertEquals(93, modData.getShort("Damage"));
    }

    @Test
    public void missingNamedItemIsRejectedDuringTemplateLoading() {
        TemplateException error = assertThrows(
            TemplateException.class,
            () -> HybridStructureLoader.load("horizonqatest:missing_named_stack"));

        assertTrue(
            error.getMessage()
                .contains("missingmod:missing_item"));
        assertTrue(
            error.getMessage()
                .contains("$.entities[0].Item"));
    }

    @Test
    public void unsupportedFormatVersionIsRejected() {
        TemplateException error = assertThrows(
            TemplateException.class,
            () -> HybridStructureLoader.load("horizonqatest:unsupported_version"));

        assertTrue(
            error.getMessage()
                .contains("unsupported format_version 3"));
        assertTrue(
            error.getMessage()
                .contains("supported versions: 1 and 2"));
    }

    @Test
    public void nonIntegerFormatVersionIsRejected() {
        TemplateException error = assertThrows(
            TemplateException.class,
            () -> HybridStructureLoader.load("horizonqatest:non_integer_version"));

        assertTrue(
            error.getMessage()
                .contains("'format_version' must be an integer"));
    }

    @Test
    public void annotationsLoadCoordinateLabels() throws Exception {
        HybridStructureTemplate template = HybridStructureLoader.load("horizonqatest:annotated");

        assertEquals(
            new com.gtnewhorizons.horizonqa.api.TestPos(0, 0, 0),
            template.getAnnotations()
                .get("origin"));
        assertEquals(
            new com.gtnewhorizons.horizonqa.api.TestPos(1, 0, 2),
            template.getAnnotations()
                .get("corner"));
    }

    @Test
    public void invalidAnnotationNameThrowsTemplateException() {
        IOException error = assertThrows(
            TemplateException.class,
            () -> HybridStructureLoader.load("horizonqatest:bad_label_name"));

        assertTrue(
            error.getMessage()
                .contains("label 'bad-name'"));
    }

    @Test
    public void invalidAnnotationCoordinatesThrowTemplateException() {
        IOException error = assertThrows(
            TemplateException.class,
            () -> HybridStructureLoader.load("horizonqatest:bad_label_coords"));

        assertTrue(
            error.getMessage()
                .contains("must contain exactly three coordinates"));
    }

    @Test
    public void outOfBoundsAnnotationThrowsTemplateException() {
        IOException error = assertThrows(
            TemplateException.class,
            () -> HybridStructureLoader.load("horizonqatest:bad_label_bounds"));

        assertTrue(
            error.getMessage()
                .contains("points outside template bounds"));
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
