package com.gtnewhorizons.gametest.api;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.gtnewhorizons.gametest.api.annotation.Experimental;

/**
 * Traverses {@link NBTTagCompound} structures using dotted-path notation.
 * <p>
 * Paths are dot-separated keys; numeric segments index into {@link NBTTagList} entries.
 * <p>
 * Examples:
 * <ul>
 * <li>{@code "mInventory.0.id"} — first element of the "mInventory" list, then its "id" tag</li>
 * <li>{@code "CustomName"} — top-level string tag</li>
 * <li>{@code "ForgeCaps.mymod:cap.level"} — nested compound traversal (colons are valid within a segment)</li>
 * </ul>
 */
@Experimental
public final class NBTPathAccessor {

    private static final Field TAG_LIST_FIELD;

    static {
        Field f = null;
        for (Field field : NBTTagList.class.getDeclaredFields()) {
            if (List.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                f = field;
                break;
            }
        }
        TAG_LIST_FIELD = f;
    }

    private NBTPathAccessor() {}

    /**
     * Resolve a dotted path against a root compound, returning the terminal {@link NBTBase} or
     * {@code null} if any segment is missing.
     */
    public static NBTBase resolve(NBTTagCompound root, String path) {
        if (root == null || path == null || path.isEmpty()) return null;

        String[] segments = path.split("\\.");
        NBTBase current = root;

        for (String segment : segments) {
            if (current instanceof NBTTagCompound compound) {
                if (!compound.hasKey(segment)) return null;
                current = compound.getTag(segment);
            } else if (current instanceof NBTTagList list) {
                int index = parseIndex(segment);
                if (index < 0 || index >= list.tagCount()) return null;
                current = getListElement(list, index);
                if (current == null) return null;
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * Resolve a dotted path and return the result as a string, or {@code null} if the path doesn't
     * exist. Uses the tag's SNBT representation.
     */
    public static String resolveAsString(NBTTagCompound root, String path) {
        NBTBase tag = resolve(root, path);
        return tag != null ? tag.toString() : null;
    }

    /** Check whether a path exists in the given compound. */
    public static boolean exists(NBTTagCompound root, String path) {
        return resolve(root, path) != null;
    }

    @SuppressWarnings("unchecked")
    private static NBTBase getListElement(NBTTagList list, int index) {
        if (TAG_LIST_FIELD != null) {
            try {
                List<NBTBase> internal = (List<NBTBase>) TAG_LIST_FIELD.get(list);
                return internal.get(index);
            } catch (Exception ignored) {}
        }
        NBTTagCompound compound = list.getCompoundTagAt(index);
        if (compound.hasNoTags()) return null;
        return compound;
    }

    private static int parseIndex(String segment) {
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
