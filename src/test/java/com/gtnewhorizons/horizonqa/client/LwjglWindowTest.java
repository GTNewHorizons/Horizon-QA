package com.gtnewhorizons.horizonqa.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class LwjglWindowTest {

    @Test
    public void preservesFramebufferDimensionsAcrossPixelScales() {
        assertEquals(960, LwjglWindow.logicalSizeForPixels(960, 1280, 1280));
        assertEquals(480, LwjglWindow.logicalSizeForPixels(960, 1280, 2560));
        assertEquals(640, LwjglWindow.logicalSizeForPixels(960, 1280, 1920));
    }

    @Test
    public void restoresOriginalLogicalSize() {
        assertEquals(1280, LwjglWindow.logicalSizeForPixels(1920, 640, 960));
    }

    @Test
    public void rejectsUnrepresentableOrInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () -> LwjglWindow.logicalSizeForPixels(961, 1280, 1920));
        assertThrows(IllegalArgumentException.class, () -> LwjglWindow.logicalSizeForPixels(960, 0, 1920));
        assertThrows(IllegalArgumentException.class, () -> LwjglWindow.logicalSizeForPixels(Integer.MAX_VALUE, 2, 1));
    }
}
