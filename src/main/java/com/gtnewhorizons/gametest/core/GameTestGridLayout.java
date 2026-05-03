package com.gtnewhorizons.gametest.core;

/**
 * Allocates non-overlapping world-space origins for test cells on a 2-D grid.
 * Tests fill left-to-right up to {@value #MAX_PER_ROW} columns, then wrap to the
 * next row. Cell dimensions equal the structure template size exactly (plus
 * {@value #INTER_CELL_GAP} blocks of clear separation). Tests without a template
 * receive a {@value #DEFAULT_CELL_SIZE}-block square cell. Rows advance by the
 * deepest cell seen in the previous row so that variable-size cells never overlap.
 */
public class GameTestGridLayout {

    /** Cell footprint used when a test has no structure template. */
    static final int DEFAULT_CELL_SIZE = 5;
    /** Clear blocks of separation between adjacent cells (horizontal and depth). */
    static final int INTER_CELL_GAP = 3;
    /** Grid width in cells before wrapping to the next row. */
    static final int MAX_PER_ROW = 10;
    /** Y coordinate of every cell origin. */
    static final int ORIGIN_Y = 64;

    /** World-space X of the next cell origin. */
    private int rowX = 0;
    /** World-space Z of the current row. */
    private int rowZ = 0;
    /** Maximum cell depth (Z) seen in the current row, used to advance rowZ on wrap. */
    private int rowMaxDepth = DEFAULT_CELL_SIZE + INTER_CELL_GAP;
    /** Number of cells allocated in the current row. */
    private int rowCount = 0;

    /**
     * Reserve and return the next cell origin as {@code [x, y, z]}.
     *
     * <p>If a template size is supplied ({@code > 0}), that exact size is used.
     * If zero (no template), {@link #DEFAULT_CELL_SIZE} is used instead.
     * {@value #INTER_CELL_GAP} blocks of clear separation are added on the far X and Z
     * edges so adjacent cells never touch.
     *
     * @param templateSizeX template width along X (0 = no template)
     * @param templateSizeZ template depth along Z (0 = no template)
     * @return absolute world-space {@code [x, y, z]} of the new cell origin
     */
    public int[] allocateOrigin(int templateSizeX, int templateSizeZ) {
        int cellW = (templateSizeX > 0 ? templateSizeX : DEFAULT_CELL_SIZE) + INTER_CELL_GAP;
        int cellD = (templateSizeZ > 0 ? templateSizeZ : DEFAULT_CELL_SIZE) + INTER_CELL_GAP;

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

    /** Convenience overload for tests that have no structure template. */
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
