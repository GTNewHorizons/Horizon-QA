package com.gtnewhorizons.horizonqa.client;

import java.awt.AWTException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;

/** Native OS input, consumed by the ordinary Minecraft/Forge loop on either LWJGL backend. */
public final class NativeInput {

    private final Robot robot;
    private final Point originalPointer;
    private final Set<Integer> keys = new HashSet<>();
    private final Set<Integer> buttons = new HashSet<>();

    public NativeInput() {
        try {
            robot = new Robot();
            robot.setAutoDelay(0);
            originalPointer = MouseInfo.getPointerInfo()
                .getLocation();
        } catch (AWTException | RuntimeException error) {
            throw infrastructure("Native input requires a desktop display (use a private Xvfb on Linux)", error);
        }
    }

    public void move(GuiScreen screen, int x, int y) {
        Point target = rawPoint(screen, x, y);
        robot.mouseMove(Display.getX() + target.x, Display.getY() + target.y);
    }

    public void correctPosition(GuiScreen screen, int x, int y) {
        Point target = rawPoint(screen, x, y);
        Point physical = MouseInfo.getPointerInfo()
            .getLocation();
        int observedY = Minecraft.getMinecraft().displayHeight - Mouse.getY() - 1;
        robot.mouseMove(physical.x + target.x - Mouse.getX(), physical.y + target.y - observedY);
    }

    private static Point rawPoint(GuiScreen screen, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        int rawX = (int) ((x + 0.5) * mc.displayWidth / screen.width);
        int rawY = (int) ((y + 0.5) * mc.displayHeight / screen.height);
        return new Point(rawX, rawY);
    }

    public void mouse(int button, boolean down) {
        if (button < 0 || button > 2) throw new IllegalArgumentException("Mouse button must be 0, 1, or 2");
        int mask = InputEvent.getMaskForButton(button == 1 ? 3 : button == 2 ? 2 : 1);
        if (down) {
            buttons.add(mask);
            robot.mousePress(mask);
        } else {
            robot.mouseRelease(mask);
            buttons.remove(mask);
        }
    }

    public void key(int awtKeyCode, boolean down) {
        if (down) {
            keys.add(awtKeyCode);
            robot.keyPress(awtKeyCode);
        } else {
            robot.keyRelease(awtKeyCode);
            keys.remove(awtKeyCode);
        }
    }

    public void useBinding(int minecraftKey, boolean down) {
        if (minecraftKey < 0) {
            if (minecraftKey < -100 || minecraftKey > -98)
                throw infrastructure("Unsupported native mouse binding: " + minecraftKey, null);
            mouse(minecraftKey + 100, down);
            return;
        }
        String name = Keyboard.getKeyName(minecraftKey);
        if (name == null) throw infrastructure("Unsupported use-item binding: " + minecraftKey, null);
        if (name.equals("RSHIFT") || name.equals("RCONTROL") || name.equals("RMENU") || name.equals("NUMPADENTER")) {
            throw infrastructure("Native Robot cannot represent this exact binding: " + name, null);
        }
        name = switch (name) {
            case "RETURN" -> "ENTER";
            case "LSHIFT" -> "SHIFT";
            case "LCONTROL" -> "CONTROL";
            case "LMENU" -> "ALT";
            case "LBRACKET" -> "OPEN_BRACKET";
            case "RBRACKET" -> "CLOSE_BRACKET";
            default -> name;
        };
        try {
            key(
                KeyEvent.class.getField("VK_" + name)
                    .getInt(null),
                down);
        } catch (ReflectiveOperationException error) {
            throw infrastructure("Unsupported native use-item key: " + name, error);
        }
    }

    public void release() {
        for (int mask : buttons) robot.mouseRelease(mask);
        buttons.clear();
        for (int key : keys) robot.keyRelease(key);
        keys.clear();
        KeyBinding.unPressAllKeys();
    }

    public void restorePointer() {
        robot.mouseMove(originalPointer.x, originalPointer.y);
    }

    public boolean bindingReleased(int binding) {
        return binding < 0 ? !Mouse.isButtonDown(binding + 100) : !Keyboard.isKeyDown(binding);
    }

    public boolean keyReleased(int code) {
        if (code == KeyEvent.VK_SHIFT)
            return !Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (code == KeyEvent.VK_CONTROL)
            return !Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) && !Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        if (code == KeyEvent.VK_ALT)
            return !Keyboard.isKeyDown(Keyboard.KEY_LMENU) && !Keyboard.isKeyDown(Keyboard.KEY_RMENU);
        // Compare public key names to AWT codes; no private LWJGL buffers or toolkit handlers.
        for (int key = 1; key < Keyboard.KEYBOARD_SIZE; key++) {
            String name = Keyboard.getKeyName(key);
            if (name == null || !Keyboard.isKeyDown(key)) continue;
            if (name.equals("RETURN")) name = "ENTER";
            if (name.equals("BACK")) name = "BACK_SPACE";
            try {
                if (KeyEvent.class.getField("VK_" + name)
                    .getInt(null) == code) return false;
            } catch (ReflectiveOperationException ignored) {}
        }
        return true;
    }

    public static boolean released() {
        for (int button = 0; button < Mouse.getButtonCount(); button++) {
            if (Mouse.isButtonDown(button)) return false;
        }
        for (int key = 1; key < Keyboard.KEYBOARD_SIZE; key++) {
            if (Keyboard.isKeyDown(key)) return false;
        }
        return true;
    }

    private static GameTestInfrastructureException infrastructure(String message, Throwable cause) {
        GameTestInfrastructureException error = new GameTestInfrastructureException("CLIENT_INPUT", message);
        if (cause != null) error.initCause(cause);
        return error;
    }
}
