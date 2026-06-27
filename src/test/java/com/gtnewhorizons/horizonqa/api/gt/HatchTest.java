package com.gtnewhorizons.horizonqa.api.gt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.function.ThrowingRunnable;

public class HatchTest {

    @Test
    public void fillRejectsNullFluid() {
        assertIllegalArgument("fluid must not be null", () -> hatch().fill(null));
    }

    @Test
    public void assertContainsRejectsNullFluid() {
        assertIllegalArgument("fluid must not be null", () -> hatch().assertContains(null));
    }

    private static Hatch hatch() {
        return new Hatch(null, "test hatch", null, null);
    }

    private static void assertIllegalArgument(String message, ThrowingRunnable action) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action);
        assertEquals(message, exception.getMessage());
    }
}
