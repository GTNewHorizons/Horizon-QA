package com.gtnewhorizons.gametest.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static test method with signature {@code void testName(GameTestHelper helper)}.
 */
@Experimental
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {

    /** Structure template name (resolved relative to holder namespace / prefix). */
    String template() default "";

    /** Maximum ticks before the test is considered timed out. */
    int timeoutTicks() default 100;

    /** Batch name; tests sharing a batch run together with {@link BeforeBatch} / {@link AfterBatch}. */
    String batch() default "";

    /** If false, failure of this test does not fail the overall run (e.g. optional coverage). */
    boolean required() default true;

    /**
     * Structure rotation: 0 = none, 1 = 90° clockwise, 2 = 180°, 3 = 270° clockwise (Y axis), matching
     * Minecraft structure placement conventions.
     */
    int rotation() default 0;
}
