package com.gtnewhorizons.horizonqa.structure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import cpw.mods.fml.common.registry.GameData;

/** Converts ItemStack identity fields between runtime-native IDs and portable registry names. */
final class PortableItemStackNbt {

    private static final int TAG_STRING = 8;
    private static final int TAG_ANY_NUMERIC = 99;

    private static final ItemIdentityCodec RUNTIME_CODEC = new RuntimeItemIdentityCodec();

    interface ItemIdentityCodec {

        String registryNameFor(NBTTagCompound nativeStack);

        NBTTagCompound nativeIdentityFor(String registryName);
    }

    private enum Operation {
        ENCODE,
        VALIDATE,
        HYDRATE
    }

    private PortableItemStackNbt() {}

    static NBTTagCompound encodeForTemplate(NBTTagCompound root) throws IOException {
        return encodeForTemplate(root, RUNTIME_CODEC);
    }

    static NBTTagCompound encodeForTemplate(NBTTagCompound root, ItemIdentityCodec codec) throws IOException {
        try {
            return transform(
                root,
                "$",
                Operation.ENCODE,
                HybridStructureTemplate.CURRENT_FORMAT_VERSION,
                false,
                null,
                codec);
        } catch (TemplateException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    static void validateForLoading(NBTTagCompound root, int formatVersion, boolean trustLegacyNumericIds,
        String templateName) throws TemplateException {
        transform(root, "$", Operation.VALIDATE, formatVersion, trustLegacyNumericIds, templateName, RUNTIME_CODEC);
    }

    static NBTTagCompound prepareForPlacement(NBTTagCompound root, int formatVersion, String templateName,
        String rootPath) throws TemplateException {
        return prepareForPlacement(root, formatVersion, templateName, rootPath, RUNTIME_CODEC);
    }

    static NBTTagCompound prepareForPlacement(NBTTagCompound root, int formatVersion, String templateName,
        String rootPath, ItemIdentityCodec codec) throws TemplateException {
        return transform(root, rootPath, Operation.HYDRATE, formatVersion, false, templateName, codec);
    }

    private static NBTTagCompound transform(NBTTagCompound root, String rootPath, Operation operation,
        int formatVersion, boolean trustLegacyNumericIds, String templateName, ItemIdentityCodec codec)
        throws TemplateException {
        if (root == null) {
            return new NBTTagCompound();
        }
        if (codec == null) {
            throw new IllegalArgumentException("codec must not be null");
        }
        return (NBTTagCompound) transformTag(
            root,
            rootPath != null && !rootPath.isEmpty() ? rootPath : "$",
            operation,
            formatVersion,
            trustLegacyNumericIds,
            templateName,
            codec);
    }

    private static NBTBase transformTag(NBTBase source, String path, Operation operation, int formatVersion,
        boolean trustLegacyNumericIds, String templateName, ItemIdentityCodec codec) throws TemplateException {
        if (source instanceof NBTTagCompound sourceCompound) {
            NBTTagCompound compound = (NBTTagCompound) sourceCompound.copy();
            transformItemIdentity(compound, path, operation, formatVersion, trustLegacyNumericIds, templateName, codec);
            for (String key : sortedKeys(compound)) {
                compound.setTag(
                    key,
                    transformTag(
                        compound.getTag(key),
                        childPath(path, key),
                        operation,
                        formatVersion,
                        trustLegacyNumericIds,
                        templateName,
                        codec));
            }
            return compound;
        }

        if (source instanceof NBTTagList sourceList) {
            NBTTagList result = new NBTTagList();
            NBTTagList remaining = (NBTTagList) sourceList.copy();
            int index = 0;
            while (remaining.tagCount() > 0) {
                result.appendTag(
                    transformTag(
                        remaining.removeTag(0),
                        path + "[" + index + "]",
                        operation,
                        formatVersion,
                        trustLegacyNumericIds,
                        templateName,
                        codec));
                index++;
            }
            return result;
        }

        return source.copy();
    }

    private static void transformItemIdentity(NBTTagCompound compound, String path, Operation operation,
        int formatVersion, boolean trustLegacyNumericIds, String templateName, ItemIdentityCodec codec)
        throws TemplateException {
        boolean nativeStack = isNativeItemStack(compound);
        boolean portableStack = isPortableItemStack(compound);
        if (!nativeStack && !portableStack) {
            return;
        }

        if (operation == Operation.ENCODE) {
            if (nativeStack) {
                encodeNativeIdentity(compound, path, codec);
            }
            return;
        }

        if (nativeStack) {
            if (formatVersion == HybridStructureTemplate.RUNTIME_NATIVE_NBT) {
                return;
            }
            if (formatVersion == HybridStructureTemplate.LEGACY_FORMAT_VERSION && trustLegacyNumericIds) {
                return;
            }
            throw unsafeNumericIdentity(templateName, formatVersion, path);
        }

        if (operation == Operation.VALIDATE || operation == Operation.HYDRATE) {
            hydratePortableIdentity(compound, path, templateName, codec);
        }
    }

    private static void encodeNativeIdentity(NBTTagCompound compound, String path, ItemIdentityCodec codec)
        throws TemplateException {
        final String registryName;
        try {
            registryName = codec.registryNameFor(compound);
        } catch (RuntimeException e) {
            throw new TemplateException(
                "Cannot export ItemStack at " + path + ": failed to resolve its numeric item ID",
                e);
        }
        if (registryName == null || registryName.isEmpty()) {
            throw new TemplateException(
                "Cannot export ItemStack at " + path
                    + ": its numeric item ID is not registered in the active environment");
        }

        compound.removeTag("idExt");
        compound.setString("id", registryName);
    }

    private static void hydratePortableIdentity(NBTTagCompound compound, String path, String templateName,
        ItemIdentityCodec codec) throws TemplateException {
        String registryName = compound.getString("id");
        final NBTTagCompound nativeIdentity;
        try {
            nativeIdentity = codec.nativeIdentityFor(registryName);
        } catch (RuntimeException e) {
            throw new TemplateException(
                "Template '" + templateName + "' could not resolve item '" + registryName + "' at " + path,
                e);
        }
        if (nativeIdentity == null || !hasNativeIdentity(nativeIdentity)) {
            throw new TemplateException(
                "Template '" + templateName
                    + "' references missing or unserializable item '"
                    + registryName
                    + "' at "
                    + path);
        }

        compound.removeTag("id");
        compound.removeTag("idExt");
        if (nativeIdentity.hasKey("id")) {
            compound.setTag(
                "id",
                nativeIdentity.getTag("id")
                    .copy());
        }
        if (nativeIdentity.hasKey("idExt")) {
            compound.setTag(
                "idExt",
                nativeIdentity.getTag("idExt")
                    .copy());
        }
    }

    private static TemplateException unsafeNumericIdentity(String templateName, int formatVersion, String path) {
        if (formatVersion == HybridStructureTemplate.LEGACY_FORMAT_VERSION) {
            return new TemplateException(
                "Template '" + templateName
                    + "' uses format_version 1 and contains an unsafe numeric ItemStack ID at "
                    + path
                    + ". Re-export it as format_version 2 in the original environment. For one-time interactive "
                    + "migration, enable -Dhorizonqa.allowLegacyNumericItemIds=true and use /horizonqa load.");
        }
        return new TemplateException(
            "Template '" + templateName
                + "' declares format_version "
                + formatVersion
                + " but contains a numeric ItemStack ID at "
                + path
                + "; format_version 2 requires registry-name IDs");
    }

    private static boolean isNativeItemStack(NBTTagCompound compound) {
        return hasStackPayload(compound) && hasNativeIdentity(compound);
    }

    private static boolean isPortableItemStack(NBTTagCompound compound) {
        return hasStackPayload(compound) && compound.hasKey("id", TAG_STRING);
    }

    private static boolean hasStackPayload(NBTTagCompound compound) {
        return compound.hasKey("Count", TAG_ANY_NUMERIC) && compound.hasKey("Damage", TAG_ANY_NUMERIC);
    }

    private static boolean hasNativeIdentity(NBTTagCompound compound) {
        return compound.hasKey("id", TAG_ANY_NUMERIC) || compound.hasKey("idExt", TAG_ANY_NUMERIC);
    }

    private static List<String> sortedKeys(NBTTagCompound compound) {
        List<String> keys = new ArrayList<>();
        for (Object key : compound.func_150296_c()) {
            keys.add(String.valueOf(key));
        }
        Collections.sort(keys);
        return keys;
    }

    private static String childPath(String parent, String key) {
        if (key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return parent + "." + key;
        }
        return parent + "[\""
            + key.replace("\\", "\\\\")
                .replace("\"", "\\\"")
            + "\"]";
    }

    private static final class RuntimeItemIdentityCodec implements ItemIdentityCodec {

        @Override
        public String registryNameFor(NBTTagCompound nativeStack) {
            ItemStack stack = ItemStack.loadItemStackFromNBT(nativeStack);
            if (stack == null || stack.getItem() == null) {
                return null;
            }
            Object name = GameData.getItemRegistry()
                .getNameForObject(stack.getItem());
            return name != null ? name.toString() : null;
        }

        @Override
        public NBTTagCompound nativeIdentityFor(String registryName) {
            Object registered = GameData.getItemRegistry()
                .getObject(registryName);
            if (!(registered instanceof Item item)) {
                return null;
            }
            NBTTagCompound nativeStack = new NBTTagCompound();
            new ItemStack(item, 1, 0).writeToNBT(nativeStack);
            return nativeStack;
        }
    }
}
