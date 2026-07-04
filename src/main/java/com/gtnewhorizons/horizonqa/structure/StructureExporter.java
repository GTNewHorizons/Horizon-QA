package com.gtnewhorizons.horizonqa.structure;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class StructureExporter {

    private static final int VERSION_NUMBER = 1;

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private static final String KEY_SEQUENCE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String ENTITIES_KEY = "entities";

    public static final class ExportResult {

        private final int tileEntityCount;
        private final int entityCount;
        private final String structureDataExtension;
        private final int labelCount;

        private ExportResult(int tileEntityCount, int entityCount, String structureDataExtension, int labelCount) {
            this.tileEntityCount = tileEntityCount;
            this.entityCount = entityCount;
            this.structureDataExtension = structureDataExtension;
            this.labelCount = labelCount;
        }

        public int tileEntityCount() {
            return tileEntityCount;
        }

        public int entityCount() {
            return entityCount;
        }

        public boolean structureDataWritten() {
            return structureDataExtension != null;
        }

        public String structureDataExtension() {
            return structureDataExtension;
        }

        public int labelCount() {
            return labelCount;
        }
    }

    private StructureExporter() {}

    public static ExportResult export(WorldServer world, int x1, int y1, int z1, int x2, int y2, int z2, File outputDir,
        String name) throws IOException {
        return export(world, x1, y1, z1, x2, y2, z2, outputDir, name, null);
    }

    public static ExportResult export(WorldServer world, int x1, int y1, int z1, int x2, int y2, int z2, File outputDir,
        String name, Map<String, int[]> absoluteLabels) throws IOException {

        int sizeX = x2 - x1 + 1;
        int sizeY = y2 - y1 + 1;
        int sizeZ = z2 - z1 + 1;
        Map<String, int[]> relativeLabels = relativizeLabels(absoluteLabels, x1, y1, z1, sizeX, sizeY, sizeZ);

        String[][][] blockNames = new String[sizeX][sizeY][sizeZ];
        int[][][] blockMetas = new int[sizeX][sizeY][sizeZ];
        NBTTagCompound tileData = new NBTTagCompound();
        NBTTagCompound entityData = new NBTTagCompound();
        NBTTagList entities = new NBTTagList();

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

        int entityCount = exportEntities(world, x1, y1, z1, x2, y2, z2, entities);
        if (entityCount > 0) {
            entityData.setTag(ENTITIES_KEY, entities);
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
            writer.write("  ]");
            if (!relativeLabels.isEmpty()) {
                writer.write(",\n");
                writer.write("  \"annotations\": {\n");
                writer.write("    \"labels\": {\n");
                int labelIndex = 0;
                for (Map.Entry<String, int[]> entry : relativeLabels.entrySet()) {
                    int[] pos = entry.getValue();
                    writer.write("      \"");
                    writer.write(escapeJson(entry.getKey()));
                    writer.write("\": [");
                    writer.write(String.valueOf(pos[0]));
                    writer.write(", ");
                    writer.write(String.valueOf(pos[1]));
                    writer.write(", ");
                    writer.write(String.valueOf(pos[2]));
                    writer.write("]");
                    if (labelIndex < relativeLabels.size() - 1) writer.write(",");
                    writer.write("\n");
                    labelIndex++;
                }
                writer.write("    }\n");
                writer.write("  }\n");
            } else {
                writer.write("\n");
            }
            writer.write("}\n");
        }
        LOG.info("StructureExporter: wrote layout -> {}", jsonFile.getAbsolutePath());

        NBTTagCompound structureData = StructureNbt.combine(tileData, entityData);
        File snbtFile = new File(outputDir, name + ".snbt");
        File nbtFile = new File(outputDir, name + ".nbt");
        String structureDataExtension = null;
        if (!StructureNbt.isEmpty(structureData)) {
            String snbt = StructureNbt.toSnbtIfLossless(structureData);
            if (snbt != null) {
                try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(snbtFile),
                    StandardCharsets.UTF_8)) {
                    writer.write(snbt);
                }
                deleteStale(nbtFile, "binary structure data");
                structureDataExtension = ".snbt";
                LOG.info("StructureExporter: wrote structure data -> {}", snbtFile.getAbsolutePath());
            } else {
                try (FileOutputStream outputStream = new FileOutputStream(nbtFile)) {
                    CompressedStreamTools.writeCompressed(structureData, outputStream);
                }
                deleteStale(snbtFile, "SNBT structure data");
                structureDataExtension = ".nbt";
                LOG.info(
                    "StructureExporter: wrote binary structure data -> {} (SNBT round-trip was not lossless)",
                    nbtFile.getAbsolutePath());
            }
        } else {
            deleteStale(snbtFile, "structure data");
            deleteStale(nbtFile, "binary structure data");
        }

        deleteStale(new File(outputDir, name + "_tiles.nbt"), "legacy tile entity data");
        deleteStale(new File(outputDir, name + "_entities.nbt"), "legacy entity data");

        return new ExportResult(
            StructureNbt.tileEntityCount(tileData),
            entityCount,
            structureDataExtension,
            relativeLabels.size());
    }

    private static Map<String, int[]> relativizeLabels(Map<String, int[]> absoluteLabels, int minX, int minY, int minZ,
        int sizeX, int sizeY, int sizeZ) throws IOException {
        TreeMap<String, int[]> relative = new TreeMap<>();
        if (absoluteLabels == null || absoluteLabels.isEmpty()) {
            return relative;
        }
        for (Map.Entry<String, int[]> entry : absoluteLabels.entrySet()) {
            String name = entry.getKey();
            if (!StructureAnnotations.isValidLabelName(name)) {
                throw new IOException("Label name '" + name + "' must match [A-Za-z_][A-Za-z0-9_]* before export");
            }
            int[] pos = entry.getValue();
            if (pos == null || pos.length != 3) {
                throw new IOException("Label '" + name + "' has invalid coordinates");
            }
            int x = pos[0] - minX;
            int y = pos[1] - minY;
            int z = pos[2] - minZ;
            if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
                throw new IOException(
                    "Label '" + name
                        + "' at ("
                        + pos[0]
                        + ", "
                        + pos[1]
                        + ", "
                        + pos[2]
                        + ") is outside the wand selection");
            }
            relative.put(name, new int[] { x, y, z });
        }
        return relative;
    }

    private static int exportEntities(WorldServer world, int x1, int y1, int z1, int x2, int y2, int z2,
        NBTTagList entities) {
        AxisAlignedBB selection = AxisAlignedBB
            .getBoundingBox(x1, y1, z1, (double) x2 + 1.0D, (double) y2 + 1.0D, (double) z2 + 1.0D);
        int entityCount = 0;

        for (Object object : world.loadedEntityList) {
            if (!(object instanceof Entity entity) || entity instanceof EntityPlayer || entity.isDead) {
                continue;
            }
            if (!isEntityInSelection(entity, selection)) {
                continue;
            }

            NBTTagCompound entityNbt = new NBTTagCompound();
            if (!entity.writeToNBTOptional(entityNbt)) {
                continue;
            }
            relativizeEntityNbt(entityNbt, x1, y1, z1);
            entities.appendTag(entityNbt);
            entityCount++;
        }

        return entityCount;
    }

    private static boolean isEntityInSelection(Entity entity, AxisAlignedBB selection) {
        if (entity.boundingBox != null && entity.boundingBox.intersectsWith(selection)) {
            return true;
        }
        return entity.posX >= selection.minX && entity.posX < selection.maxX
            && entity.posY >= selection.minY
            && entity.posY < selection.maxY
            && entity.posZ >= selection.minZ
            && entity.posZ < selection.maxZ;
    }

    static void relativizeEntityNbt(NBTTagCompound entityNbt, int originX, int originY, int originZ) {
        patchDoubleList(entityNbt, "Pos", -originX, -originY, -originZ);
        relativizeIntegerTriple(entityNbt, "TileX", "TileY", "TileZ", originX, originY, originZ);
        relativizeShortTriple(entityNbt, "xTile", "yTile", "zTile", originX, originY, originZ);
        entityNbt.removeTag("UUIDMost");
        entityNbt.removeTag("UUIDLeast");
    }

    private static void patchDoubleList(NBTTagCompound nbt, String key, double dx, double dy, double dz) {
        NBTTagList list = nbt.getTagList(key, 6);
        if (list.tagCount() < 3) {
            return;
        }
        nbt.setTag(key, doubleList(list.func_150309_d(0) + dx, list.func_150309_d(1) + dy, list.func_150309_d(2) + dz));
    }

    private static void relativizeIntegerTriple(NBTTagCompound nbt, String xKey, String yKey, String zKey, int originX,
        int originY, int originZ) {
        if (!nbt.hasKey(xKey) || !nbt.hasKey(yKey) || !nbt.hasKey(zKey)) {
            return;
        }
        nbt.setInteger(xKey, nbt.getInteger(xKey) - originX);
        nbt.setInteger(yKey, nbt.getInteger(yKey) - originY);
        nbt.setInteger(zKey, nbt.getInteger(zKey) - originZ);
    }

    private static void relativizeShortTriple(NBTTagCompound nbt, String xKey, String yKey, String zKey, int originX,
        int originY, int originZ) {
        if (!nbt.hasKey(xKey) || !nbt.hasKey(yKey) || !nbt.hasKey(zKey)) {
            return;
        }
        nbt.setShort(xKey, (short) (nbt.getShort(xKey) - originX));
        nbt.setShort(yKey, (short) (nbt.getShort(yKey) - originY));
        nbt.setShort(zKey, (short) (nbt.getShort(zKey) - originZ));
    }

    static NBTTagList doubleList(double x, double y, double z) {
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagDouble(x));
        list.appendTag(new NBTTagDouble(y));
        list.appendTag(new NBTTagDouble(z));
        return list;
    }

    private static void deleteStale(File file, String description) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not delete stale " + description + " file: " + file.getAbsolutePath());
        }
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
