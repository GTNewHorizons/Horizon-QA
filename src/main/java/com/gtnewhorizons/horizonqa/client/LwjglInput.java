package com.gtnewhorizons.horizonqa.client;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** LWJGL 2.9 event and polled-state injection for real screen dispatch, without native cursor movement. */
public final class LwjglInput {

    private static boolean hasPointer;
    private static int pointerX;
    private static int pointerY;
    private static int heldButton = -1;
    private static GuiScreen dragScreen;

    private LwjglInput() {}

    public static void move(GuiScreen screen, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        pointerX = x * mc.displayWidth / screen.width;
        pointerY = (screen.height - y - 1) * mc.displayHeight / screen.height;
        hasPointer = true;
        applyPointer();
    }

    public static void applyPointer() {
        if (!hasPointer) return;
        set(Mouse.class, "x", pointerX);
        set(Mouse.class, "y", pointerY);
        if (heldButton >= 0) buffer(Mouse.class, "buttons").put(heldButton, (byte) 1);
    }

    public static void end() {
        release();
        hasPointer = false;
    }

    public static void mouse(GuiScreen screen, int x, int y, int button, boolean down) {
        Minecraft mc = Minecraft.getMinecraft();
        int rawX = x * mc.displayWidth / screen.width;
        int rawY = (screen.height - y - 1) * mc.displayHeight / screen.height;
        mouseEvent(rawX, rawY, button, down);
        dispatch(screen);
    }

    /** Starts a held gesture whose polled state survives native input polling between frames. */
    public static void beginDrag(GuiScreen screen, int x, int y, int button) {
        if (isDragging()) throw new IllegalStateException("A drag is already active");
        if (button < 0 || button >= Mouse.getButtonCount()) throw new IllegalArgumentException("Invalid mouse button");
        heldButton = button;
        dragScreen = screen;
        mouse(screen, x, y, button, true);
    }

    /** Delivers native motion with the existing button still held. */
    public static void dragMove(GuiScreen screen, int x, int y) {
        move(screen, x, y);
        writeMouseEvent(pointerX, pointerY, -1, false, 0);
        dispatch(screen);
    }

    public static boolean isDragging() {
        return heldButton >= 0;
    }

    /** A replacement screen must never inherit the previous screen's held gesture. */
    public static void screenChanging(GuiScreen next) {
        if (isDragging() && next != dragScreen) release();
    }

    /** Supplies a configured Minecraft binding for consumption by the normal input loop. */
    public static void binding(int key, boolean down) {
        if (key < 0) mouseEvent(Mouse.getX(), Mouse.getY(), key + 100, down);
        else keyEvent(key, '\0', down);
    }

    /** Delivers a single native wheel event and matching polled wheel delta to the real screen. */
    public static void scroll(GuiScreen screen, int x, int y, int wheelDelta) {
        move(screen, x, y);
        set(Mouse.class, "dwheel", wheelDelta);
        writeMouseEvent(pointerX, pointerY, -1, false, wheelDelta);
        dispatch(screen);
    }

    private static void mouseEvent(int rawX, int rawY, int button, boolean down) {
        if (button < 0 || button >= Mouse.getButtonCount()) throw new IllegalArgumentException("Invalid mouse button");
        writeMouseEvent(rawX, rawY, button, down, 0);
        buffer(Mouse.class, "buttons").put(button, (byte) (down ? 1 : 0));
    }

    private static void writeMouseEvent(int rawX, int rawY, int button, boolean down, int wheelDelta) {
        ByteBuffer event = buffer(Mouse.class, "readBuffer");
        event.clear();
        event.put((byte) button)
            .put((byte) (down ? 1 : 0))
            .putInt(rawX)
            .putInt(rawY)
            .putInt(wheelDelta)
            .putLong(System.nanoTime())
            .flip();
        set(Mouse.class, "x", rawX);
        set(Mouse.class, "y", rawY);
    }

    public static void shift(GuiScreen screen, boolean down) {
        key(screen, Keyboard.KEY_LSHIFT, '\0', down);
    }

    public static void key(GuiScreen screen, int key, char character, boolean down) {
        keyEvent(key, character, down);
        dispatch(screen);
    }

    private static void keyEvent(int key, char character, boolean down) {
        if (key <= 0 || key >= Keyboard.KEYBOARD_SIZE) throw new IllegalArgumentException("Invalid keyboard key");
        ByteBuffer event = buffer(Keyboard.class, "readBuffer");
        event.clear();
        event.putInt(key)
            .put((byte) (down ? 1 : 0))
            .putInt(down ? character : 0)
            .putLong(System.nanoTime())
            .put((byte) 0)
            .flip();
        buffer(Keyboard.class, "keyDownBuffer").put(key, (byte) (down ? 1 : 0));
    }

    private static void dispatch(GuiScreen screen) {
        screen.handleInput();
    }

    public static void release() {
        heldButton = -1;
        dragScreen = null;
        KeyBinding.unPressAllKeys();
        set(Mouse.class, "dwheel", 0);
        clear(buffer(Mouse.class, "buttons"));
        clear(buffer(Keyboard.class, "keyDownBuffer"));
        buffer(Mouse.class, "readBuffer").limit(0);
        buffer(Keyboard.class, "readBuffer").limit(0);
    }

    private static void clear(ByteBuffer buffer) {
        for (int index = 0; index < buffer.capacity(); index++) buffer.put(index, (byte) 0);
    }

    private static ByteBuffer buffer(Class<?> owner, String name) {
        try {
            return (ByteBuffer) field(owner, name).get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported LWJGL 2 input layout: " + owner.getName() + "." + name, e);
        }
    }

    private static void set(Class<?> owner, String name, Object value) {
        try {
            field(owner, name).set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported LWJGL 2 input layout: " + owner.getName() + "." + name, e);
        }
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
