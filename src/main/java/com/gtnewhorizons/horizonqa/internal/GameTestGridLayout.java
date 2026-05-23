package com.gtnewhorizons.horizonqa.internal;

public class GameTestGridLayout {

    static final int DEFAULT_CELL_SIZE = 5;
    static final int INTER_CELL_GAP = 3;
    static final int MAX_PER_ROW = 10;
    static final int ORIGIN_Y = 64;

    private int rowX = 0;
    private int rowZ = 0;
    private int rowMaxDepth = DEFAULT_CELL_SIZE + INTER_CELL_GAP;
    private int rowCount = 0;

    public int[] allocateOrigin(int templateSizeX, int templateSizeZ) {
        int cellW = Math.max(templateSizeX, DEFAULT_CELL_SIZE) + INTER_CELL_GAP;
        int cellD = Math.max(templateSizeZ, DEFAULT_CELL_SIZE) + INTER_CELL_GAP;

        if (rowCount >= MAX_PER_ROW) {
            rowX = 0;
            rowZ += rowMaxDepth;
            rowMaxDepth = DEFAULT_CELL_SIZE + INTER_CELL_GAP;
            rowCount = 0;
        }

        int x = rowX;
        int z = rowZ;

        rowX += cellW;
        if (cellD > rowMaxDepth) rowMaxDepth = cellD;
        rowCount++;

        return new int[] { x, ORIGIN_Y, z };
    }

    public int[] allocateOrigin() {
        return allocateOrigin(0, 0);
    }

    public void reset() {
        rowX = 0;
        rowZ = 0;
        rowMaxDepth = DEFAULT_CELL_SIZE + INTER_CELL_GAP;
        rowCount = 0;
    }
}
