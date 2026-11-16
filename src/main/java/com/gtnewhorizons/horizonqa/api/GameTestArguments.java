package com.gtnewhorizons.horizonqa.api;

import java.util.Arrays;

/**
 * One invocation supplied by a parameterized game test's method source.
 *
 * <p>
 * Use {@link #named(String, Object, Object...)} when the inputs have a stable domain name such as a
 * voltage tier or fluid id. Named invocations use that name in selectors, logs, JUnit XML, and status
 * JSON. {@link #of(Object, Object...)} creates an invocation named by its zero-based position in the
 * source.
 *
 * <p>
 * The first argument is a fixed parameter rather than part of the varargs array. Consequently, a
 * single {@code null} or array value is preserved as one test argument instead of being confused
 * with a null or expanded varargs array. Use {@link #ofValues(Object[])} or
 * {@link #namedValues(String, Object[])} when a later value is {@code null} or an array.
 *
 * <p>
 * Rows are snapshots. HorizonQA defensively copies argument arrays, including array-valued
 * arguments, for each launch, but arbitrary object values are not cloned. Prefer immutable
 * descriptors such as ids, names, and numbers; construct mutable game objects inside the test
 * method so interactive reruns cannot observe mutations from an earlier invocation.
 */
public final class GameTestArguments {

    private static final int MAX_NAME_LENGTH = 128;

    private final String name;
    private final Object[] arguments;

    private GameTestArguments(String name, Object[] arguments) {
        if (arguments == null) {
            throw new IllegalArgumentException("arguments must not be null");
        }
        this.name = name;
        this.arguments = arguments.clone();
    }

    /**
     * Create an invocation whose case name is its zero-based position in the method source.
     *
     * @param first first value passed to the test method after {@link GameTestHelper}; may be
     *              {@code null} or an array
     * @param rest  any remaining values
     */
    public static GameTestArguments of(Object first, Object... rest) {
        return new GameTestArguments(null, combine(first, rest));
    }

    /**
     * Create an indexed invocation from an explicit values array.
     *
     * <p>
     * Prefer this factory when any value after the first is {@code null} or itself an array, because
     * Java varargs cannot distinguish those values without casts.
     *
     * @param values values passed to the test method after {@link GameTestHelper}
     */
    public static GameTestArguments ofValues(Object[] values) {
        return new GameTestArguments(null, values);
    }

    /**
     * Create a named invocation.
     *
     * @param name  stable case name of at most 128 characters matching {@code [A-Za-z0-9_.-]+}
     * @param first first value passed to the test method after {@link GameTestHelper}; may be
     *              {@code null} or an array
     * @param rest  any remaining values
     */
    public static GameTestArguments named(String name, Object first, Object... rest) {
        validateName(name);
        return new GameTestArguments(name, combine(first, rest));
    }

    /**
     * Create a named invocation from an explicit values array.
     *
     * <p>
     * Prefer this factory when any value after the first is {@code null} or itself an array, because
     * Java varargs cannot distinguish those values without casts.
     *
     * @param name   stable case name of at most 128 characters matching
     *               {@code [A-Za-z0-9_.-]+}
     * @param values values passed to the test method after {@link GameTestHelper}
     */
    public static GameTestArguments namedValues(String name, Object[] values) {
        validateName(name);
        return new GameTestArguments(name, values);
    }

    /** Return whether this invocation has an explicit case name. */
    public boolean isNamed() {
        return name != null;
    }

    /** Return the explicit case name, or {@code null} when the source position supplies the name. */
    public String name() {
        return name;
    }

    /** Return a defensive copy of the values passed after {@link GameTestHelper}. */
    public Object[] arguments() {
        return arguments.clone();
    }

    @Override
    public String toString() {
        return (name == null ? "" : name + ":") + Arrays.toString(arguments);
    }

    private static Object[] combine(Object first, Object[] rest) {
        if (rest == null) {
            throw new IllegalArgumentException("remaining arguments must not be null");
        }
        Object[] combined = new Object[rest.length + 1];
        combined[0] = first;
        System.arraycopy(rest, 0, combined, 1, rest.length);
        return combined;
    }

    private static void validateName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("name must match [A-Za-z0-9_.-]+");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must be at most " + MAX_NAME_LENGTH + " characters");
        }
    }
}
