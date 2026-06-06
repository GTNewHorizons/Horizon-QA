package com.gtnewhorizons.horizonqa.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public static void method run once before all tests in the same {@link GameTest#batch()}.
 */
@Experimental
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BeforeBatch {

    /** Batch name, must match {@link GameTest#batch()} on tests in that batch. */
    String value();
}
