package com.gtnewhorizons.horizonqa.structure;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

public class HybridStructureLoaderTest {

    @Test
    public void missingTemplateThrowsTemplateException() {
        IOException error = assertThrows(
            TemplateException.class,
            () -> HybridStructureLoader.load("horizonqatest:missing"));

        assertTrue(
            error.getMessage()
                .contains("Structure template resource not found"));
    }

    @Test
    public void malformedTemplateThrowsTemplateException() {
        IOException error = assertThrows(
            TemplateException.class,
            () -> HybridStructureLoader.load("horizonqatest:malformed"));

        assertTrue(
            error.getMessage()
                .contains("Malformed template 'horizonqatest:malformed'"));
    }

    @Test
    public void unreadableExistingTilesNbtThrowsTemplateException() {
        IOException error = assertThrows(
            TemplateException.class,
            () -> HybridStructureLoader.load("horizonqatest:unreadable"));

        assertTrue(
            error.getMessage()
                .contains("unreadable tile entity data"));
        assertTrue(
            error.getMessage()
                .contains("unreadable_tiles.nbt"));
    }
}
