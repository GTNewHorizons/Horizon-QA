package com.gtnewhorizons.horizonqa.examples.tests;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.gtnewhorizons.horizonqa.api.client.ClientWindow;
import com.gtnewhorizons.horizonqa.client.SdlInput;

import cpw.mods.fml.common.Loader;

/** Explicitly selected integration example requiring Offscreen and its SDL lwjgl3ify runtime */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientWindowTests {

    private ClientWindowTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 600)
    public static void offscreenShiftClickWithoutNativeFocus(GameTestHelper helper) {
        checkUnfocusedShift(helper, false);
    }

    @GameTest(template = "client_smoke", timeoutTicks = 600, required = false)
    public static void offscreenShiftFailureRestoresKeyboard(GameTestHelper helper) {
        checkUnfocusedShift(helper, true);
    }

    private static void checkUnfocusedShift(GameTestHelper helper, boolean failOnPress) {
        requireOffscreen(helper);
        AtomicReference<ClientWindow> target = new AtomicReference<>();
        AtomicReference<Integer> targetId = new AtomicReference<>();
        AtomicReference<CounterScreen> selected = new AtomicReference<>();
        AtomicReference<NativeKeys> nativeKeys = new AtomicReference<>();
        ClientTarget increment = ClientTarget.of(
            "unfocused.increment",
            w -> w.screen(CounterScreen.class)
                .target());
        ClientTest.scenario(helper)
            .client("open two independently polled windows", c -> {
                CounterScreen main = new CounterScreen("Main keyboard isolation");
                main.rejectShift = true;
                Minecraft.getMinecraft()
                    .displayGuiScreen(main);
                CounterScreen first = new CounterScreen("First modifier window");
                CounterScreen second = new CounterScreen("Second modifier window");
                Object a = open(() -> first);
                c.afterTest(() -> closeIfOpen(a));
                Object b = open(() -> second);
                c.afterTest(() -> closeIfOpen(b));
                int firstId = (Integer) invoke(a, "getId");
                int secondId = (Integer) invoke(b, "getId");
                boolean useFirst = !WindowsWindowThreads.hasFocus(firstId);
                int id = useFirst ? firstId : secondId;
                if (WindowsWindowThreads.hasFocus(id)) throw new AssertionError("No unfocused fixture window");
                selected.set(useFirst ? first : second);
                selected.get().failShiftPress = failOnPress;
                targetId.set(id);
                (useFirst ? second : first).rejectShift = true;
                target.set(c.offscreenWindow(id));
                c.afterTest(() -> {
                    if (!selected.get().shiftAtPress) throw new AssertionError("The hosted press never observed Shift");
                    if (nativeKeys.get() != null) nativeKeys.get()
                        .assertUnchanged();
                    if (GuiScreen.isShiftKeyDown() || Keyboard.isKeyDown(Keyboard.KEY_LSHIFT))
                        throw new AssertionError("Shift leaked after the hosted operation");
                });
            })
            .async(
                "position before taking the keyboard snapshot",
                c -> target.get()
                    .hover(increment))
            .client("remember the native polling buffer", c -> nativeKeys.set(new NativeKeys()))
            .async("restore polling after an exceptional scoped callback", c -> {
                IllegalStateException expected = new IllegalStateException("Intentional scoped observation failure");
                return target.get()
                    .run(w -> { throw expected; })
                    .handle((ignored, error) -> {
                        if (error != expected)
                            throw new AssertionError("Scoped callback failure was not preserved", error);
                        nativeKeys.get()
                            .assertRestored();
                        return null;
                    });
            })
            .async(
                "Shift click the unfocused window",
                c -> target.get()
                    .shiftClick(0, increment))
            .async(
                "both callbacks polled scoped Shift",
                c -> target.get()
                    .run(w -> {
                        CounterScreen screen = selected.get();
                        if (WindowsWindowThreads.hasFocus(targetId.get()))
                            throw new AssertionError("The test acquired native input focus");
                        if (!screen.shiftAtPress || !screen.shiftAtRelease || screen.clicks != 1)
                            throw new AssertionError("Hosted mouse callbacks did not see Shift");
                        if (GuiScreen.isShiftKeyDown()) throw new AssertionError("Shift remained held in the target");
                    }))
            .client(
                "native polling state was restored",
                c -> nativeKeys.get()
                    .assertRestored())
            .succeed();
    }

    private static final class NativeKeys {

        private final java.lang.reflect.Field pollingField;
        private final java.nio.ByteBuffer buffer;
        private final byte[] saved;
        private final java.nio.ByteBuffer nativeBuffer = WindowsWindowThreads.nativeKeyboard();
        private final byte[] nativeSaved = new byte[nativeBuffer.capacity()];

        private NativeKeys() {
            try {
                pollingField = Class.forName("org.lwjglx.input.Keyboard")
                    .getField("sdlKeyPressedArray");
                buffer = (java.nio.ByteBuffer) pollingField.get(null);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Cannot observe the native keyboard polling buffer", e);
            }
            saved = new byte[buffer.capacity()];
            for (int i = 0; i < saved.length; i++) saved[i] = buffer.get(i);
            for (int i = 0; i < nativeSaved.length; i++) nativeSaved[i] = nativeBuffer.get(i);
        }

        private void assertUnchanged() {
            for (int i = 0; i < saved.length; i++) {
                if (saved[i] != buffer.get(i))
                    throw new AssertionError("Enclosing keyboard polling state changed at " + i);
            }
            for (int i = 0; i < nativeSaved.length; i++) {
                if (nativeSaved[i] != nativeBuffer.get(i))
                    throw new AssertionError("Native SDL keyboard state changed at " + i);
            }
        }

        private void assertRestored() {
            assertUnchanged();
            try {
                if (pollingField.get(null) != buffer)
                    throw new AssertionError("Native polling buffer was not restored");
            } catch (IllegalAccessException e) {
                throw new AssertionError("Cannot inspect the restored polling buffer", e);
            }
        }
    }

    @GameTest(template = "client_smoke", timeoutTicks = 600)
    public static void offscreenCaptureShowsPresentedFrame(GameTestHelper helper) {
        requireOffscreen(helper);
        AtomicReference<ClientWindow> window = new AtomicReference<>();
        ClientTest.scenario(helper)
            .client("open the native presentation fixture", c -> {
                Object opened = open(PresentationScreen::new);
                c.afterTest(() -> closeIfOpen(opened));
                WindowsWindowThreads.assertSameMessageThread((Integer) invoke(opened, "getId"));
                window.set(c.offscreenWindow((Integer) invoke(opened, "getId")));
            })
            .async(
                "verify the presented image",
                c -> window.get()
                    .capture("presented-initial")
                    .thenAccept(ClientWindowTests::assertPresentedFrame))
            .async(
                "resize the native framebuffer",
                c -> window.get()
                    .resize(800, 600))
            .async(
                "verify presentation after resize",
                c -> window.get()
                    .capture("presented-resized")
                    .thenAccept(ClientWindowTests::assertPresentedFrame))
            .async(
                "close the first native context",
                c -> window.get()
                    .close())
            .client("open a new native context", c -> {
                Object opened = open(PresentationScreen::new);
                c.afterTest(() -> closeIfOpen(opened));
                WindowsWindowThreads.assertSameMessageThread((Integer) invoke(opened, "getId"));
                window.set(c.offscreenWindow((Integer) invoke(opened, "getId")));
            })
            .async(
                "verify presentation in the new context",
                c -> window.get()
                    .capture("presented-reopened")
                    .thenAccept(ClientWindowTests::assertPresentedFrame))
            .succeed();
    }

    @me.eigenraven.lwjgl3ify.api.Lwjgl3Aware
    private static final class WindowsWindowThreads {

        private static java.nio.ByteBuffer nativeKeyboard() {
            return org.lwjgl.sdl.SDLKeyboard.SDL_GetKeyboardState();
        }

        private static boolean hasFocus(int windowId) {
            return (org.lwjgl.sdl.SDLVideo.SDL_GetWindowFlags(org.lwjgl.sdl.SDLVideo.SDL_GetWindowFromID(windowId))
                & org.lwjgl.sdl.SDLVideo.SDL_WINDOW_INPUT_FOCUS) != 0;
        }

        private static void assertSameMessageThread(int windowId) {
            if (org.lwjgl.system.Platform.get() != org.lwjgl.system.Platform.WINDOWS) return;
            long window = org.lwjgl.sdl.SDLVideo.SDL_GetWindowFromID(windowId);
            int mainThread = ownerThread(
                org.lwjgl.sdl.SDLVideo.SDL_GetWindowFromID(com.gtnewhorizons.horizonqa.client.SdlInput.mainWindowId()));
            int extraThread = ownerThread(window);
            if (mainThread == 0 || extraThread != mainThread) {
                throw new AssertionError(
                    "Offscreen window is not owned by the native message-pump thread: main=" + mainThread
                        + ", extra="
                        + extraThread);
            }
        }

        private static int ownerThread(long window) {
            int properties = org.lwjgl.sdl.SDLVideo.SDL_GetWindowProperties(window);
            long handle = org.lwjgl.sdl.SDLProperties
                .SDL_GetPointerProperty(properties, org.lwjgl.sdl.SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0L);
            if (handle == 0L) throw new AssertionError("SDL did not expose the native Windows window");
            long function = org.lwjgl.system.windows.User32.getLibrary()
                .getFunctionAddress("GetWindowThreadProcessId");
            return org.lwjgl.system.JNI.callPPI(handle, 0L, function);
        }
    }

    private static void assertPresentedFrame(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) throw new AssertionError("Capture is not a readable image: " + file);
            int left = image.getRGB(image.getWidth() / 4, image.getHeight() / 2) & 0xFFFFFF;
            int right = image.getRGB(image.getWidth() * 3 / 4, image.getHeight() / 2) & 0xFFFFFF;
            if (left != 0 || right != 0xFFFFFF) {
                throw new AssertionError(
                    "Native window did not present the black/white fixture: left=0x" + Integer.toHexString(left)
                        + ", right=0x"
                        + Integer.toHexString(right));
            }
        } catch (IOException error) {
            throw new AssertionError("Cannot read presented-window capture: " + file, error);
        }
    }

    private static final class PresentationScreen extends GuiScreen {

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawRect(0, 0, width / 2, height, 0xFF000000);
            drawRect(width / 2, 0, width, height, 0xFFFFFFFF);
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }

    @GameTest(template = "client_smoke", timeoutTicks = 600)
    public static void unboundOffscreenInputFailureDoesNotFailTest(GameTestHelper helper) {
        requireOffscreen(helper);
        AtomicReference<FailingScreen> screen = new AtomicReference<>();
        ClientTest.scenario(helper)
            .client("open an unbound Offscreen window", c -> {
                screen.set(new FailingScreen());
                Object opened = open(screen::get);
                c.afterTest(() -> closeIfOpen(opened));
                int id = (Integer) invoke(opened, "getId");
                SdlInput.mouse(id, 30, 30, 0, true);
                SdlInput.mouse(id, 30, 30, 0, false);
            })
            .awaitClient(
                "unbound mouse handler ran",
                c -> { if (!screen.get().changed) throw new AssertionError("Offscreen did not dispatch the click"); })
            .succeed();
    }

    @GameTest(template = "client_smoke", timeoutTicks = 600)
    public static void offscreenBindingLeavesWindowForOwnerCleanup(GameTestHelper helper) {
        requireOffscreen(helper);
        ClientTest.scenario(helper)
            .client("bind a window owned by the fixture", c -> {
                Object opened = open(() -> new CounterScreen("Borrowed window"));
                c.afterTest(() -> {
                    if (!(Boolean) invoke(opened, "isOpen")) {
                        throw new AssertionError("Horizon-QA closed the window before its owner cleanup");
                    }
                    closeIfOpen(opened);
                });
                c.offscreenWindow((Integer) invoke(opened, "getId"));
            })
            .succeed();
    }

    @GameTest(template = "client_smoke", timeoutTicks = 600)
    public static void offscreenWindowKeepsMainUsable(GameTestHelper helper) {
        requireOffscreen(helper);
        AtomicReference<CounterScreen> main = new AtomicReference<>();
        AtomicReference<CounterScreen> extra = new AtomicReference<>();
        AtomicReference<ClientWindow> window = new AtomicReference<>();
        ClientTarget increment = ClientTarget.of(
            "counter.increment",
            w -> w.screen(CounterScreen.class)
                .target());
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(100)
            .client("open independent counter screens", c -> {
                main.set(new CounterScreen("Main window"));
                extra.set(new CounterScreen("Offscreen window"));
                Minecraft.getMinecraft()
                    .displayGuiScreen(main.get());
                Object opened = open(extra::get);
                c.afterTest(() -> closeIfOpen(opened));
                int id = (Integer) invoke(opened, "getId");
                window.set(c.offscreenWindow(id));
            })
            .hover(increment)
            .async(
                "hover Offscreen window",
                c -> window.get()
                    .hover(increment))
            .async(
                "hover rendered without clicking",
                c -> window.get()
                    .run(w -> {
                        CounterScreen screen = w.screen(CounterScreen.class);
                        if (!screen.hovered || screen.clicks != 0 || screen.pressed || screen.releasedAt != null)
                            throw new AssertionError("Hover dispatched a mouse button or missed the additional window");
                    }))
            .client("main window was not clicked by hover", c -> {
                if (main.get().clicks != 0 || main.get().pressed || main.get().releasedAt != null)
                    throw new AssertionError("Hover dispatched a mouse button in the main window");
            })
            .step("click main window")
            .click(increment)
            .async(
                "click Offscreen window",
                c -> window.get()
                    .click(0, increment))
            .awaitAsync(
                "Offscreen accepted its click",
                c -> window.get()
                    .run(w -> {
                        if (w.screen() != extra.get() || extra.get().clicks != 1) {
                            throw new AssertionError("Offscreen did not receive exactly one click");
                        }
                    }))
            .async(
                "main screen remained independent",
                c -> c.mainWindow()
                    .run(w -> {
                        if (w.screen() != main.get() || main.get().clicks != 1) {
                            throw new AssertionError("Offscreen input changed the main screen");
                        }
                    }))
            .async(
                "capture main window",
                c -> c.mainWindow()
                    .capture("main-counter"))
            .async(
                "capture Offscreen window",
                c -> window.get()
                    .capture("offscreen-counter"))
            .async(
                "resize Offscreen window",
                c -> window.get()
                    .resize(800, 600))
            .async(
                "click resized Offscreen window",
                c -> window.get()
                    .click(0, increment))
            .awaitAsync(
                "resized screen accepted its click",
                c -> window.get()
                    .run(w -> {
                        if (w.screen() != extra.get() || extra.get().clicks != 2) {
                            throw new AssertionError("Resize lost the screen or routed input incorrectly");
                        }
                    }))
            .async(
                "capture resized Offscreen window",
                c -> window.get()
                    .capture("offscreen-resized"))
            .async(
                "close test-owned Offscreen window",
                c -> window.get()
                    .close())
            .async(
                "main window remains usable",
                c -> c.mainWindow()
                    .click(0, increment))
            .async(
                "verify independent close",
                c -> c.mainWindow()
                    .run(w -> {
                        if (window.get()
                            .isOpen() || w.screen() != main.get()
                            || main.get().clicks != 2) {
                            throw new AssertionError("Closing Offscreen affected the main window");
                        }
                    }))
            .succeed();
    }

    @GameTest(template = "client_smoke", timeoutTicks = 600)
    public static void offscreenDragKeepsInputScoped(GameTestHelper helper) {
        requireOffscreen(helper);
        AtomicReference<CounterScreen> main = new AtomicReference<>();
        AtomicReference<CounterScreen> extra = new AtomicReference<>();
        AtomicReference<ClientWindow> window = new AtomicReference<>();
        AtomicReference<Point> endpoint = new AtomicReference<>();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(100)
            .client("open polling drag fixture beside main screen", c -> {
                main.set(new CounterScreen("Main window"));
                extra.set(new CounterScreen("Offscreen drag"));
                Minecraft.getMinecraft()
                    .displayGuiScreen(main.get());
                Object opened = open(extra::get);
                c.afterTest(() -> closeIfOpen(opened));
                window.set(c.offscreenWindow((Integer) invoke(opened, "getId")));
            })
            .async(
                "drag with window-local polled state",
                c -> window.get()
                    .drag(
                        0,
                        "offscreen.increment",
                        w -> extra.get()
                            .target(),
                        w -> {
                            Point point = new Point(extra.get().width - 30, extra.get().height - 30);
                            endpoint.set(point);
                            return point;
                        },
                        4))
            .client("verify drag frames and independent main input", c -> {
                if (extra.get().heldFrames < 4 || !endpoint.get()
                    .equals(extra.get().releasedAt))
                    throw new AssertionError("Offscreen drag lost held frames or its endpoint");
                if (main.get().clicks != 0 || main.get().pressed || Mouse.isButtonDown(0))
                    throw new AssertionError("Offscreen drag leaked into main input");
            })
            .succeed();
    }

    /** Expected optional failure. The report must retain the exception after the click changes state */
    @GameTest(template = "client_smoke", timeoutTicks = 300, required = false)
    public static void offscreenClickFailureIsReported(GameTestHelper helper) {
        checkHandlerFailure(helper, false);
    }

    /** Expected optional failure. The report must retain the exception after the key changes state */
    @GameTest(template = "client_smoke", timeoutTicks = 300, required = false)
    public static void offscreenKeyFailureIsReported(GameTestHelper helper) {
        checkHandlerFailure(helper, true);
    }

    private static void checkHandlerFailure(GameTestHelper helper, boolean keyboard) {
        requireOffscreen(helper);
        AtomicReference<FailingScreen> screen = new AtomicReference<>();
        AtomicReference<ClientWindow> window = new AtomicReference<>();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(100)
            .client("open throwing Offscreen fixture", c -> {
                screen.set(new FailingScreen());
                Object opened = open(screen::get);
                c.afterTest(() -> closeIfOpen(opened));
                window.set(c.offscreenWindow((Integer) invoke(opened, "getId")));
            })
            .async(
                "dispatch failing handler",
                c -> keyboard ? window.get()
                    .key(Keyboard.KEY_A, 'a')
                    : window.get()
                        .click(
                            0,
                            "throwing button",
                            w -> screen.get()
                                .target()))
            .client(
                "state changed before exception",
                c -> { if (!screen.get().changed) throw new AssertionError("Handler did not run"); })
            .succeed();
    }

    private static void requireOffscreen(GameTestHelper helper) {
        helper.assumeTrue(
            Arrays.stream(
                System.getProperty("horizonqa.tests", "")
                    .split(","))
                .map(String::trim)
                .anyMatch(helper.getTestId()::equals),
            "Select this Offscreen integration test by its exact ID in an SDL client profile");
        if (!Loader.isModLoaded("offscreen")) {
            throw new AssertionError("This selected test requires Offscreen 0.0.4 and its SDL lwjgl3ify runtime");
        }
    }

    private static final class FailingScreen extends GuiScreen {

        private boolean changed;

        private ClickTarget target() {
            return new ClickTarget(this, new Rectangle(0, 0, width, height), () -> true);
        }

        @Override
        protected void mouseClicked(int x, int y, int button) {
            changed = true;
            throw new IllegalStateException("Intentional Offscreen click failure after mutation");
        }

        @Override
        protected void keyTyped(char character, int key) {
            changed = true;
            throw new IllegalStateException("Intentional Offscreen key failure after mutation");
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }

    private static Object open(Supplier<GuiScreen> screen) {
        try {
            return Class.forName("com.sbancuz.offscreen.api.OffscreenAPI")
                .getMethod("open", Supplier.class)
                .invoke(null, screen);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Offscreen could not open the fixture", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unsupported Offscreen API", e);
        }
    }

    private static Object invoke(Object target, String method) {
        try {
            return target.getClass()
                .getMethod(method)
                .invoke(target);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Offscreen " + method + " failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unsupported Offscreen window API: " + method, e);
        }
    }

    private static void closeIfOpen(Object window) {
        if (!(Boolean) invoke(window, "isOpen")) return;
        try {
            Class.forName("com.sbancuz.offscreen.api.OffscreenAPI")
                .getMethod("close", Class.forName("com.sbancuz.offscreen.window.Window"))
                .invoke(null, window);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Offscreen fixture cleanup failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unsupported Offscreen close API", e);
        }
    }

    private static final class CounterScreen extends GuiScreen {

        private final String title;
        private GuiButton increment;
        private boolean hovered;
        private boolean rejectShift;
        private boolean shiftAtPress;
        private boolean shiftAtRelease;
        private boolean failShiftPress;
        private int clicks;
        private boolean pressed;
        private int heldFrames;
        private Point releasedAt;

        private CounterScreen(String title) {
            this.title = title;
        }

        @Override
        public void initGui() {
            buttonList.clear();
            increment = new GuiButton(0, width / 2 - 60, height / 2, 120, 20, "Increment");
            buttonList.add(increment);
        }

        private ClickTarget target() {
            if (increment == null) return null;
            return new ClickTarget(
                "increment",
                new Rectangle(increment.xPosition, increment.yPosition, increment.width, increment.height),
                () -> hovered);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            if (rejectShift && GuiScreen.isShiftKeyDown()) throw new AssertionError("Shift belongs to another window");
            if (Mouse.isButtonDown(0) != pressed)
                throw new AssertionError("Polled button state belongs to another window: " + title);
            if (pressed) heldFrames++;
            drawDefaultBackground();
            drawCenteredString(fontRendererObj, title + ": " + clicks, width / 2, height / 2 - 25, 0xFFFFFF);
            hovered = increment.mousePressed(mc, mouseX, mouseY);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        protected void mouseClicked(int x, int y, int button) {
            shiftAtPress = GuiScreen.isShiftKeyDown() && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
            if (failShiftPress) throw new IllegalStateException("Intentional unfocused Shift callback failure");
            if (button == 0) {
                if (!Mouse.isButtonDown(0)) throw new AssertionError("Hosted press is absent from polling");
                pressed = true;
            }
            super.mouseClicked(x, y, button);
        }

        @Override
        protected void mouseMovedOrUp(int x, int y, int button) {
            shiftAtRelease = GuiScreen.isShiftKeyDown() && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
            if (button == 0) {
                if (Mouse.isButtonDown(0)) throw new AssertionError("Hosted release is absent from polling");
                pressed = false;
                releasedAt = new Point(x, y);
            }
            super.mouseMovedOrUp(x, y, button);
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (button == increment) clicks++;
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }
}
