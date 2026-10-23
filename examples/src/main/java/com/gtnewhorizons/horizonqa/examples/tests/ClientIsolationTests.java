package com.gtnewhorizons.horizonqa.examples.tests;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.InputImplementation;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.gtnewhorizons.horizonqa.client.LwjglInput;

/** Supplies noisy native input while exercising the real LWJGL polling and synthetic event paths. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientIsolationTests {

    private ClientIsolationTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void nativeMouseCannotReplaceSyntheticInput(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .client("physical mouse input is isolated", client -> {
                try {
                    checkMouseIsolation();
                } catch (IllegalAccessException e) {
                    throw new AssertionError("Cannot supply native mouse input", e);
                }
            })
            .succeed();
    }

    private static void checkMouseIsolation() throws IllegalAccessException {
        LwjglInput.restoreInput();
        Object original = FieldUtils.readDeclaredStaticField(Mouse.class, "implementation", true);
        Object noisy = Proxy.newProxyInstance(
            InputImplementation.class.getClassLoader(),
            new Class<?>[] { InputImplementation.class },
            (proxy, method, arguments) -> {
                if (method.getName()
                    .equals("pollMouse")) {
                    IntBuffer coordinates = (IntBuffer) arguments[0];
                    coordinates.put(0, 17)
                        .put(1, 23)
                        .put(2, 120);
                    ByteBuffer buttons = (ByteBuffer) arguments[1];
                    buttons.put(0, (byte) 1);
                    return null;
                }
                if (method.getName()
                    .equals("readMouse")) {
                    ((ByteBuffer) arguments[0]).put((byte) 0)
                        .put((byte) 1)
                        .putInt(17)
                        .putInt(23)
                        .putInt(120)
                        .putLong(System.nanoTime());
                    return null;
                }
                try {
                    return method.invoke(original, arguments);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
        try {
            FieldUtils.writeDeclaredStaticField(Mouse.class, "implementation", noisy, true);
            LwjglInput.isolateInput();
            int x = Mouse.getX();
            int y = Mouse.getY();
            Mouse.poll();
            if (Mouse.next() || Mouse.isButtonDown(0)
                || Mouse.getDWheel() != 0
                || Mouse.getX() != x
                || Mouse.getY() != y) {
                throw new AssertionError("Physical mouse input reached the client");
            }
            LwjglInput.binding(-100, true);
            Mouse.poll();
            if (!Mouse.isButtonDown(0) || !Mouse.next()
                || Mouse.getEventButton() != 0
                || !Mouse.getEventButtonState()
                || Mouse.next()) {
                throw new AssertionError("Native polling replaced the synthetic press");
            }
            LwjglInput.binding(-100, false);
            Mouse.poll();
            if (Mouse.isButtonDown(0) || !Mouse.next() || Mouse.getEventButtonState() || Mouse.next()) {
                throw new AssertionError("Native polling replaced the synthetic release");
            }
            LwjglInput.restoreInput();
            Mouse.poll();
            if (!Mouse.isButtonDown(0) || Mouse.getDWheel() != 120 || !Mouse.next()) {
                throw new AssertionError("Restoring native input left the mouse isolated");
            }
        } finally {
            LwjglInput.restoreInput();
            FieldUtils.writeDeclaredStaticField(Mouse.class, "implementation", original, true);
            LwjglInput.release();
            LwjglInput.isolateInput();
        }
    }

    @GameTest(template = "client_smoke", timeoutTicks = 300)
    public static void nativeKeyboardCannotReplaceSyntheticInput(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .client("physical keyboard input is isolated", client -> {
                try {
                    checkKeyboardIsolation();
                } catch (IllegalAccessException e) {
                    throw new AssertionError("Cannot supply native keyboard input", e);
                }
            })
            .succeed();
    }

    private static void checkKeyboardIsolation() throws IllegalAccessException {
        LwjglInput.restoreInput();
        Object original = FieldUtils.readDeclaredStaticField(Keyboard.class, "implementation", true);
        Object noisy = Proxy.newProxyInstance(
            InputImplementation.class.getClassLoader(),
            new Class<?>[] { InputImplementation.class },
            (proxy, method, arguments) -> {
                if (method.getName()
                    .equals("pollKeyboard")) {
                    ByteBuffer keys = (ByteBuffer) arguments[0];
                    for (int index = 0; index < keys.capacity(); index++) keys.put(index, (byte) 0);
                    keys.put(Keyboard.KEY_ESCAPE, (byte) 1);
                    return null;
                }
                if (method.getName()
                    .equals("readKeyboard")) {
                    ((ByteBuffer) arguments[0]).putInt(Keyboard.KEY_ESCAPE)
                        .put((byte) 1)
                        .putInt('x')
                        .putLong(System.nanoTime())
                        .put((byte) 0);
                    return null;
                }
                try {
                    return method.invoke(original, arguments);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
        try {
            FieldUtils.writeDeclaredStaticField(Keyboard.class, "implementation", noisy, true);
            LwjglInput.isolateInput();
            Keyboard.poll();
            if (Keyboard.next() || Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) {
                throw new AssertionError("Physical keyboard input reached the client");
            }
            LwjglInput.binding(Keyboard.KEY_A, true);
            Keyboard.poll();
            if (!Keyboard.isKeyDown(Keyboard.KEY_A) || !Keyboard.next()
                || Keyboard.getEventKey() != Keyboard.KEY_A
                || !Keyboard.getEventKeyState()
                || Keyboard.next()) {
                throw new AssertionError("Native polling replaced the synthetic key press");
            }
            LwjglInput.binding(Keyboard.KEY_A, false);
            Keyboard.poll();
            if (Keyboard.isKeyDown(Keyboard.KEY_A) || !Keyboard.next()
                || Keyboard.getEventKeyState()
                || Keyboard.next()) {
                throw new AssertionError("Native polling replaced the synthetic key release");
            }
            LwjglInput.restoreInput();
            Keyboard.poll();
            if (!Keyboard.isKeyDown(Keyboard.KEY_ESCAPE) || !Keyboard.next() || Keyboard.getEventCharacter() != 'x') {
                throw new AssertionError("Restoring native input left the keyboard isolated");
            }
        } finally {
            LwjglInput.restoreInput();
            FieldUtils.writeDeclaredStaticField(Keyboard.class, "implementation", original, true);
            LwjglInput.release();
            LwjglInput.isolateInput();
        }
    }
}
