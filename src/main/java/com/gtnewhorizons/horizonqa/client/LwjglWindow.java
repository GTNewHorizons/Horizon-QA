package com.gtnewhorizons.horizonqa.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.lwjgl.opengl.Display;

/** Native resizing of the existing LWJGL 2 window. The normal event loop observes the resulting OS event. */
public final class LwjglWindow {

    private LwjglWindow() {}

    public static void validateSize(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Window dimensions must be positive");
    }

    public static void resize(int width, int height) {
        validateSize(width, height);
        try {
            Class<?> lockClass = Class.forName("org.lwjgl.opengl.GlobalLock");
            synchronized (FieldUtils.readDeclaredStaticField(lockClass, "lock", true)) {
                if (!Display.isCreated() || Display.isFullscreen()
                    || Display.getParent() != null
                    || !Display.isResizable()) {
                    throw new IllegalStateException(
                        "Window resize requires a created, resizable, windowed Display without an AWT parent");
                }
                Method implementation = Display.class.getDeclaredMethod("getImplementation");
                implementation.setAccessible(true);
                Object backend = implementation.invoke(null);
                Method reshape = Class.forName("org.lwjgl.opengl.DisplayImplementation")
                    .getDeclaredMethod("reshape", int.class, int.class, int.class, int.class);
                reshape.setAccessible(true);
                reshape.invoke(backend, Display.getX(), Display.getY(), width, height);
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Native window resize failed", cause);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new IllegalStateException("Unsupported LWJGL 2 window layout", e);
        }
    }
}
