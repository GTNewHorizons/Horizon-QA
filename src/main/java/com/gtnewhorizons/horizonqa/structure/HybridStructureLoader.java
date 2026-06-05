package com.gtnewhorizons.horizonqa.structure;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class HybridStructureLoader {

    private static final Logger LOG = LogManager.getLogger("HorizonQA");
    private static final Gson GSON = new Gson();

    private HybridStructureLoader() {}

    public static HybridStructureTemplate load(String templateName) throws IOException {
        String[] parts = templateName.split(":", 2);
        if (parts.length != 2) {
            throw new TemplateException("Invalid template name (expected 'namespace:path'): " + templateName);
        }
        String namespace = parts[0];
        String path = parts[1];

        String jsonResource = "/assets/" + namespace + "/horizonqastructures/" + path + ".json";
        String nbtResource = "/assets/" + namespace + "/horizonqastructures/" + path + "_tiles.nbt";

        InputStream jsonStream = HybridStructureLoader.class.getResourceAsStream(jsonResource);
        if (jsonStream == null) {
            throw new TemplateException("Structure template resource not found: " + jsonResource);
        }

        int sizeX, sizeY, sizeZ;
        HybridStructureTemplate.PaletteEntry[] palette;
        char[] paletteKeys;
        int[][][] blockData;

        try (InputStreamReader reader = new InputStreamReader(jsonStream, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                throw malformed(templateName, "root JSON object is empty");
            }

            JsonArray sizeArr = requiredArray(root, "size", templateName);
            if (sizeArr.size() != 3) {
                throw malformed(templateName, "'size' must contain exactly three numbers");
            }
            sizeX = sizeArr.get(0)
                .getAsInt();
            sizeY = sizeArr.get(1)
                .getAsInt();
            sizeZ = sizeArr.get(2)
                .getAsInt();
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                throw malformed(templateName, "'size' values must all be positive");
            }

            JsonObject paletteObj = requiredObject(root, "palette", templateName);

            List<HybridStructureTemplate.PaletteEntry> paletteList = new ArrayList<>();
            List<Character> keyList = new ArrayList<>();
            Map<Character, Integer> keyToIndex = new HashMap<>();

            paletteList.add(new HybridStructureTemplate.PaletteEntry("minecraft:air", 0));
            keyList.add(HybridStructureTemplate.AIR_KEY);
            keyToIndex.put(HybridStructureTemplate.AIR_KEY, 0);

            int paletteIdx = 1;
            for (Map.Entry<String, JsonElement> entry : paletteObj.entrySet()) {
                String keyStr = entry.getKey();
                if (keyStr.isEmpty()) continue;
                char key = getKey(templateName, keyStr, keyToIndex);

                JsonElement paletteValue = entry.getValue();
                if (paletteValue == null || !paletteValue.isJsonObject()) {
                    throw malformed(
                        templateName,
                        "palette key '" + keyStr + "' must map to an object with a block name");
                }
                JsonObject val = paletteValue.getAsJsonObject();
                String name = requiredString(val, "name", templateName, "palette key '" + keyStr + "'");
                int meta = val.has("meta") ? val.get("meta")
                    .getAsInt() : 0;
                String label = val.has("label") ? val.get("label")
                    .getAsString() : null;

                paletteList.add(new HybridStructureTemplate.PaletteEntry(name, meta, label));
                keyList.add(key);
                keyToIndex.put(key, paletteIdx);
                paletteIdx++;
            }

            palette = paletteList.toArray(new HybridStructureTemplate.PaletteEntry[0]);
            paletteKeys = new char[keyList.size()];
            for (int i = 0; i < keyList.size(); i++) {
                paletteKeys[i] = keyList.get(i);
            }

            JsonArray layersArr = requiredArray(root, "layers", templateName);
            if (layersArr.size() != sizeY) {
                throw new TemplateException(
                    "Template '" + templateName
                        + "' declares size Y="
                        + sizeY
                        + " but has "
                        + layersArr.size()
                        + " layers");
            }

            blockData = new int[sizeX][sizeY][sizeZ];

            for (int y = 0; y < sizeY; y++) {
                JsonElement layerElement = layersArr.get(y);
                if (layerElement == null || !layerElement.isJsonArray()) {
                    throw malformed(templateName, "layer y=" + y + " must be an array of rows");
                }
                JsonArray layer = layerElement.getAsJsonArray();
                if (layer.size() != sizeZ) {
                    throw new TemplateException(
                        "Template '" + templateName
                            + "' layer y="
                            + y
                            + " has "
                            + layer.size()
                            + " rows but size Z="
                            + sizeZ);
                }
                for (int z = 0; z < sizeZ; z++) {
                    JsonElement rowElement = layer.get(z);
                    if (rowElement == null || !rowElement.isJsonPrimitive()) {
                        throw malformed(templateName, "layer y=" + y + " row z=" + z + " must be a string");
                    }
                    String row = rowElement.getAsString();
                    if (row.length() != sizeX) {
                        throw new TemplateException(
                            "Template '" + templateName
                                + "' layer y="
                                + y
                                + " row z="
                                + z
                                + " has length "
                                + row.length()
                                + " but size X="
                                + sizeX);
                    }
                    for (int x = 0; x < sizeX; x++) {
                        char c = row.charAt(x);
                        Integer idx = keyToIndex.get(c);
                        if (idx == null) {
                            throw new TemplateException(
                                "Unknown palette key '" + c
                                    + "' at ("
                                    + x
                                    + ","
                                    + y
                                    + ","
                                    + z
                                    + ") in template '"
                                    + templateName
                                    + "'");
                        }
                        blockData[x][y][z] = idx;
                    }
                }
            }
        } catch (RuntimeException e) {
            throw new TemplateException("Malformed template '" + templateName + "': " + errorMessage(e), e);
        }

        NBTTagCompound tileData = null;
        InputStream nbtStream = HybridStructureLoader.class.getResourceAsStream(nbtResource);
        if (nbtStream != null) {
            try {
                tileData = CompressedStreamTools.readCompressed(nbtStream);
            } catch (IOException | RuntimeException e) {
                throw new TemplateException(
                    "Template '" + templateName
                        + "' has unreadable tile entity data "
                        + nbtResource
                        + ": "
                        + errorMessage(e),
                    e);
            } finally {
                try {
                    nbtStream.close();
                } catch (IOException ignored) {}
            }
        }

        LOG.debug(
            "Loaded template '{}' ({}x{}x{}, {} palette entries)",
            templateName,
            sizeX,
            sizeY,
            sizeZ,
            palette.length - 1);
        return new HybridStructureTemplate(sizeX, sizeY, sizeZ, palette, paletteKeys, blockData, tileData);
    }

    private static char getKey(String templateName, String keyStr, Map<Character, Integer> keyToIndex)
        throws TemplateException {
        char key = keyStr.charAt(0);

        if (key == HybridStructureTemplate.AIR_KEY) {
            throw new TemplateException(
                "Template '" + templateName
                    + "' palette must not use reserved air key '"
                    + HybridStructureTemplate.AIR_KEY
                    + "'");
        }
        if (keyToIndex.containsKey(key)) {
            throw new TemplateException("Template '" + templateName + "' has duplicate palette key '" + key + "'");
        }
        return key;
    }

    private static JsonArray requiredArray(JsonObject root, String name, String templateName) throws TemplateException {
        JsonElement element = root.get(name);
        if (element == null || !element.isJsonArray()) {
            throw malformed(templateName, "missing or invalid '" + name + "' array");
        }
        return element.getAsJsonArray();
    }

    private static JsonObject requiredObject(JsonObject root, String name, String templateName)
        throws TemplateException {
        JsonElement element = root.get(name);
        if (element == null || !element.isJsonObject()) {
            throw malformed(templateName, "missing or invalid '" + name + "' object");
        }
        return element.getAsJsonObject();
    }

    private static String requiredString(JsonObject root, String name, String templateName, String context)
        throws TemplateException {
        JsonElement element = root.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            throw malformed(templateName, context + " is missing string field '" + name + "'");
        }
        String value = element.getAsString();
        if (value == null || value.isEmpty()) {
            throw malformed(templateName, context + " has empty string field '" + name + "'");
        }
        return value;
    }

    private static TemplateException malformed(String templateName, String message) {
        return new TemplateException("Malformed template '" + templateName + "': " + message);
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
