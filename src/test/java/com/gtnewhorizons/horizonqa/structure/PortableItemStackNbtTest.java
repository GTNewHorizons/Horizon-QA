package com.gtnewhorizons.horizonqa.structure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

public class PortableItemStackNbtTest {

    private static final String SPAWN_EGG = "minecraft:spawn_egg";
    private static final int SPAWN_EGG_ID = 383;
    private static final String EXTENDED_ITEM = "horizonqatest:extended_item";
    private static final int EXTENDED_ITEM_ID = 70_000;

    private static final PortableItemStackNbt.ItemIdentityCodec CODEC = new PortableItemStackNbt.ItemIdentityCodec() {

        @Override
        public NBTTagCompound nativeIdentityFor(String registryName) {
            NBTTagCompound identity = new NBTTagCompound();
            if (SPAWN_EGG.equals(registryName)) {
                identity.setShort("id", (short) SPAWN_EGG_ID);
                return identity;
            }
            if (EXTENDED_ITEM.equals(registryName)) {
                identity.setShort("id", (short) 0);
                identity.setInteger("idExt", EXTENDED_ITEM_ID);
                return identity;
            }
            return null;
        }
    };

    @Test
    public void markedNativeShortIdEncodesAndPortableNameHydrates() throws Exception {
        NBTTagCompound source = capturedStack(SPAWN_EGG, SPAWN_EGG_ID, 1, 93);
        source.setByte("Slot", (byte) 4);
        NBTTagCompound itemTag = new NBTTagCompound();
        itemTag.setString("marker", "preserved");
        source.setTag("tag", itemTag);

        NBTTagCompound encoded = PortableItemStackNbt.encodeForTemplate(source);

        assertFalse(encoded.hasKey("id"));
        assertEquals(SPAWN_EGG, encoded.getString(PortableItemStackNbt.PORTABLE_ID_KEY));
        assertEquals(1, encoded.getByte("Count"));
        assertEquals(93, encoded.getShort("Damage"));
        assertEquals(4, encoded.getByte("Slot"));
        assertEquals(
            "preserved",
            encoded.getCompoundTag("tag")
                .getString("marker"));
        assertTrue(source.hasKey("id", 99));
        assertFalse(source.hasKey("id", 8));

        NBTTagCompound hydrated = PortableItemStackNbt.decodeForRuntime(
            encoded,
            HybridStructureTemplate.CURRENT_FORMAT_VERSION,
            "horizonqatest:portable",
            "$.Item",
            CODEC);

        assertTrue(hydrated.hasKey("id", 99));
        assertEquals(
            2,
            hydrated.getTag("id")
                .getId());
        assertEquals(SPAWN_EGG_ID, hydrated.getShort("id"));
        assertEquals(1, hydrated.getByte("Count"));
        assertEquals(93, hydrated.getShort("Damage"));
        assertEquals(4, hydrated.getByte("Slot"));
        assertEquals(
            "preserved",
            hydrated.getCompoundTag("tag")
                .getString("marker"));
        assertEquals(SPAWN_EGG, encoded.getString(PortableItemStackNbt.PORTABLE_ID_KEY));
    }

    @Test
    public void transformsItemStacksRecursivelyThroughCompoundsAndLists() throws Exception {
        NBTTagCompound inventoryStack = capturedStack(SPAWN_EGG, SPAWN_EGG_ID, 2, 5);
        NBTTagCompound nestedStack = extendedCapturedStack(3, 7);
        NBTTagCompound inventoryTag = new NBTTagCompound();
        inventoryTag.setTag("Contained", nestedStack);
        inventoryStack.setTag("tag", inventoryTag);

        NBTTagList inventory = new NBTTagList();
        inventory.appendTag(inventoryStack);
        NBTTagCompound tile = new NBTTagCompound();
        tile.setTag("Items", inventory);
        NBTTagList tiles = new NBTTagList();
        tiles.appendTag(tile);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("tiles", tiles);

        NBTTagCompound encoded = PortableItemStackNbt.encodeForTemplate(root);
        NBTTagCompound encodedInventoryStack = encoded.getTagList("tiles", 10)
            .getCompoundTagAt(0)
            .getTagList("Items", 10)
            .getCompoundTagAt(0);
        NBTTagCompound encodedNestedStack = encodedInventoryStack.getCompoundTag("tag")
            .getCompoundTag("Contained");

        assertEquals(SPAWN_EGG, encodedInventoryStack.getString(PortableItemStackNbt.PORTABLE_ID_KEY));
        assertEquals(EXTENDED_ITEM, encodedNestedStack.getString(PortableItemStackNbt.PORTABLE_ID_KEY));
        assertFalse(encodedNestedStack.hasKey("idExt"));

        NBTTagCompound hydrated = PortableItemStackNbt.decodeForRuntime(
            encoded,
            HybridStructureTemplate.CURRENT_FORMAT_VERSION,
            "horizonqatest:nested",
            "$",
            CODEC);
        NBTTagCompound hydratedInventoryStack = hydrated.getTagList("tiles", 10)
            .getCompoundTagAt(0)
            .getTagList("Items", 10)
            .getCompoundTagAt(0);
        NBTTagCompound hydratedNestedStack = hydratedInventoryStack.getCompoundTag("tag")
            .getCompoundTag("Contained");

        assertEquals(SPAWN_EGG_ID, hydratedInventoryStack.getShort("id"));
        assertEquals(0, hydratedNestedStack.getShort("id"));
        assertEquals(EXTENDED_ITEM_ID, hydratedNestedStack.getInteger("idExt"));
    }

    @Test
    public void numericIdsOutsideCompleteItemStacksRemainUntouched() throws Exception {
        NBTTagCompound entity = new NBTTagCompound();
        entity.setInteger("id", 42);
        entity.setInteger("Count", 1);

        NBTTagCompound enchantment = new NBTTagCompound();
        enchantment.setShort("id", (short) 16);
        enchantment.setShort("lvl", (short) 3);
        NBTTagList enchantments = new NBTTagList();
        enchantments.appendTag(enchantment);

        NBTTagCompound modPrivateData = new NBTTagCompound();
        modPrivateData.setInteger("mID", SPAWN_EGG_ID);
        modPrivateData.setTag("ench", enchantments);

        NBTTagCompound root = new NBTTagCompound();
        root.setTag("entity", entity);
        root.setTag("private", modPrivateData);

        assertEquals(root, PortableItemStackNbt.encodeForTemplate(root));
        assertEquals(
            root,
            PortableItemStackNbt.decodeForRuntime(
                root,
                HybridStructureTemplate.CURRENT_FORMAT_VERSION,
                "horizonqatest:false_positive",
                "$",
                CODEC));
    }

    @Test
    public void arbitraryCompoundWithItemLikeFieldNamesRemainsUntouched() throws Exception {
        NBTTagCompound tile = new NBTTagCompound();
        tile.setString("id", "ExampleTile");
        tile.setInteger("Count", 4);
        tile.setInteger("Damage", 2);

        assertEquals(tile, PortableItemStackNbt.encodeForTemplate(tile));
        assertEquals(
            tile,
            PortableItemStackNbt.decodeForRuntime(
                tile,
                HybridStructureTemplate.CURRENT_FORMAT_VERSION,
                "horizonqatest:item_like_tile",
                "$.tiles[0]",
                CODEC));
    }

    @Test
    public void integerCountAndModPrivateFieldsSurviveBothDirections() throws Exception {
        NBTTagCompound source = capturedStack(SPAWN_EGG, SPAWN_EGG_ID, 1, 12);
        source.setInteger("Count", 1_024);
        source.setInteger("mID", 8_192);

        NBTTagCompound encoded = PortableItemStackNbt.encodeForTemplate(source);
        assertEquals(
            3,
            encoded.getTag("Count")
                .getId());
        assertEquals(1_024, encoded.getInteger("Count"));
        assertEquals(8_192, encoded.getInteger("mID"));

        NBTTagCompound hydrated = PortableItemStackNbt.decodeForRuntime(
            encoded,
            HybridStructureTemplate.CURRENT_FORMAT_VERSION,
            "horizonqatest:integer_count",
            "$",
            CODEC);
        assertEquals(
            3,
            hydrated.getTag("Count")
                .getId());
        assertEquals(1_024, hydrated.getInteger("Count"));
        assertEquals(8_192, hydrated.getInteger("mID"));
    }

    @Test
    public void markedUnregisteredItemFailsExport() {
        NBTTagCompound source = capturedStack("", SPAWN_EGG_ID, 1, 0);

        IOException error = assertThrows(IOException.class, () -> PortableItemStackNbt.encodeForTemplate(source));

        assertTrue(
            error.getMessage()
                .contains("item is not registered"));
    }

    @Test
    public void missingRegistryNameReportsTemplateAndExactNbtPath() {
        NBTTagCompound item = portableStack("missingmod:missing_item", 1, 0);
        NBTTagCompound entity = new NBTTagCompound();
        entity.setTag("Item", item);
        NBTTagList entities = new NBTTagList();
        entities.appendTag(entity);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("entities", entities);

        TemplateException error = assertThrows(
            TemplateException.class,
            () -> PortableItemStackNbt.decodeForRuntime(
                root,
                HybridStructureTemplate.CURRENT_FORMAT_VERSION,
                "horizonqatest:missing_item",
                "$",
                CODEC));

        assertTrue(
            error.getMessage()
                .contains("horizonqatest:missing_item"));
        assertTrue(
            error.getMessage()
                .contains("missingmod:missing_item"));
        assertTrue(
            error.getMessage()
                .contains("$.entities[0].Item"));
        assertEquals("missingmod:missing_item", item.getString(PortableItemStackNbt.PORTABLE_ID_KEY));
    }

    @Test
    public void legacyNumericItemStackIsRejectedByDefault() {
        NBTTagCompound source = nativeStack(SPAWN_EGG_ID, 1, 93);

        TemplateException error = assertThrows(
            TemplateException.class,
            () -> PortableItemStackNbt.decodeForRuntime(
                source,
                HybridStructureTemplate.LEGACY_FORMAT_VERSION,
                "horizonqatest:legacy",
                "$.entities[0].Item",
                CODEC));

        assertTrue(
            error.getMessage()
                .contains("format_version 1"));
        assertTrue(
            error.getMessage()
                .contains("unsafe numeric ItemStack ID"));
        assertTrue(
            error.getMessage()
                .contains("$.entities[0].Item"));
    }

    @Test
    public void legacyAe2CableBusStackReportsItsExactNestedPath() {
        NBTTagCompound definition = nativeStack(4_150, 1, 240);
        NBTTagCompound cableBus = new NBTTagCompound();
        cableBus.setTag("def:4", definition);
        NBTTagList tiles = new NBTTagList();
        tiles.appendTag(cableBus);
        NBTTagCompound source = new NBTTagCompound();
        source.setTag("tiles", tiles);

        TemplateException error = assertThrows(
            TemplateException.class,
            () -> PortableItemStackNbt.decodeForRuntime(
                source,
                HybridStructureTemplate.LEGACY_FORMAT_VERSION,
                "horizonqatest:legacy_ae2_cable_bus",
                "$",
                CODEC));

        assertTrue(
            error.getMessage()
                .contains("unsafe numeric ItemStack ID"));
        assertTrue(
            error.getMessage()
                .contains("$.tiles[0][\"def:4\"]"));
    }

    @Test
    public void currentFormatLeavesUnmarkedNumericCompoundUntouched() throws Exception {
        NBTTagCompound source = nativeStack(SPAWN_EGG_ID, 1, 93);

        assertEquals(source, PortableItemStackNbt.encodeForTemplate(source));
        assertEquals(
            source,
            PortableItemStackNbt.decodeForRuntime(
                source,
                HybridStructureTemplate.CURRENT_FORMAT_VERSION,
                "horizonqatest:numeric_v2",
                "$",
                CODEC));
    }

    @Test
    public void trustedLegacyFormatCopiesNumericItemStacksWithoutResolvingThem() throws Exception {
        NBTTagCompound source = nativeStack(SPAWN_EGG_ID, 1, 93);

        NBTTagCompound prepared = PortableItemStackNbt.decodeForRuntime(
            source,
            HybridStructureTemplate.LEGACY_FORMAT_VERSION,
            true,
            "horizonqatest:runtime",
            "$",
            new PortableItemStackNbt.ItemIdentityCodec() {

                @Override
                public NBTTagCompound nativeIdentityFor(String registryName) {
                    throw new AssertionError("Runtime-native data must not be hydrated");
                }
            });

        assertEquals(source, prepared);
        prepared.setByte("Count", (byte) 2);
        assertEquals(1, source.getByte("Count"));
    }

    @Test
    public void extendedIdentityRoundTripsUsingRuntimeDefinedIdFields() throws Exception {
        NBTTagCompound source = extendedCapturedStack(4, 11);
        source.setInteger("Count", 512);

        NBTTagCompound encoded = PortableItemStackNbt.encodeForTemplate(source);
        assertEquals(EXTENDED_ITEM, encoded.getString(PortableItemStackNbt.PORTABLE_ID_KEY));
        assertFalse(encoded.hasKey("idExt"));
        assertEquals(
            3,
            encoded.getTag("Count")
                .getId());
        assertEquals(512, encoded.getInteger("Count"));

        NBTTagCompound hydrated = PortableItemStackNbt.decodeForRuntime(
            encoded,
            HybridStructureTemplate.CURRENT_FORMAT_VERSION,
            "horizonqatest:extended",
            "$",
            CODEC);
        assertEquals(0, hydrated.getShort("id"));
        assertEquals(EXTENDED_ITEM_ID, hydrated.getInteger("idExt"));
        assertEquals(
            3,
            hydrated.getTag("Count")
                .getId());
        assertEquals(512, hydrated.getInteger("Count"));
        assertEquals(11, hydrated.getShort("Damage"));
    }

    @Test
    public void registryNameBridgesDifferentNumericIdsAcrossEnvironments() throws Exception {
        String registryName = "examplemod:portable_item";
        NBTTagCompound source = capturedStack(registryName, 101, 6, 42);

        NBTTagCompound encoded = PortableItemStackNbt.encodeForTemplate(source);

        assertEquals(registryName, encoded.getString(PortableItemStackNbt.PORTABLE_ID_KEY));
        NBTTagCompound hydrated = PortableItemStackNbt.decodeForRuntime(
            encoded,
            HybridStructureTemplate.CURRENT_FORMAT_VERSION,
            "horizonqatest:cross_environment",
            "$.entities[0].Item",
            new PortableItemStackNbt.ItemIdentityCodec() {

                @Override
                public NBTTagCompound nativeIdentityFor(String name) {
                    if (!registryName.equals(name)) {
                        return null;
                    }
                    NBTTagCompound identity = new NBTTagCompound();
                    identity.setShort("id", (short) 0);
                    identity.setInteger("idExt", 50_001);
                    return identity;
                }
            });

        assertEquals(0, hydrated.getShort("id"));
        assertEquals(50_001, hydrated.getInteger("idExt"));
        assertEquals(6, hydrated.getByte("Count"));
        assertEquals(42, hydrated.getShort("Damage"));
        assertEquals(101, source.getShort("id"));
    }

    private static NBTTagCompound nativeStack(int itemId, int count, int damage) {
        NBTTagCompound stack = new NBTTagCompound();
        stack.setShort("id", (short) itemId);
        stack.setByte("Count", (byte) count);
        stack.setShort("Damage", (short) damage);
        return stack;
    }

    private static NBTTagCompound extendedNativeStack(int count, int damage) {
        NBTTagCompound stack = nativeStack(0, count, damage);
        stack.setInteger("idExt", EXTENDED_ITEM_ID);
        return stack;
    }

    private static NBTTagCompound capturedStack(String registryName, int itemId, int count, int damage) {
        NBTTagCompound stack = nativeStack(itemId, count, damage);
        stack.setString(PortableItemStackNbt.PORTABLE_ID_KEY, registryName);
        return stack;
    }

    private static NBTTagCompound extendedCapturedStack(int count, int damage) {
        NBTTagCompound stack = extendedNativeStack(count, damage);
        stack.setString(PortableItemStackNbt.PORTABLE_ID_KEY, EXTENDED_ITEM);
        return stack;
    }

    private static NBTTagCompound portableStack(String registryName, int count, int damage) {
        NBTTagCompound stack = new NBTTagCompound();
        stack.setString(PortableItemStackNbt.PORTABLE_ID_KEY, registryName);
        stack.setByte("Count", (byte) count);
        stack.setShort("Damage", (short) damage);
        return stack;
    }
}
