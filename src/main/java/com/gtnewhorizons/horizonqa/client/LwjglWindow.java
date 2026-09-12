package com.gtnewhorizons.horizonqa.client;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
            Field lockField = lockClass.getDeclaredField("lock");
            lockField.setAccessible(true);
            synchronized (lockField.get(null)) {
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
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported LWJGL 2 window layout", e);
        }
    }
}
