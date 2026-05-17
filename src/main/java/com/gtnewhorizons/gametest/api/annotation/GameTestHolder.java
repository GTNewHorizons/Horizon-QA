package com.gtnewhorizons.gametest.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class that holds static {@link GameTest} methods. {@link #value()} is the template namespace
 * (e.g. mod id) used when resolving structure paths.
 */
@Experimental
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GameTestHolder {

    /** Namespace / holder id for templates under this holder. */
    String value();

    /** Optional prefix applied to template names for this holder. */
    String templatePrefix() default "";
}
