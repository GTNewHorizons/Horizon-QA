package com.gtnewhorizons.horizonqa.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

final class StructureNbt {

    static final String TILES_KEY = "tiles";
    static final String ENTITIES_KEY = "entities";

    static final int TAG_LIST = 9;
    static final int TAG_COMPOUND = 10;

    static final class StructureData {

        private final NBTTagCompound tileData;
        private final NBTTagCompound entityData;

        StructureData(NBTTagCompound tileData, NBTTagCompound entityData) {
            this.tileData = tileData != null ? tileData : new NBTTagCompound();
            this.entityData = entityData != null ? entityData : new NBTTagCompound();
        }

        NBTTagCompound tileData() {
            return tileData;
        }

        NBTTagCompound entityData() {
            return entityData;
        }
    }

    private StructureNbt() {}

    static StructureData empty() {
        return new StructureData(null, null);
    }

    static NBTTagCompound combine(NBTTagCompound tileData, NBTTagCompound entityData) {
        NBTTagCompound combined = new NBTTagCompound();
        if (!isEmpty(tileData)) {
            combined.setTag(TILES_KEY, tileList(tileData));
        }

        NBTTagList entities = entityList(entityData);
        if (entities.tagCount() > 0) {
            combined.setTag(ENTITIES_KEY, entities.copy());
        }
        return combined;
    }

    static StructureData splitCombined(NBTTagCompound combined, String templateName, String resource)
        throws TemplateException {
        NBTTagCompound tileData = new NBTTagCompound();
        NBTTagCompound entityData = new NBTTagCompound();

        if (combined.hasKey(TILES_KEY, TAG_LIST)) {
            tileData = tileDataFromList(combined.getTagList(TILES_KEY, TAG_COMPOUND), templateName, resource);
        } else if (combined.hasKey(TILES_KEY, TAG_COMPOUND)) {
            tileData = combined.getCompoundTag(TILES_KEY);
        } else if (combined.hasKey(TILES_KEY)) {
            throw malformedStructureData(templateName, resource, "'" + TILES_KEY + "' must be a list or compound");
        }

        if (combined.hasKey(ENTITIES_KEY, TAG_LIST)) {
            entityData.setTag(ENTITIES_KEY, combined.getTagList(ENTITIES_KEY, TAG_COMPOUND));
        } else if (combined.hasKey(ENTITIES_KEY)) {
            throw malformedStructureData(templateName, resource, "'" + ENTITIES_KEY + "' must be a list");
        }

        return new StructureData(tileData, entityData);
    }

    private static NBTTagList tileList(NBTTagCompound tileData) {
        NBTTagList tiles = new NBTTagList();
        List<String> keys = sortedKeys(tileData);
        for (String key : keys) {
            tiles.appendTag(tileData.getCompoundTag(key)
                .copy());
        }
        return tiles;
    }

    private static NBTTagCompound tileDataFromList(NBTTagList tiles, String templateName, String resource)
        throws TemplateException {
        NBTTagCompound tileData = new NBTTagCompound();
        for (int i = 0; i < tiles.tagCount(); i++) {
            NBTTagCompound tile = tiles.getCompoundTagAt(i);
            if (!tile.hasKey("x") || !tile.hasKey("y") || !tile.hasKey("z")) {
                throw malformedStructureData(
                    templateName,
                    resource,
                    "'" + TILES_KEY + "' entry " + i + " must contain x, y, and z");
            }
            String key = tile.getInteger("x") + "," + tile.getInteger("y") + "," + tile.getInteger("z");
            tileData.setTag(key, tile.copy());
        }
        return tileData;
    }

    static NBTTagCompound parseSnbt(String snbt, String templateName, String resource) throws TemplateException {
        try {
            NBTBase parsed = JsonToNBT.func_150315_a(snbt);
            if (!(parsed instanceof NBTTagCompound compound)) {
                throw malformedStructureData(templateName, resource, "root tag must be a compound");
            }
            return compound;
        } catch (NBTException | RuntimeException e) {
            throw new TemplateException(
                "Template '" + templateName + "' has unreadable structure data " + resource + ": "
                    + errorMessage(e),
                e);
        }
    }

    static String toSnbt(NBTTagCompound compound) {
        StringBuilder builder = new StringBuilder();
        appendTag(compound, builder, 0);
        builder.append('\n');
        return builder.toString();
    }

    static int tileEntityCount(NBTTagCompound tileData) {
        if (tileData == null) {
            return 0;
        }
        return tileData.func_150296_c()
            .size();
    }

    static int entityCount(NBTTagCompound entityData) {
        return entityList(entityData).tagCount();
    }

    static NBTTagList entityList(NBTTagCompound entityData) {
        if (entityData == null) {
            return new NBTTagList();
        }
        return entityData.getTagList(ENTITIES_KEY, TAG_COMPOUND);
    }

    static boolean isEmpty(NBTTagCompound compound) {
        return compound == null || compound.hasNoTags();
    }

    private static TemplateException malformedStructureData(String templateName, String resource, String message) {
        return new TemplateException(
            "Template '" + templateName + "' has malformed structure data " + resource + ": " + message);
    }

    private static void appendTag(NBTBase tag, StringBuilder builder, int indent) {
        if (tag instanceof NBTTagCompound compound) {
            appendCompound(compound, builder, indent);
        } else if (tag instanceof NBTTagList list) {
            appendList(list, builder, indent);
        } else {
            builder.append(tag);
        }
    }

    private static void appendCompound(NBTTagCompound compound, StringBuilder builder, int indent) {
        List<String> keys = sortedKeys(compound);

        if (keys.isEmpty()) {
            builder.append("{}");
            return;
        }

        builder.append("{\n");
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            indent(builder, indent + 1);
            builder.append(key);
            builder.append(": ");
            appendTag(compound.getTag(key), builder, indent + 1);
            if (i < keys.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        indent(builder, indent);
        builder.append('}');
    }

    private static void appendList(NBTTagList list, StringBuilder builder, int indent) {
        List<NBTBase> tags = new ArrayList<>();
        NBTTagList copy = (NBTTagList) list.copy();
        while (copy.tagCount() > 0) {
            tags.add(copy.removeTag(0));
        }

        if (tags.isEmpty()) {
            builder.append("[]");
            return;
        }

        boolean multiline = false;
        for (NBTBase tag : tags) {
            byte id = tag.getId();
            if (id == TAG_COMPOUND || id == TAG_LIST) {
                multiline = true;
                break;
            }
        }

        if (!multiline) {
            builder.append('[');
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                appendTag(tags.get(i), builder, indent);
            }
            builder.append(']');
            return;
        }

        builder.append("[\n");
        for (int i = 0; i < tags.size(); i++) {
            indent(builder, indent + 1);
            appendTag(tags.get(i), builder, indent + 1);
            if (i < tags.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        indent(builder, indent);
        builder.append(']');
    }

    private static List<String> sortedKeys(NBTTagCompound compound) {
        List<String> keys = new ArrayList<>();
        for (Object key : compound.func_150296_c()) {
            keys.add(String.valueOf(key));
        }
        Collections.sort(keys);
        return keys;
    }

    private static void indent(StringBuilder builder, int indent) {
        for (int i = 0; i < indent; i++) {
            builder.append("  ");
        }
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
