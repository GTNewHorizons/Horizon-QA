package com.gtnewhorizons.gametest.api;

import com.gtnewhorizons.gametest.api.annotation.Experimental;

@Experimental
public class GameTestAssertException extends AssertionError {

    private final int x;
    private final int y;
    private final int z;

    public GameTestAssertException(String message, int x, int y, int z) {
        super(message);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public GameTestAssertException(String message, TestPos pos) {
        this(message, pos.x(), pos.y(), pos.z());
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
