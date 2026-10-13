package com.gtnewhorizons.horizonqa.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;
import com.gtnewhorizons.horizonqa.internal.FatalErrors;

/** Resizes the native window so the ordinary game loop receives a resize event. */
public final class LwjglWindow {

    private LwjglWindow() {}

    public static void resize(int width, int height) {
        try {
            Method implementation;
            try {
                implementation = Display.class.getDeclaredMethod("getImplementation");
            } catch (NoSuchMethodException modernBackend) {
                Display.setDisplayMode(new DisplayMode(width, height));
                return;
            }
            // LWJGL 2 setDisplayMode recreates the window without publishing a resize event to Minecraft.
            Object lock = FieldUtils
                .readDeclaredStaticField(Class.forName("org.lwjgl.opengl.GlobalLock"), "lock", true);
            synchronized (lock) {
                implementation.setAccessible(true);
                Object backend = implementation.invoke(null);
                Method reshape = Class.forName("org.lwjgl.opengl.DisplayImplementation")
                    .getDeclaredMethod("reshape", int.class, int.class, int.class, int.class);
                reshape.setAccessible(true);
                reshape.invoke(backend, Display.getX(), Display.getY(), width, height);
            }
        } catch (InvocationTargetException error) {
            throw failure(error.getCause());
        } catch (ReflectiveOperationException | LWJGLException | RuntimeException error) {
            throw failure(error);
        }
    }

    private static GameTestInfrastructureException failure(Throwable cause) {
        FatalErrors.rethrow(cause);
        GameTestInfrastructureException failure = new GameTestInfrastructureException(
            "CLIENT_WINDOW",
            "Native window resize failed");
        failure.initCause(cause);
        return failure;
    }
}
