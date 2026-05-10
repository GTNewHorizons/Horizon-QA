package com.gtnewhorizons.gametest.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.gametest.api.TestIsolationViolation;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

final class TestCellScanner {

    private static final int OUTER_MARGIN = GameTestGridLayout.INTER_CELL_GAP;

    private TestCellScanner() {}

    static void preClear(WorldServer world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        GridSweeper.clear(world, minX, minY, minZ, maxX, maxY, maxZ);
    }

    static void preClearWithMargin(WorldServer world, int cellMinX, int cellMinY, int cellMinZ, int cellMaxX,
        int cellMaxY, int cellMaxZ) {
        int minX = cellMinX - OUTER_MARGIN;
        int minY = Math.max(0, cellMinY - OUTER_MARGIN);
        int minZ = cellMinZ - OUTER_MARGIN;
        int maxX = cellMaxX + OUTER_MARGIN;
        int maxY = cellMaxY + OUTER_MARGIN;
        int maxZ = cellMaxZ + OUTER_MARGIN;
        GridSweeper.clear(world, minX, minY, minZ, maxX, maxY, maxZ);
    }

    static void registerIsolationCheck(GameTestInstance inst, WorldServer world, int cellMinX, int cellMinY,
        int cellMinZ, int cellMaxX, int cellMaxY, int cellMaxZ, int tmplMinX, int tmplMinY, int tmplMinZ, int tmplMaxX,
        int tmplMaxY, int tmplMaxZ, boolean hasTemplate) {

        inst.addCleanup(() -> {
            if (hasTemplate) {
                List<String> extra = scanCellPadding(
                    world,
                    cellMinX,
                    cellMinY,
                    cellMinZ,
                    cellMaxX,
                    cellMaxY,
                    cellMaxZ,
                    tmplMinX,
                    tmplMinY,
                    tmplMinZ,
                    tmplMaxX,
                    tmplMaxY,
                    tmplMaxZ);
                for (String pos : extra) {
                    inst.addWarning("Block outside template footprint: " + pos);
                }
            }

            List<String> leaked = scanOuterMarginForIGTE(
                world,
                cellMinX,
                cellMinY,
                cellMinZ,
                cellMaxX,
                cellMaxY,
                cellMaxZ);
            if (!leaked.isEmpty()) {
                throw new TestIsolationViolation(
                    inst.getDefinition()
                        .getTestId(),
                    leaked,
                    cellMinX,
                    cellMinY,
                    cellMinZ);
            }
        });
    }

    private static List<String> scanCellPadding(WorldServer world, int cellMinX, int cellMinY, int cellMinZ,
        int cellMaxX, int cellMaxY, int cellMaxZ, int tmplMinX, int tmplMinY, int tmplMinZ, int tmplMaxX, int tmplMaxY,
        int tmplMaxZ) {

        List<String> result = new ArrayList<>();
        for (int x = cellMinX; x <= cellMaxX; x++) {
            for (int y = cellMinY; y <= cellMaxY; y++) {
                for (int z = cellMinZ; z <= cellMaxZ; z++) {
                    if (x >= tmplMinX && x <= tmplMaxX
                        && y >= tmplMinY
                        && y <= tmplMaxY
                        && z >= tmplMinZ
                        && z <= tmplMaxZ) continue;
                    if (!world.isAirBlock(x, y, z)) {
                        result.add("(" + x + ", " + y + ", " + z + ")");
                    }
                }
            }
        }
        return result;
    }

    private static List<String> scanOuterMarginForIGTE(WorldServer world, int cellMinX, int cellMinY, int cellMinZ,
        int cellMaxX, int cellMaxY, int cellMaxZ) {

        List<String> result = new ArrayList<>();
        int minX = cellMinX - OUTER_MARGIN;
        int minY = Math.max(0, cellMinY - OUTER_MARGIN);
        int minZ = cellMinZ - OUTER_MARGIN;
        int maxX = cellMaxX + OUTER_MARGIN;
        int maxY = cellMaxY + OUTER_MARGIN;
        int maxZ = cellMaxZ + OUTER_MARGIN;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x >= cellMinX && x <= cellMaxX
                        && y >= cellMinY
                        && y <= cellMaxY
                        && z >= cellMinZ
                        && z <= cellMaxZ) continue;
                    TileEntity te = world.getTileEntity(x, y, z);
                    if (te instanceof IGregTechTileEntity) {
                        result.add("(" + x + ", " + y + ", " + z + ")");
                    }
                }
            }
        }
        return result;
    }
}
