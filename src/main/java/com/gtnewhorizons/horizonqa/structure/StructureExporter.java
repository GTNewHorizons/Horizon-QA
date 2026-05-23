package com.gtnewhorizons.horizonqa.structure;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class StructureExporter {

    private static final int VERSION_NUMBER = 1;

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private static final String KEY_SEQUENCE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private StructureExporter() {}

    public static void export(WorldServer world, int x1, int y1, int z1, int x2, int y2, int z2, File outputDir,
        String name) throws IOException {

        int sizeX = x2 - x1 + 1;
        int sizeY = y2 - y1 + 1;
        int sizeZ = z2 - z1 + 1;

        String[][][] blockNames = new String[sizeX][sizeY][sizeZ];
        int[][][] blockMetas = new int[sizeX][sizeY][sizeZ];
        NBTTagCompound tileData = new NBTTagCompound();

        TreeMap<String, String> sortedUniqueBlocks = new TreeMap<>();

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    int wx = x1 + x;
                    int wy = y1 + y;
                    int wz = z1 + z;

                    Block block = world.getBlock(wx, wy, wz);
                    int meta = world.getBlockMetadata(wx, wy, wz);
                    String regName = RegistryStringResolver.getName(block);

                    if (regName == null || regName.equals("minecraft:air")) {
                        blockNames[x][y][z] = "minecraft:air";
                        blockMetas[x][y][z] = 0;
                    } else {
                        blockNames[x][y][z] = regName;
                        blockMetas[x][y][z] = meta;

                        String palKey = regName + "@" + meta;
                        if (!sortedUniqueBlocks.containsKey(palKey)) {
                            sortedUniqueBlocks.put(palKey, resolveLabel(block, meta));
                        }

                        TileEntity te = world.getTileEntity(wx, wy, wz);
                        if (te != null) {
                            NBTTagCompound teNbt = new NBTTagCompound();
                            te.writeToNBT(teNbt);
                            teNbt.setInteger("x", x);
                            teNbt.setInteger("y", y);
                            teNbt.setInteger("z", z);
                            tileData.setTag(x + "," + y + "," + z, teNbt);
                        }
                    }
                }
            }
        }

        if (sortedUniqueBlocks.size() > KEY_SEQUENCE.length()) {
            throw new IOException(
                "Structure contains " + sortedUniqueBlocks.size()
                    + " unique block types, exceeding the maximum of "
                    + KEY_SEQUENCE.length());
        }

        Map<String, Integer> indexMap = new LinkedHashMap<>();
        indexMap.put("minecraft:air@0", 0);

        char[] keys = new char[sortedUniqueBlocks.size() + 1];
        keys[0] = HybridStructureTemplate.AIR_KEY;

        List<String> palNames = new ArrayList<>();
        List<Integer> palMetas = new ArrayList<>();
        List<String> palLabels = new ArrayList<>();
        List<Character> palKeys = new ArrayList<>();

        int idx = 1;
        for (Map.Entry<String, String> entry : sortedUniqueBlocks.entrySet()) {
            String palKey = entry.getKey();
            String label = entry.getValue();

            int atPos = palKey.lastIndexOf('@');
            String entryName = palKey.substring(0, atPos);
            int entryMeta = Integer.parseInt(palKey.substring(atPos + 1));

            char key = KEY_SEQUENCE.charAt(idx - 1);
            keys[idx] = key;
            indexMap.put(palKey, idx);

            palKeys.add(key);
            palNames.add(entryName);
            palMetas.add(entryMeta);
            palLabels.add(label);
            idx++;
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File jsonFile = new File(outputDir, name + ".json");
        try (FileWriter writer = new FileWriter(jsonFile)) {
            writer.write("{\n");
            writer.write("  \"format_version\": " + VERSION_NUMBER + ",\n");
            writer.write("  \"size\": [" + sizeX + ", " + sizeY + ", " + sizeZ + "],\n");

            writer.write("  \"palette\": {");
            if (palKeys.isEmpty()) {
                writer.write("},\n");
            } else {
                writer.write("\n");
                for (int i = 0; i < palKeys.size(); i++) {
                    writer.write("    \"");
                    writer.write(palKeys.get(i));
                    writer.write("\": {\"name\": \"");
                    writer.write(palNames.get(i));
                    writer.write("\", \"meta\": ");
                    writer.write(String.valueOf(palMetas.get(i)));
                    if (palLabels.get(i) != null) {
                        writer.write(", \"label\": \"");
                        writer.write(escapeJson(palLabels.get(i)));
                        writer.write("\"");
                    }
                    writer.write("}");
                    if (i < palKeys.size() - 1) writer.write(",");
                    writer.write("\n");
                }
                writer.write("  },\n");
            }

            writer.write("  \"layers\": [\n");
            for (int y = 0; y < sizeY; y++) {
                writer.write("    [\n");
                for (int z = 0; z < sizeZ; z++) {
                    writer.write("      \"");
                    for (int x = 0; x < sizeX; x++) {
                        String palKey = blockNames[x][y][z] + "@" + blockMetas[x][y][z];
                        int palIdx = indexMap.getOrDefault(palKey, 0);
                        writer.write(keys[palIdx]);
                    }
                    writer.write("\"");
                    if (z < sizeZ - 1) writer.write(",");
                    writer.write("\n");
                }
                writer.write("    ]");
                if (y < sizeY - 1) writer.write(",");
                writer.write("\n");
            }
            writer.write("  ]\n");
            writer.write("}\n");
        }
        LOG.info("StructureExporter: wrote layout → {}", jsonFile.getAbsolutePath());

        File nbtFile = new File(outputDir, name + "_tiles.nbt");
        try (FileOutputStream fos = new FileOutputStream(nbtFile)) {
            CompressedStreamTools.writeCompressed(tileData, fos);
        }
        LOG.info("StructureExporter: wrote tile data → {}", nbtFile.getAbsolutePath());
    }

    private static String resolveLabel(Block block, int meta) {
        try {
            ItemStack stack = new ItemStack(block, 1, meta);
            if (stack.getItem() != null) {
                String displayName = stack.getDisplayName();
                if (displayName != null && !displayName.isEmpty()
                    && !displayName.startsWith("tile.")
                    && !displayName.startsWith("item.")) {
                    return displayName;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
