package com.gtnewhorizons.horizonqa.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.gtnewhorizons.horizonqa.api.GameTestArguments;

/**
 * Expands one {@link GameTest} method into independently reported invocations supplied by a static
 * method in the same holder.
 *
 * <p>
 * The source must be a public static no-argument method returning a stream, iterable, iterator, or
 * array. Every element must be a {@link GameTestArguments} row. Sources execute during discovery and
 * cases retain encounter order. A source may contain at most 256 rows; named case keys may contain at
 * most 128 characters. An empty value uses the test method's name as the source method name.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MethodSource {

    /** Source method name in the same {@link GameTestHolder}; empty uses the test method name. */
    String value() default "";
}
