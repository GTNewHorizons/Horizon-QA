package com.gtnewhorizons.horizonqa.client;

import java.awt.Point;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Optional Offscreen 0.3 integration. Reflection keeps its classes out of the ordinary client/server load path. */
public final class OffscreenWindowDriver implements WindowDriver {

    private static final Map<Object, OffscreenWindowDriver> WINDOWS = new IdentityHashMap<>();
    private final Object window;
    private final int id;
    private final FrameCapture capture;
    private final List<String> diagnostics;
    private long frames;
    private Throwable failure;
    private final List<CompletableFuture<Void>> inputWaiters = new ArrayList<>();
    private int heldButton = -1;
    private boolean observing;
    private String checkpoint;
    private CompletableFuture<File> captureRequest;
    private CompletableFuture<Void> resizeRequest;
    private int requestedWidth;
    private int requestedHeight;

    public OffscreenWindowDriver(int id, FrameCapture capture, List<String> diagnostics) {
        if (!SdlInput.isAvailable())
            throw new IllegalStateException("Offscreen tests require the SDL lwjgl3ify runtime");
        this.window = invokeStatic(
            "com.sbancuz.offscreen.api.OffscreenAPI",
            "getWindow",
            new Class<?>[] { int.class },
            id);
        if (window == null) throw new IllegalArgumentException("No Offscreen window with ID " + id);
        this.id = id;
        this.capture = capture;
        this.diagnostics = diagnostics;
        WINDOWS.put(window, this);
    }

    public String description() {
        return "Offscreen window " + id;
    }

    public boolean isOpen() {
        return (Boolean) invoke(window, "isOpen");
    }

    public GuiScreen screen() {
        requireOpen();
        Object ui = invoke(window, "getCurrentUI");
        return ui == null ? null : (GuiScreen) invoke(ui, "getGuiScreen");
    }

    public long renderedFrames() {
        requireOpen();
        return frames;
    }

    public <T> T observe(Supplier<T> observation) {
        requireOpen();
        if (observing) return observation.get();
        return SdlInput.withMouseState(id, () -> {
            Object ui = invoke(window, "getCurrentUI");
            if (ui == null) return observation.get();
            Object scope = invoke(ui, "scope");
            invoke(scope, "enter");
            observing = true;
            try {
                return observation.get();
            } finally {
                observing = false;
                invoke(scope, "restore");
            }
        });
    }

    private Point coordinates(GuiScreen screen, int x, int y) {
        requireOpen();
        Point size = SdlInput.logicalSize(id);
        return new Point(x * size.x / screen.width, y * size.y / screen.height);
    }

    public void move(GuiScreen screen, int x, int y) {
        Point p = coordinates(screen, x, y);
        SdlInput.move(id, p.x, p.y);
    }

    public void mouse(GuiScreen screen, int x, int y, int button, boolean down) {
        Point p = coordinates(screen, x, y);
        SdlInput.mouse(id, p.x, p.y, button, down);
    }

    public void scroll(GuiScreen screen, int x, int y, int delta) {
        Point p = coordinates(screen, x, y);
        SdlInput.scroll(id, p.x, p.y, delta);
    }

    public void key(GuiScreen screen, int key, char character, boolean down) {
        requireOpen();
        SdlInput.key(id, key, character, down);
    }

    public void shift(GuiScreen screen, boolean down) {
        if (down) SdlInput.requireInputFocus(id);
        key(screen, Keyboard.KEY_LSHIFT, '\0', down);
    }

    public void beginDrag(GuiScreen screen, int x, int y, int button) {
        if (heldButton >= 0) throw new IllegalStateException("A drag is already active in " + description());
        heldButton = button;
        mouse(screen, x, y, button, true);
    }

    public void dragMove(GuiScreen screen, int x, int y) {
        move(screen, x, y);
    }

    public boolean isDragging() {
        return heldButton >= 0 && isOpen();
    }

    public void release() {
        heldButton = -1;
        SdlInput.release(id);
    }

    public CompletableFuture<Void> inputConsumed() {
        requireOpen();
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (failure != null) result.completeExceptionally(failure);
        else inputWaiters.add(result);
        return result;
    }

    public CompletableFuture<Void> resize(ClientTest owner, int width, int height) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        return owner.run(c -> {
            requireOpen();
            if (resizeRequest != null) throw new IllegalStateException("Resize already pending in " + description());
            requestedWidth = width;
            requestedHeight = height;
            resizeRequest = result;
            invoke(window, "resize", new Class<?>[] { int.class, int.class }, width, height);
        })
            .thenCompose(ignored -> result);
    }

    public CompletableFuture<File> capture(ClientTest owner, String name) {
        FrameCapture.validateCheckpoint(name);
        CompletableFuture<File> result = new CompletableFuture<>();
        return owner.run(c -> {
            requireOpen();
            if (captureRequest != null) throw new IllegalStateException("Capture already pending in " + description());
            checkpoint = name;
            captureRequest = result;
        })
            .thenCompose(ignored -> result);
    }

    public void close() {
        release();
        if (isOpen()) invoke(window, "destroy");
        closed();
    }

    /** Scopes polling to a bound test window during the host's real update and render lifecycle. */
    public static void withInput(Object window, Runnable action) {
        OffscreenWindowDriver driver = WINDOWS.get(window);
        if (driver == null) action.run();
        else SdlInput.withMouseState(driver.id, () -> {
            action.run();
            return null;
        });
    }

    /** Called while the completed Offscreen framebuffer is still bound on the render thread. */
    public static void completedFrame(Object window, int width, int height) {
        OffscreenWindowDriver driver = WINDOWS.get(window);
        if (driver == null || driver.failure != null) return;
        driver.frames++;
        SdlInput.frameConsumed(driver.id);
        List<CompletableFuture<Void>> consumed = new ArrayList<>(driver.inputWaiters);
        driver.inputWaiters.clear();
        consumed.forEach(waiter -> waiter.complete(null));
        if (driver.resizeRequest != null) {
            Point size = SdlInput.logicalSize(driver.id);
            if (size.x == driver.requestedWidth && size.y == driver.requestedHeight) {
                CompletableFuture<Void> request = driver.resizeRequest;
                driver.resizeRequest = null;
                request.complete(null);
            }
        }
        if (driver.captureRequest != null) {
            CompletableFuture<File> request = driver.captureRequest;
            String checkpoint = driver.checkpoint;
            driver.captureRequest = null;
            driver.capture.capture(checkpoint, width, height)
                .whenComplete((file, error) -> {
                    if (error != null) request.completeExceptionally(error);
                    else {
                        driver.diagnostics.add("artifact=" + file.getAbsolutePath());
                        request.complete(file);
                    }
                });
        }
    }

    /** Offscreen logs scoped failures instead of rethrowing them. Preserve them as test failures. */
    public static void renderingFailed(Throwable error) {
        if (!ClientTest.failActive(error)) return;
        for (OffscreenWindowDriver driver : new ArrayList<>(WINDOWS.values())) {
            driver.failure = error;
            List<CompletableFuture<Void>> pending = new ArrayList<>(driver.inputWaiters);
            driver.inputWaiters.clear();
            pending.forEach(waiter -> waiter.completeExceptionally(error));
            if (driver.captureRequest != null) driver.captureRequest.completeExceptionally(error);
            if (driver.resizeRequest != null) driver.resizeRequest.completeExceptionally(error);
        }
    }

    /** Rejects pending work when the exact hosted window is destroyed. */
    public static void windowClosed(Object window) {
        OffscreenWindowDriver driver = WINDOWS.get(window);
        if (driver != null) driver.closed();
    }

    public static void releaseAll() {
        List<OffscreenWindowDriver> windows = new ArrayList<>(WINDOWS.values());
        Throwable failure = null;
        for (OffscreenWindowDriver driver : windows) {
            try {
                driver.close();
            } catch (RuntimeException | Error e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        WINDOWS.clear();
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
    }

    private void closed() {
        IllegalStateException error = new IllegalStateException(description() + " closed");
        if (resizeRequest != null) resizeRequest.completeExceptionally(error);
        if (captureRequest != null) captureRequest.completeExceptionally(error);
        inputWaiters.forEach(waiter -> waiter.completeExceptionally(error));
        inputWaiters.clear();
        WINDOWS.remove(window);
        SdlInput.frameConsumed(id);
    }

    private void requireOpen() {
        if (!isOpen()) throw new IllegalStateException(description() + " closed");
    }

    private static Object invoke(Object target, String method) {
        return invoke(target, method, new Class<?>[0]);
    }

    private static Object invoke(Object target, String method, Class<?>[] parameters, Object... arguments) {
        try {
            return target.getClass()
                .getMethod(method, parameters)
                .invoke(target, arguments);
        } catch (ReflectiveOperationException e) {
            throw unsupported(e);
        }
    }

    private static Object invokeStatic(String type, String method, Class<?>[] parameters, Object... arguments) {
        try {
            return Class.forName(type)
                .getMethod(method, parameters)
                .invoke(null, arguments);
        } catch (ReflectiveOperationException e) {
            throw unsupported(e);
        }
    }

    private static RuntimeException unsupported(ReflectiveOperationException error) {
        Throwable cause = error instanceof InvocationTargetException ? error.getCause() : error;
        if (cause instanceof RuntimeException) return (RuntimeException) cause;
        if (cause instanceof Error) throw (Error) cause;
        return new IllegalStateException("Unsupported Offscreen 0.3 runtime", cause);
    }
}
