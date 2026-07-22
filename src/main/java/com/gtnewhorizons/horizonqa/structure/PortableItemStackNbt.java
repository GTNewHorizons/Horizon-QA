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

import com.gtnewhorizons.horizonqa.internal.ItemStackExportCapture;

import cpw.mods.fml.common.registry.GameData;

/** Converts scoped export markers and portable identities; numeric shape detection is legacy-only. */
final class PortableItemStackNbt {

    static final String PORTABLE_ID_KEY = ItemStackExportCapture.PORTABLE_ID_KEY;

    private static final int TAG_STRING = 8;
    private static final int TAG_ANY_NUMERIC = 99;

    private static final ItemIdentityCodec RUNTIME_CODEC = new RuntimeItemIdentityCodec();

    interface ItemIdentityCodec {

        NBTTagCompound nativeIdentityFor(String registryName);
    }

    private enum Operation {
        ENCODE,
        DECODE
    }

    private PortableItemStackNbt() {}

    static NBTTagCompound encodeForTemplate(NBTTagCompound root) throws IOException {
        try {
            return transform(
                root,
                "$",
                Operation.ENCODE,
                HybridStructureTemplate.CURRENT_FORMAT_VERSION,
                false,
                null,
                null);
        } catch (TemplateException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    static NBTTagCompound decodeForRuntime(NBTTagCompound root, int formatVersion, boolean trustLegacyNumericIds,
        String templateName, String rootPath) throws TemplateException {
        return decodeForRuntime(root, formatVersion, trustLegacyNumericIds, templateName, rootPath, RUNTIME_CODEC);
    }

    static NBTTagCompound decodeForRuntime(NBTTagCompound root, int formatVersion, String templateName, String rootPath,
        ItemIdentityCodec codec) throws TemplateException {
        return decodeForRuntime(root, formatVersion, false, templateName, rootPath, codec);
    }

    static NBTTagCompound decodeForRuntime(NBTTagCompound root, int formatVersion, boolean trustLegacyNumericIds,
        String templateName, String rootPath, ItemIdentityCodec codec) throws TemplateException {
        return transform(root, rootPath, Operation.DECODE, formatVersion, trustLegacyNumericIds, templateName, codec);
    }

    private static NBTTagCompound transform(NBTTagCompound root, String rootPath, Operation operation,
        int formatVersion, boolean trustLegacyNumericIds, String templateName, ItemIdentityCodec codec)
        throws TemplateException {
        if (root == null) {
            return new NBTTagCompound();
        }
        if (operation == Operation.DECODE && codec == null) {
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
        boolean portableStack = isPortableItemStack(compound);
        if (compound.hasKey(PORTABLE_ID_KEY) && !portableStack) {
            throw new TemplateException(
                "Template '" + templateName
                    + "' has invalid portable ItemStack identity at "
                    + path
                    + "; '"
                    + PORTABLE_ID_KEY
                    + "' must be a string");
        }
        if (portableStack && !hasStackPayload(compound)) {
            throw new TemplateException(
                "Template '" + templateName
                    + "' has invalid portable ItemStack at "
                    + path
                    + "; Count and Damage must be numeric");
        }
        if (operation == Operation.ENCODE) {
            if (!portableStack) {
                return;
            }
            String registryName = compound.getString(PORTABLE_ID_KEY);
            if (registryName.isEmpty()) {
                throw new TemplateException(
                    "Cannot export ItemStack at " + path + ": its item is not registered in the active environment");
            }
            compound.removeTag("id");
            compound.removeTag("idExt");
            return;
        }

        if (portableStack && hasNativeIdentity(compound)) {
            throw new TemplateException(
                "Template '" + templateName + "' has both native and portable ItemStack identities at " + path);
        }
        if (portableStack) {
            hydratePortableIdentity(compound, path, templateName, codec);
            return;
        }
        if (formatVersion != HybridStructureTemplate.LEGACY_FORMAT_VERSION || !isLegacyNumericItemStack(compound)) {
            return;
        }
        if (!trustLegacyNumericIds) {
            throw unsafeLegacyNumericIdentity(templateName, path);
        }
    }

    private static void hydratePortableIdentity(NBTTagCompound compound, String path, String templateName,
        ItemIdentityCodec codec) throws TemplateException {
        String registryName = compound.getString(PORTABLE_ID_KEY);
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

        compound.removeTag(PORTABLE_ID_KEY);
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

    private static TemplateException unsafeLegacyNumericIdentity(String templateName, String path) {
        return new TemplateException(
            "Template '" + templateName
                + "' uses format_version 1 and contains an unsafe numeric ItemStack ID at "
                + path
                + ". Re-export it as format_version 2 in the original environment. For one-time interactive "
                + "migration, enable -Dhorizonqa.allowLegacyNumericItemIds=true and use /horizonqa load.");
    }

    private static boolean isLegacyNumericItemStack(NBTTagCompound compound) {
        return hasStackPayload(compound) && hasNativeIdentity(compound);
    }

    private static boolean isPortableItemStack(NBTTagCompound compound) {
        return compound.hasKey(PORTABLE_ID_KEY, TAG_STRING);
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
