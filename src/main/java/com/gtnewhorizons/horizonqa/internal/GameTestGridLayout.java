package com.gtnewhorizons.horizonqa.internal;

import com.gtnewhorizons.horizonqa.HorizonQAProperties;

public class GameTestGridLayout {

    static final int DEFAULT_CELL_SIZE = 5;
    static final int INTER_CELL_GAP = 3;
    static final int MAX_PER_ROW = 10;

    private final int originX;
    private final int originY;
    private final int originZ;
    private int rowX;
    private int rowZ;
    private int rowMaxDepth = DEFAULT_CELL_SIZE + INTER_CELL_GAP;
    private int rowCount = 0;

    public GameTestGridLayout() {
        this(HorizonQAProperties.gridOriginX(), HorizonQAProperties.gridOriginY(), HorizonQAProperties.gridOriginZ());
    }

    GameTestGridLayout(int originX, int originY, int originZ) {
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        reset();
    }

    public int[] allocateOrigin(int templateSizeX, int templateSizeZ) {
        int cellW = Math.max(templateSizeX, DEFAULT_CELL_SIZE) + INTER_CELL_GAP;
        int cellD = Math.max(templateSizeZ, DEFAULT_CELL_SIZE) + INTER_CELL_GAP;

        if (rowCount >= MAX_PER_ROW) {
            rowX = originX;
            rowZ += rowMaxDepth;
            rowMaxDepth = DEFAULT_CELL_SIZE + INTER_CELL_GAP;
            rowCount = 0;
        }

        int x = rowX;
        int z = rowZ;

        rowX += cellW;
        if (cellD > rowMaxDepth) rowMaxDepth = cellD;
        rowCount++;

        return new int[] { x, originY, z };
    }

    public int[] allocateOrigin() {
        return allocateOrigin(0, 0);
    }

    public void reset() {
        rowX = originX;
        rowZ = originZ;
        rowMaxDepth = DEFAULT_CELL_SIZE + INTER_CELL_GAP;
        rowCount = 0;
    }
}
