package com.gtnewhorizons.horizonqa.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public static void method run once when the same {@link GameTest#batch()} finishes or aborts. Cleanup is
 * still attempted when a before-hook fails after batch setup begins or the reported run ends early.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AfterBatch {

    /** Batch name, must match {@link GameTest#batch()} on tests in that batch. */
    String value();
}
