package com.gtnewhorizons.gametest.api;

import com.gtnewhorizons.gametest.api.annotation.Experimental;

@Experimental
public class GameTestAssertException extends AssertionError {

    private final int x;
    private final int y;
    private final int z;
    private final boolean hasPosition;

    /** Non-positional failure — no block location to highlight in the overlay. */
    public GameTestAssertException(String message, int x, int y, int z) {
        super(message);
        this.x = x;
        this.y = y;
        this.z = z;
        this.hasPosition = false;
    }

    /** Positional failure — highlights the given block in the overlay. */
    public GameTestAssertException(String message, TestPos pos) {
        super(message);
        this.x = pos.x();
        this.y = pos.y();
        this.z = pos.z();
        this.hasPosition = true;
    }

    public boolean hasPosition() {
        return hasPosition;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public TestPos getPos() {
        return new TestPos(x, y, z);
    }
}
