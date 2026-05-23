package com.gtnewhorizons.horizonqa.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

/**
 * Thrown at test teardown when an {@code IGregTechTileEntity} is found outside the test cell. The leaking test itself
 * is failed, so the error is co-located with its cause.
 */
@Experimental
public class TestIsolationViolation extends GameTestAssertException {

    private final List<String> leakedPositions;

    public TestIsolationViolation(String testId, List<String> leakedPositions, int x, int y, int z) {
        super(buildMessage(testId, leakedPositions), x, y, z);
        this.leakedPositions = Collections.unmodifiableList(new ArrayList<>(leakedPositions));
    }

    public List<String> getLeakedPositions() {
        return leakedPositions;
    }

    private static String buildMessage(String testId, List<String> positions) {
        return testId + " leaked IGregTechTileEntity outside cell at: " + String.join(", ", positions);
    }
}
