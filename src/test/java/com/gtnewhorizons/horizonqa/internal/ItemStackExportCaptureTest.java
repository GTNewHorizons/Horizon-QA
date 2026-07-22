package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

public class ItemStackExportCaptureTest {

    @Test
    public void nestedScopesStayActiveUntilTheOutermostScopeCloses() {
        assertFalse(ItemStackExportCapture.isActive());

        try (ItemStackExportCapture.Scope outer = ItemStackExportCapture.open()) {
            assertTrue(ItemStackExportCapture.isActive());
            try (ItemStackExportCapture.Scope inner = ItemStackExportCapture.open()) {
                assertTrue(ItemStackExportCapture.isActive());
            }
            assertTrue(ItemStackExportCapture.isActive());
        }

        assertFalse(ItemStackExportCapture.isActive());
    }

    @Test
    public void scopeIsConfinedToTheCurrentThread() throws Exception {
        AtomicBoolean activeOnOtherThread = new AtomicBoolean(true);

        try (ItemStackExportCapture.Scope ignored = ItemStackExportCapture.open()) {
            Thread other = new Thread(
                () -> activeOnOtherThread.set(ItemStackExportCapture.isActive()),
                "horizonqa-export-scope-test");
            other.start();
            other.join();

            assertTrue(ItemStackExportCapture.isActive());
            assertFalse(activeOnOtherThread.get());
        }
    }

    @Test
    public void exceptionDoesNotLeakTheScope() {
        try {
            try (ItemStackExportCapture.Scope ignored = ItemStackExportCapture.open()) {
                throw new IllegalStateException("expected");
            }
        } catch (IllegalStateException ignored) {}

        assertFalse(ItemStackExportCapture.isActive());
    }
}
