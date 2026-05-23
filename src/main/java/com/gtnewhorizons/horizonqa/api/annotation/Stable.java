package com.gtnewhorizons.horizonqa.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public API type whose contract is stable and safe to depend on.
 * This is a zero-runtime marker: retained in class files for tooling but
 * not available via reflection at runtime.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Stable {}
