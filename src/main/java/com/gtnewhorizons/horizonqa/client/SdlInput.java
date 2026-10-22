package com.gtnewhorizons.horizonqa.client;

import static org.lwjgl.input.Keyboard.*;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLKeycode.*;
import static org.lwjgl.sdl.SDLVideo.*;

import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_EventFilter;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

/** SDL queue injection for lwjgl3ify 3.0.25. Coordinates are top-left logical window units. */
@Lwjgl3Aware
public final class SdlInput {

    private static final Map<Integer, State> states = new HashMap<>();
    private static ByteBuffer testKeys;
    private static SDL_EventFilter inputFilter;
    private static long previousFilter;
    private static long previousUserData;
    private static final ThreadLocal<Boolean> injecting = ThreadLocal.withInitial(() -> false);

    private SdlInput() {}

    public static boolean isAvailable() {
        try {
            Class.forName("org.lwjglx.Lwjgl3ifyEventLoop", false, SdlInput.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static int mainWidth() {
        return mainSize(true);
    }

    public static int mainHeight() {
        return mainSize(false);
    }

    private static int mainSize(boolean width) {
        java.awt.Point size = logicalSize(mainWindowId());
        return width ? size.x : size.y;
    }

    public static java.awt.Point logicalSize(int windowId) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer w = stack.mallocInt(1);
            java.nio.IntBuffer h = stack.mallocInt(1);
            if (!SDL_GetWindowSize(SDL_GetWindowFromID(windowId), w, h)) {
                throw new IllegalStateException("Cannot read SDL window size: " + SDLError.SDL_GetError());
            }
            return new java.awt.Point(w.get(0), h.get(0));
        }
    }

    public static int mainWindowId() {
        return SDL_GetWindowID((Long) call("org.lwjglx.opengl.Display", "getWindow", new Class<?>[0]));
    }

    /** Allocate main-window key state without taking ownership of another window's active shadow. */
    public static void begin() {
        if (testKeys != null) return;
        ByteBuffer nativeKeys = (ByteBuffer) field("org.lwjglx.input.Keyboard", "sdlKeyPressedArray", null, false);
        if (nativeKeys == null) throw new IllegalStateException("SDL keyboard has not been initialized");
        testKeys = ByteBuffer.allocateDirect(nativeKeys.capacity());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filter = stack.callocPointer(1);
            PointerBuffer userdata = stack.callocPointer(1);
            SDL_GetEventFilter(filter, userdata);
            previousFilter = filter.get(0);
            previousUserData = userdata.get(0);
        }
        int mainId = mainWindowId();
        final SDL_EventFilter prior = previousFilter == 0 ? null : SDL_EventFilter.create(previousFilter);
        final long priorData = previousUserData;
        inputFilter = SDL_EventFilter.create((userdata, address) -> {
            SDL_Event event = SDL_Event.create(address);
            int type = event.type();
            int id = type == SDL_EVENT_MOUSE_MOTION ? event.motion()
                .windowID()
                : type == SDL_EVENT_MOUSE_BUTTON_DOWN || type == SDL_EVENT_MOUSE_BUTTON_UP ? event.button()
                    .windowID()
                    : type == SDL_EVENT_MOUSE_WHEEL ? event.wheel()
                        .windowID() : 0;
            if (id == mainId && !injecting.get()) return false;
            return prior == null || prior.invoke(priorData, address);
        });
        SDL_SetEventFilter(inputFilter, 0);
    }

    public static void end() {
        for (Integer id : new HashSet<>(states.keySet())) release(id);
        testKeys = null;
        if (inputFilter != null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filter = stack.callocPointer(1);
                PointerBuffer userdata = stack.callocPointer(1);
                SDL_GetEventFilter(filter, userdata);
                if (filter.get(0) != inputFilter.address()) {
                    throw new IllegalStateException("SDL input filter changed during the test session");
                }
                nSDL_SetEventFilter(previousFilter, previousUserData);
            }
            inputFilter.free();
            inputFilter = null;
        }
        states.entrySet()
            .removeIf(entry -> entry.getValue().textBuffers.isEmpty());
    }

    /** Offscreen updates its modifier polling buffer only while its window has native input focus. */
    public static void requireInputFocus(int windowId) {
        if ((SDL_GetWindowFlags(SDL_GetWindowFromID(windowId)) & SDL_WINDOW_INPUT_FOCUS) == 0) {
            throw new IllegalStateException("Offscreen modifier input requires native focus for window " + windowId);
        }
    }

    public static void move(int windowId, int x, int y) {
        move(windowId, x, y, false);
    }

    public static void dragMove(int windowId, int x, int y) {
        move(windowId, x, y, true);
    }

    private static void move(int windowId, int x, int y, boolean dispatchMotion) {
        State state = state(windowId);
        try (SDL_Event event = SDL_Event.calloc()) {
            event.motion()
                .type(SDL_EVENT_MOUSE_MOTION)
                .windowID(windowId)
                .timestamp(System.nanoTime())
                .x(x)
                .y(y)
                .xrel(x - state.x)
                .yrel(y - state.y)
                .state(state.buttons);
            state.x = x;
            state.y = y;
            push(event);
        }
        if (windowId == mainWindowId() && !dispatchMotion) {
            // Positioning must render before the gesture dispatches its next motion event.
            while ((Boolean) call("org.lwjglx.input.Mouse", "next", new Class<?>[0])) {}
        }
        applyMainState();
    }

    public static void mouse(int windowId, int x, int y, int button, boolean down) {
        if (button < 0 || button > 4) throw new IllegalArgumentException("SDL supports mouse buttons 0 through 4");
        move(windowId, x, y);
        State state = state(windowId);
        int sdlButton = button == 0 ? 1 : button == 1 ? 3 : button == 2 ? 2 : button + 1;
        if (down) state.buttons |= 1 << (sdlButton - 1);
        else state.buttons &= ~(1 << (sdlButton - 1));
        try (SDL_Event event = SDL_Event.calloc()) {
            event.button()
                .type(down ? SDL_EVENT_MOUSE_BUTTON_DOWN : SDL_EVENT_MOUSE_BUTTON_UP)
                .windowID(windowId)
                .timestamp(System.nanoTime())
                .button((byte) sdlButton)
                .down(down)
                .clicks((byte) 1)
                .x(x)
                .y(y);
            push(event);
        }
        applyMainState();
    }

    public static void scroll(int windowId, int x, int y, int wheelDelta) {
        move(windowId, x, y);
        // lwjgl3ify exposes SDL wheel values directly. Offscreen converts notches to LWJGL units.
        float delta = windowId == mainWindowId() ? wheelDelta : wheelDelta / 120.0f;
        try (SDL_Event event = SDL_Event.calloc()) {
            event.wheel()
                .type(SDL_EVENT_MOUSE_WHEEL)
                .windowID(windowId)
                .timestamp(System.nanoTime())
                .y(delta)
                .mouse_x(x)
                .mouse_y(y);
            push(event);
        }
    }

    public static void key(int windowId, int lwjglKey, char character, boolean down) {
        State state = state(windowId);
        int scan = (Integer) call(
            "org.lwjglx.input.KeyCodes",
            "lwjglToSdlScancode",
            new Class<?>[] { int.class },
            lwjglKey);
        int key = (Integer) call(
            "org.lwjglx.input.KeyCodes",
            "lwjglToSdlKeycode",
            new Class<?>[] { int.class },
            lwjglKey);
        if (scan <= 0 || key == 0) throw new IllegalArgumentException("Unsupported LWJGL key: " + lwjglKey);
        if (windowId != mainWindowId() && down) {
            short mods = modifiers(state);
            int raw = SDLKeyboard.SDL_GetKeyFromScancode(scan, mods, false);
            int printable = key >= 32 && key <= 126 ? key : raw >= 32 && raw <= 126 ? raw : -1;
            char nativeCharacter = printable < 0 ? '\0'
                : (mods & SDL_KMOD_CTRL) != 0 ? (char) (key & 0x1f) : (char) printable;
            if (character != nativeCharacter) {
                throw new IllegalArgumentException(
                    "Offscreen cannot override the native character for LWJGL key " + lwjglKey
                        + ": requested U+"
                        + Integer.toHexString(character)
                        + ", native U+"
                        + Integer.toHexString(nativeCharacter)
                        + ". Supply the matching native key and character");
            }
        }
        if (down) state.keys.add(lwjglKey);
        else state.keys.remove(lwjglKey);
        if (windowId == mainWindowId()) {
            begin();
            testKeys.put(scan, (byte) (down ? 1 : 0));
        }
        long timestamp = System.nanoTime();
        try (SDL_Event event = SDL_Event.calloc()) {
            event.key()
                .type(down ? SDL_EVENT_KEY_DOWN : SDL_EVENT_KEY_UP)
                .windowID(windowId)
                .timestamp(timestamp)
                .scancode(scan)
                .key(key)
                .down(down)
                .mod(modifiers(state));
            if (windowId == mainWindowId()) {
                push(event);
                setMainEventCharacter(timestamp, down ? character : '\0');
            } else {
                push(event);
            }
        }
    }

    /** Preserve the supplied-character contract after SDL routing, before legacy screen dispatch. */
    private static void setMainEventCharacter(long timestamp, char character) {
        Iterable<?> queue = (Iterable<?>) field("org.lwjglx.input.Keyboard", "eventQueue", null, false);
        try {
            for (Object event : queue) {
                if (event.getClass()
                    .getField("nano")
                    .getLong(event) == timestamp) {
                    event.getClass()
                        .getField("codepoint")
                        .setInt(event, character);
                    return;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported lwjgl3ify key event layout", e);
        }
        throw new IllegalStateException("SDL did not queue the synthetic main-window key event");
    }

    /** Scope main GUI polling without mutating or replacing Offscreen's retained keyboard shadow. */
    public static void withMainKeyboard(Runnable action) {
        begin();
        Object previous = field("org.lwjglx.input.Keyboard", "sdlKeyPressedArray", null, false);
        field("org.lwjglx.input.Keyboard", "sdlKeyPressedArray", testKeys, true);
        try {
            action.run();
        } finally {
            if (field("org.lwjglx.input.Keyboard", "sdlKeyPressedArray", null, false) == testKeys) {
                field("org.lwjglx.input.Keyboard", "sdlKeyPressedArray", previous, true);
            }
        }
    }

    public static void text(int windowId, String text) {
        State state = state(windowId);
        ByteBuffer utf8 = MemoryUtil.memUTF8(text);
        state.textBuffers.add(utf8);
        try (SDL_Event event = SDL_Event.calloc()) {
            event.text()
                .type(SDL_EVENT_TEXT_INPUT)
                .windowID(windowId)
                .timestamp(System.nanoTime())
                .text(utf8);
            // Offscreen copies the event into its ring, retaining the UTF-8 pointer until its next frame.
            push(event);
        }
        if (windowId == mainWindowId()) frameConsumed(windowId);
    }

    /** Call only after Offscreen has drained the events for this window. */
    public static void frameConsumed(int windowId) {
        State state = states.get(windowId);
        if (state == null) return;
        for (ByteBuffer buffer : state.textBuffers) MemoryUtil.memFree(buffer);
        state.textBuffers.clear();
    }

    public static void release(int windowId) {
        State state = states.get(windowId);
        if (state == null) return;
        if (SDL_GetWindowFromID(windowId) != 0) {
            for (Integer key : new HashSet<>(state.keys)) key(windowId, key, '\0', false);
            for (int button = 0; button < 5; button++) {
                int sdlButton = button == 0 ? 1 : button == 1 ? 3 : button == 2 ? 2 : button + 1;
                if ((state.buttons & (1 << (sdlButton - 1))) != 0) mouse(windowId, state.x, state.y, button, false);
            }
        }
        if (SDL_GetWindowFromID(windowId) == 0) frameConsumed(windowId);
        if (state.textBuffers.isEmpty()) states.remove(windowId);
        if (windowId == mainWindowId()) call("org.lwjglx.input.Mouse", "getDWheel", new Class<?>[0]);
        applyMainState();
    }

    /** Publishes the selected window's held buttons only while its GUI context is active. */
    public static <T> T withMouseState(int windowId, Supplier<T> action) {
        Object previous = field("org.lwjglx.input.Mouse", "sdlMouseButtonFlags", null, false);
        State state = states.get(windowId);
        field("org.lwjglx.input.Mouse", "sdlMouseButtonFlags", state == null ? 0 : state.buttons, true);
        try {
            return action.get();
        } finally {
            field("org.lwjglx.input.Mouse", "sdlMouseButtonFlags", previous, true);
        }
    }

    public static void applyMainState() {
        if (testKeys == null) return;
        State state = states.get(mainWindowId());
        field("org.lwjglx.input.Mouse", "sdlMouseButtonFlags", state == null ? 0 : state.buttons, true);
        call("org.lwjglx.input.Mouse", "poll", new Class<?>[0]);
    }

    public static void resize(int windowId, int width, int height) {
        LwjglWindow.validateSize(width, height);
        state(windowId);
        Runnable resize = () -> {
            if (!SDL_SetWindowSize(SDL_GetWindowFromID(windowId), width, height)) {
                throw new IllegalStateException("SDL window resize failed: " + SDLError.SDL_GetError());
            }
        };
        call(
            "me.eigenraven.lwjgl3ify.client.MainThreadExec",
            "runOnMainThread",
            new Class<?>[] { Runnable.class },
            resize);
    }

    /** Main-window callers request framebuffer pixels, while SDL_SetWindowSize accepts logical units. */
    public static void resizeMainFramebuffer(int width, int height) {
        LwjglWindow.validateSize(width, height);
        int windowId = mainWindowId();
        java.awt.Point logical = logicalSize(windowId);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer pixelWidth = stack.mallocInt(1);
            java.nio.IntBuffer pixelHeight = stack.mallocInt(1);
            if (!SDL_GetWindowSizeInPixels(SDL_GetWindowFromID(windowId), pixelWidth, pixelHeight)) {
                throw new IllegalStateException("Cannot read SDL framebuffer size: " + SDLError.SDL_GetError());
            }
            resize(
                windowId,
                LwjglWindow.logicalSizeForPixels(width, logical.x, pixelWidth.get(0)),
                LwjglWindow.logicalSizeForPixels(height, logical.y, pixelHeight.get(0)));
        }
    }

    private static State state(int windowId) {
        if (windowId == 0 || SDL_GetWindowFromID(windowId) == 0) {
            throw new IllegalStateException("SDL window is not open: " + windowId);
        }
        return states.computeIfAbsent(windowId, ignored -> new State());
    }

    private static short modifiers(State state) {
        int result = 0;
        int[] keys = { KEY_LSHIFT, KEY_RSHIFT, KEY_LCONTROL, KEY_RCONTROL, KEY_LMENU, KEY_RMENU, KEY_LMETA, KEY_RMETA };
        int[] masks = { SDL_KMOD_LSHIFT, SDL_KMOD_RSHIFT, SDL_KMOD_LCTRL, SDL_KMOD_RCTRL, SDL_KMOD_LALT, SDL_KMOD_RALT,
            SDL_KMOD_LGUI, SDL_KMOD_RGUI };
        for (int i = 0; i < keys.length; i++) if (state.keys.contains(keys[i])) result |= masks[i];
        return (short) result;
    }

    private static void push(SDL_Event event) {
        SDLError.SDL_ClearError();
        boolean queued;
        injecting.set(true);
        try {
            queued = SDL_PushEvent(event);
        } finally {
            injecting.remove();
        }
        String error = SDLError.SDL_GetError();
        // Offscreen consumes its events inside the SDL filter and deliberately returns false.
        if (!queued && !error.isEmpty()) throw new IllegalStateException("SDL rejected test input: " + error);
        call("org.lwjglx.Lwjgl3ifyEventLoop", "pumpEvents", new Class<?>[0]);
        applyMainState();
    }

    private static Object call(String owner, String method, Class<?>[] parameters, Object... arguments) {
        try {
            return Class.forName(owner)
                .getMethod(method, parameters)
                .invoke(null, arguments);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("SDL operation failed: " + owner + "." + method, e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported lwjgl3ify runtime: " + owner + "." + method, e);
        }
    }

    private static Object field(String owner, String name, Object value, boolean write) {
        try {
            java.lang.reflect.Field field = Class.forName(owner)
                .getField(name);
            if (write) field.set(null, value);
            return write ? null : field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported lwjgl3ify runtime: " + owner + "." + name, e);
        }
    }

    private static final class State {

        private int x;
        private int y;
        private int buttons;
        private final Set<Integer> keys = new HashSet<>();
        private final List<ByteBuffer> textBuffers = new ArrayList<>();
    }
}
