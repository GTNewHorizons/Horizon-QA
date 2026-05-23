package com.gtnewhorizons.horizonqa.api;

import java.util.Objects;

import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

@Experimental
public final class TestPos {

    private final int x;
    private final int y;
    private final int z;

    public TestPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static TestPos at(int x, int y, int z) {
        return new TestPos(x, y, z);
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TestPos testPos)) {
            return false;
        }
        return x == testPos.x && y == testPos.y && z == testPos.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "TestPos{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
