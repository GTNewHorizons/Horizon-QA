package com.gtnewhorizons.horizonqa.api.client;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;
import com.gtnewhorizons.horizonqa.client.ClientTaskQueue;
import com.gtnewhorizons.horizonqa.client.ClientTaskQueue.Phase;
import com.gtnewhorizons.horizonqa.client.FrameCapture;
import com.gtnewhorizons.horizonqa.client.LwjglWindow;
import com.gtnewhorizons.horizonqa.client.NativeInput;
import com.gtnewhorizons.horizonqa.internal.FatalErrors;

/**
 * A single test's client lifetime. Attach on the server test thread, then await every operation with
 * {@code thenExecuteAsync} or {@code thenWaitUntilAsync}, or author through {@link #scenario}.
 * Callbacks run on the client thread;
 * server assertions belong in ordinary sequence steps. No future may be awaited with a blocking get/join.
 */
public final class ClientTest {

    @SuppressWarnings("java:S3077")
    private static volatile ClientTest active;
    private final ClientTaskQueue operations = new ClientTaskQueue();
    private final List<Runnable> cleanup = new ArrayList<>();
    private final List<String> diagnostics = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Void> teardown = new CompletableFuture<>();
    private final FrameCapture frames;
    private volatile boolean closed;
    @SuppressWarnings("java:S3077")
    private volatile Throwable crash;
    private Thread clientThread;
    private NativeInput input;
    private GuiScreen originalScreen;
    private boolean initialized;
    private boolean cleanupStarted;
    private boolean restored;
    private long renderedFrames;
    private long releasedFrame;
    private long restoredFrame;
    private CompletableFuture<File> finalCapture;
    private Throwable cleanupError;
    private Error fatalCleanupError;
    private String waitingFor = "";
    private int originalWindowWidth;
    private int originalWindowHeight;

    private ClientTest(GameTestHelper helper) {
        File artifacts = new File(
            HorizonQAProperties.junitReportFile()
                .getParentFile(),
            "artifacts/" + helper.getTestId()
                .replaceAll("[^a-zA-Z0-9._-]", "_"));
        frames = new FrameCapture(artifacts);
        helper.recordDiagnostic("clientArtifacts=" + artifacts.getAbsolutePath());
    }

    /**
     * Attaches this test's sole session and registers bounded client cleanup before server cleanup.
     * The reported client runner serializes tests. A failed cleanup aborts subsequent cases.
     */
    public static synchronized ClientTest attach(GameTestHelper helper) {
        if (!HorizonQAProperties.clientTestsEnabled()) throw new IllegalStateException("Client scenarios are disabled");
        if (active != null) throw new IllegalStateException("Another test still owns the client");
        ClientTest session = new ClientTest(helper);
        helper.afterTestAsync(
            100,
            () -> session.close()
                .thenApply(ignored -> null));
        helper.afterTest(() -> session.diagnostics.forEach(helper::recordDiagnostic));
        active = session;
        return session;
    }

    /**
     * Attaches one client lifetime and starts the test's sole existing sequence for fluent authoring.
     * Call on the server test thread before execution; do not also call {@code helper.startSequence()}.
     * Finish the returned chain with {@link ClientScenario#succeed()}.
     */
    public static ClientScenario scenario(GameTestHelper helper) {
        ClientTest session = attach(helper);
        return new ClientScenario(helper.startSequence(), session);
    }

    private synchronized CompletableFuture<Void> close() {
        operations.close();
        closed = true;
        return teardown;
    }

    /** Queues a client END callback. Register restoration before mutating client state. */
    public CompletableFuture<Void> run(Consumer<ClientTest> action) {
        return operations.submit(Phase.END, () -> {
            action.accept(this);
            return null;
        });
    }

    /** Returns the live screen from inside a client callback. */
    public GuiScreen screen() {
        requireClientThread();
        return Minecraft.getMinecraft().currentScreen;
    }

    /** Asserts the screen type. An asynchronous assertion wait retries a mismatch within its tick budget. */
    public <T extends GuiScreen> T screen(Class<T> type) {
        GuiScreen screen = screen();
        if (!type.isInstance(screen)) throw new AssertionError(
            "Expected " + type.getName()
                + ", observed "
                + (screen == null ? "<no screen>"
                    : screen.getClass()
                        .getName()));
        return type.cast(screen);
    }

    /** Registers client cleanup in registration order; call inside {@link #run} before mutation. */
    public void afterTest(Runnable restoration) {
        requireClientThread();
        cleanup.add(restoration);
    }

    /**
     * Clicks a live target through native OS input and the normal game loop. Buttons are 0 left, 1 right,
     * 2 middle. Return null while unavailable; throw on ambiguity. Bounds, screen and actual pointer
     * readiness are resolved again after rendering before press. Completion includes release; separately
     * await the resulting client or authoritative gameplay state. Press/release are queued together so
     * rendering load cannot turn a click into a long press. An already-held button is rejected.
     * The enclosing sequence bounds all waiting.
     */
    public CompletableFuture<Void> click(int button, String description, Function<ClientTest, ClickTarget> resolver) {
        return clicks(button, 1, description, resolver);
    }

    /**
     * Sends two native left clicks at one resolved position without intervening game frames.
     * Normal input handling consumes both press/release pairs; no GUI handlers are invoked directly.
     * Rejects an already-held left button and observes rendered processing and release before completing.
     * Await the resulting client or gameplay state separately; the enclosing sequence bounds all waiting.
     */
    public CompletableFuture<Void> doubleClick(String description, Function<ClientTest, ClickTarget> resolver) {
        return clicks(0, 2, description, resolver);
    }

    private CompletableFuture<Void> clicks(int button, int count, String description,
        Function<ClientTest, ClickTarget> resolver) {
        if (button < 0 || button > 2) throw new IllegalArgumentException("Mouse button must be 0, 1, or 2");
        TargetPosition target = new TargetPosition(description, resolver);
        return operations.submitWhenReady(Phase.END, () -> {
            if (Mouse.isButtonDown(button)) throw new IllegalStateException("Click requires a released mouse button");
            return target.ready();
        }, () -> {
            for (int click = 0; click < count; click++) {
                nativeInput().mouse(button, true);
                nativeInput().mouse(button, false);
            }
            return renderedFrames;
        })
            .thenCompose(
                frame -> operations
                    .submitWhenReady(Phase.END, () -> renderedAfterPress(frame) && !Mouse.isButtonDown(button), () -> {
                        waitingFor = "";
                        return null;
                    }));
    }

    /**
     * Scrolls a live target through OS input and waits for the normal game loop to process it.
     * Positive notches scroll down; negative notches scroll up. Accepts nonzero values from -100 to 100.
     * Assert the resulting scroll/zoom state separately; the enclosing sequence bounds all waiting.
     */
    public CompletableFuture<Void> scroll(int notches, String description, Function<ClientTest, ClickTarget> resolver) {
        if (notches == 0 || notches < -100 || notches > 100)
            throw new IllegalArgumentException("Wheel notches must be -100..100 and nonzero");
        TargetPosition target = new TargetPosition(description, resolver);
        return operations.submitWhenReady(Phase.END, target::ready, () -> {
            nativeInput().scroll(notches);
            return renderedFrames;
        })
            .thenCompose(frame -> operations.submitWhenReady(Phase.END, () -> renderedAfterPress(frame), () -> {
                waitingFor = "";
                return null;
            }));
    }

    /**
     * Drags a live target with the native left button over a positive number of rendered movement frames.
     * Resolve the endpoint on the client thread immediately before pressing, in scaled GUI pixels.
     * Rejects already-held input and screen/size changes during the gesture. Completion observes release;
     * failures and cancellation retain bounded session cleanup. Await resulting state separately.
     */
    public CompletableFuture<Void> drag(String description, Function<ClientTest, ClickTarget> resolver,
        Function<ClientTest, Point> endpoint, int movementFrames) {
        if (movementFrames <= 0) throw new IllegalArgumentException("Movement frames must be positive");
        DragGesture gesture = new DragGesture(description, resolver, endpoint, movementFrames);
        return operations.submitWhenReady(Phase.END, gesture::ready, () -> null);
    }

    /** Queues a native window resize and awaits normal Minecraft resize handling and a completed frame. */
    public CompletableFuture<Void> resizeWindow(int width, int height) {
        validateWindowSize(width, height);
        return operations.submit(Phase.END, () -> {
            requestWindowResize(width, height);
            return renderedFrames;
        })
            .thenCompose(frame -> operations.submitWhenReady(Phase.END, () -> {
                Minecraft mc = Minecraft.getMinecraft();
                waitingFor = "window " + width
                    + "x"
                    + height
                    + ": framebuffer="
                    + mc.displayWidth
                    + "x"
                    + mc.displayHeight;
                return renderedFrames > frame + 1 && mc.displayWidth == width && mc.displayHeight == height;
            }, () -> {
                waitingFor = "";
                return null;
            }));
    }

    /**
     * Requests native resizing inside a client callback, without blocking or invoking Minecraft's resize handler.
     * The normal loop must observe the new framebuffer dimensions; await them separately.
     * The first request registers restoration of the original window size before mutation.
     */
    public void requestWindowResize(int width, int height) {
        requireClientThread();
        validateWindowSize(width, height);
        if (!Display.isCreated() || Display.isFullscreen() || Display.getParent() != null || !Display.isResizable())
            throw new GameTestInfrastructureException("CLIENT_WINDOW", "Resize requires a resizable windowed display");
        if (originalWindowWidth == 0) {
            originalWindowWidth = Display.getWidth();
            originalWindowHeight = Display.getHeight();
            afterTest(() -> LwjglWindow.resize(originalWindowWidth, originalWindowHeight));
        }
        LwjglWindow.resize(width, height);
    }

    private static void validateWindowSize(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Window dimensions must be positive");
    }

    /**
     * Owns Shift around one normal native left-click. An already-held modifier is rejected.
     * Completion includes observed modifier release; errors also schedule release, while cancellation
     * and timeout retain the session's exclusive bounded teardown. Await through a sequence step.
     */
    public CompletableFuture<Void> shiftClick(String description, Function<ClientTest, ClickTarget> resolver) {
        return operations.submit(Phase.END, () -> {
            if (!nativeInput().keyReleased(KeyEvent.VK_SHIFT)) throw new IllegalStateException("Shift is already held");
            nativeInput().key(KeyEvent.VK_SHIFT, true);
            return renderedFrames;
        })
            .thenCompose(frame -> {
                CompletableFuture<Void> gesture = operations
                    .submitWhenReady(
                        Phase.END,
                        () -> renderedAfterPress(frame) && !nativeInput().keyReleased(KeyEvent.VK_SHIFT),
                        () -> null)
                    .thenCompose(ignored -> click(0, description, resolver));
                return gesture.handle((ignored, error) -> operations.submit(Phase.END, () -> {
                    nativeInput().key(KeyEvent.VK_SHIFT, false);
                    return renderedFrames;
                })
                    .thenCompose(
                        released -> awaitReleasedFrame(released, () -> nativeInput().keyReleased(KeyEvent.VK_SHIFT)))
                    .handle((released, releaseError) -> {
                        if (releaseError != null) {
                            if (error != null && error != releaseError) releaseError.addSuppressed(error);
                            throw new CompletionException(releaseError);
                        }
                        if (error != null) throw new CompletionException(error);
                        return (Void) null;
                    }))
                    .thenCompose(release -> release);
            });
    }

    /** Sends one native key press/release using an AWT {@code KeyEvent.VK_*} code, through normal input. */
    public CompletableFuture<Void> key(int awtKeyCode) {
        return operations.submit(Phase.END, () -> {
            nativeInput().key(awtKeyCode, true);
            return renderedFrames;
        })
            .thenCompose(frame -> operations.submitWhenReady(Phase.END, () -> renderedAfterPress(frame), () -> {
                nativeInput().key(awtKeyCode, false);
                return renderedFrames;
            }))
            .thenCompose(frame -> awaitReleasedFrame(frame, () -> nativeInput().keyReleased(awtKeyCode)));
    }

    /** Holds a native key until explicitly released or teardown; call inside an awaited client callback. */
    public void holdKey(int awtKeyCode) {
        nativeInput().key(awtKeyCode, true);
    }

    /** Releases a key held by this session; call inside an awaited client callback. */
    public void releaseKey(int awtKeyCode) {
        nativeInput().key(awtKeyCode, false);
    }

    /**
     * Presses/releases the configured use-item binding with no active GUI. Minecraft performs the actual
     * item/block interaction. Setup may equip and aim the player; it must not invoke the item handler.
     * An unsupported native key binding is an infrastructure error. Await resulting gameplay state separately.
     */
    public CompletableFuture<Void> useItem() {
        return operations.submit(Phase.END, () -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen != null || mc.thePlayer == null)
                throw new AssertionError("Expected a player with no GUI");
            int key = mc.gameSettings.keyBindUseItem.getKeyCode();
            nativeInput().useBinding(key, true);
            return new long[] { key, renderedFrames };
        })
            .thenCompose(press -> operations.submitWhenReady(Phase.END, () -> renderedAfterPress(press[1]), () -> {
                nativeInput().useBinding((int) press[0], false);
                return renderedFrames;
            }))
            .thenCompose(
                frame -> awaitReleasedFrame(
                    frame,
                    () -> nativeInput()
                        .bindingReleased(Minecraft.getMinecraft().gameSettings.keyBindUseItem.getKeyCode())));
    }

    private boolean renderedAfterPress(long frame) {
        waitingFor = "pressed input: renderedFrame=" + renderedFrames + " after=" + (frame + 1);
        return renderedFrames > frame + 1;
    }

    private CompletableFuture<Void> awaitReleasedFrame(long frame, java.util.function.BooleanSupplier released) {
        return operations.submitWhenReady(Phase.END, () -> {
            waitingFor = "input release: frame=" + renderedFrames
                + " after="
                + frame
                + " released="
                + released.getAsBoolean();
            return renderedFrames > frame && released.getAsBoolean();
        }, () -> {
            waitingFor = "";
            return null;
        });
    }

    /** Captures an ordinary completed framebuffer and encodes its PNG off the game thread. */
    public CompletableFuture<File> capture(String name) {
        if (!name.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) throw new IllegalArgumentException("Invalid capture name");
        return operations.submit(Phase.FRAME, () -> captureFrame(name))
            .thenCompose(result -> result);
    }

    private CompletableFuture<File> captureFrame(String name) {
        return frames.capture(name)
            .thenApply(file -> {
                diagnostics.add("screenshot=" + file.getAbsolutePath());
                return file;
            });
    }

    private NativeInput nativeInput() {
        requireClientThread();
        if (!Display.isActive()) throw new GameTestInfrastructureException(
            "CLIENT_FOCUS",
            "Client display has no input focus; window=" + Display.getX()
                + ","
                + Display.getY()
                + " "
                + Display.getWidth()
                + "x"
                + Display.getHeight()
                + "; renderedFrames="
                + renderedFrames);
        if (input == null) input = new NativeInput();
        return input;
    }

    private void requireClientThread() {
        if (Thread.currentThread() != clientThread) throw new IllegalStateException("Use an awaited client callback");
    }

    /** Internal tick/frame entry; only the physical client's runtime calls this. */
    public static void tick(Phase phase) {
        ClientTest session = active;
        if (session == null) return;
        session.clientThread = Thread.currentThread();
        if (!session.initialized) {
            session.originalScreen = Minecraft.getMinecraft().currentScreen;
            session.initialized = true;
        }
        if (phase == Phase.FRAME) session.renderedFrames++;
        if (session.closed) session.pollCleanup(phase);
        else session.operations.dispatch(phase);
    }

    /** Invalidates work after a real client-loop crash; the runtime aborts the reported run as infrastructure. */
    public static void crashed(Throwable error) {
        ClientTest session = active;
        if (session == null) return;
        session.crash = error;
        session.close();
    }

    /** Internal shutdown entry. The caller must impose a separate wall-clock shutdown bound. */
    public static CompletableFuture<Void> closeActive() {
        ClientTest session = active;
        return session == null ? CompletableFuture.completedFuture(null) : session.close();
    }

    /** Final client-thread fallback after the runtime's wall-clock cleanup deadline. */
    public static void interruptCleanup() {
        ClientTest session = active;
        if (session == null) return;
        session.requireClientThread();
        if (session.input != null) session.attempt(session.input::release);
        session.restore();
        session.attempt(
            () -> { throw new GameTestInfrastructureException("CLIENT_SHUTDOWN", "Client teardown was interrupted"); });
        session.completeTeardown();
    }

    private void pollCleanup(Phase phase) {
        if (!cleanupStarted && phase == Phase.END) {
            cleanupStarted = true;
            diagnostics.add(
                "lastScreen=" + (screen() == null ? "<none>"
                    : screen().getClass()
                        .getName()));
            if (!waitingFor.isEmpty()) diagnostics.add("waitingFor=" + waitingFor);
            if (input != null) attempt(input::release);
            releasedFrame = renderedFrames;
            return;
        }
        if (!cleanupStarted) return;
        if (finalCapture == null && phase == Phase.FRAME) {
            try {
                finalCapture = crash == null ? captureFrame("final") : CompletableFuture.completedFuture(null);
            } catch (RuntimeException | Error error) {
                finalCapture = new CompletableFuture<>();
                finalCapture.completeExceptionally(error);
                FatalErrors.rethrow(error);
            }
        }
        if (phase != Phase.END || finalCapture == null
            || renderedFrames <= releasedFrame + 1
            || (input != null && !NativeInput.released())) return;
        restore();
        if (!finalCapture.isDone() || renderedFrames <= restoredFrame) return;
        if (originalWindowWidth > 0
            && (Display.getWidth() != originalWindowWidth || Display.getHeight() != originalWindowHeight
                || Minecraft.getMinecraft().displayWidth != originalWindowWidth
                || Minecraft.getMinecraft().displayHeight != originalWindowHeight))
            return;
        attempt(() -> finalCapture.join());
        completeTeardown();
    }

    private void restore() {
        if (restored) return;
        restored = true;
        restoredFrame = renderedFrames;
        for (Runnable restoration : cleanup) attempt(restoration);
        attempt(
            () -> Minecraft.getMinecraft()
                .displayGuiScreen(originalScreen));
        if (input != null && originalScreen != null) attempt(input::restorePointer);
    }

    private void completeTeardown() {
        if (crash != null) {
            GameTestInfrastructureException error = new GameTestInfrastructureException(
                "CLIENT_CRASH",
                "Client loop crashed during test lifetime");
            error.initCause(crash);
            if (cleanupError != null) error.addSuppressed(cleanupError);
            cleanupError = error;
        }
        active = null;
        if (cleanupError != null) teardown.completeExceptionally(cleanupError);
        else teardown.complete(null);
        FatalErrors.rethrow(fatalCleanupError);
    }

    private void attempt(Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            if (FatalErrors.isFatal(error) && fatalCleanupError == null) fatalCleanupError = (Error) error;
            if (cleanupError == null) cleanupError = error;
            else if (cleanupError != error) cleanupError.addSuppressed(error);
        }
    }

    private final class DragGesture {

        private final TargetPosition target;
        private final Function<ClientTest, Point> endpoint;
        private final int movementFrames;
        private GuiScreen screen;
        private int width;
        private int height;
        private Point from;
        private Point to;
        private long frame;
        private int movement;
        private int stage;
        private boolean pressed;

        DragGesture(String description, Function<ClientTest, ClickTarget> resolver,
            Function<ClientTest, Point> endpoint, int movementFrames) {
            target = new TargetPosition(description, resolver);
            this.endpoint = java.util.Objects.requireNonNull(endpoint, "endpoint");
            this.movementFrames = movementFrames;
        }

        boolean ready() {
            try {
                return advance();
            } catch (RuntimeException | Error error) {
                if (pressed && input != null) {
                    try {
                        input.mouse(0, false);
                    } catch (RuntimeException | Error releaseError) {
                        error.addSuppressed(releaseError);
                        FatalErrors.rethrow(releaseError);
                    }
                }
                pressed = false;
                throw error;
            }
        }

        private boolean advance() {
            if (stage == 0) {
                if (!target.ready()) return false;
                screen = screen();
                width = screen.width;
                height = screen.height;
                from = new Point(target.positioned);
                to = new Point(java.util.Objects.requireNonNull(endpoint.apply(ClientTest.this), "endpoint"));
                if (!new Rectangle(0, 0, width, height).contains(to))
                    throw new IllegalArgumentException("Drag endpoint must be inside the live screen");
                if (Mouse.isButtonDown(0)) throw new IllegalStateException("Left mouse button is already held");
                pressed = true;
                nativeInput().mouse(0, true);
                stage = 1;
                frame = renderedFrames;
                return false;
            }
            if (screen() != screen || screen.width != width || screen.height != height)
                throw new AssertionError("Screen changed during native drag");
            waitingFor = "drag " + target.description
                + ": movement="
                + movement
                + "/"
                + movementFrames
                + " stage="
                + stage
                + " renderedFrame="
                + renderedFrames
                + " after="
                + frame;
            if (stage == 1 && !renderedAfterPress(frame)) return false;
            if (stage == 2 && renderedFrames <= frame) return false;
            if (stage == 3) {
                if (renderedFrames <= frame || Mouse.isButtonDown(0)) return false;
                waitingFor = "";
                return true;
            }
            if (movement < movementFrames) {
                movement++;
                int x = from.x + (int) ((long) (to.x - from.x) * movement / movementFrames);
                int y = from.y + (int) ((long) (to.y - from.y) * movement / movementFrames);
                nativeInput().move(screen, x, y);
                frame = renderedFrames;
                stage = 2;
                return false;
            }
            nativeInput().mouse(0, false);
            pressed = false;
            frame = renderedFrames;
            stage = 3;
            return false;
        }
    }

    private final class TargetPosition {

        private final String description;
        private final Function<ClientTest, ClickTarget> resolver;
        private GuiScreen positionedScreen;
        private Object identity;
        private Point positioned;
        private long frame;

        TargetPosition(String description, Function<ClientTest, ClickTarget> resolver) {
            this.description = description;
            this.resolver = resolver;
        }

        boolean ready() {
            waitingFor = description;
            GuiScreen screen = screen();
            ClickTarget target = resolver.apply(ClientTest.this);
            if (screen == null || target == null) return false;
            Rectangle visible = target.bounds.intersection(new Rectangle(0, 0, screen.width, screen.height));
            if (visible.isEmpty()) return false;
            int pointerX = Mouse.getX() * screen.width / Minecraft.getMinecraft().displayWidth;
            int pointerY = screen.height - Mouse.getY() * screen.height / Minecraft.getMinecraft().displayHeight - 1;
            waitingFor = description + ": pointer=" + pointerX + "," + pointerY + " bounds=" + visible;
            if (positionedScreen != screen || identity != target.identity
                || positioned == null
                || !visible.contains(positioned)) {
                positionedScreen = screen;
                identity = target.identity;
                positioned = new Point(visible.x + visible.width / 2, visible.y + visible.height / 2);
                nativeInput().move(screen, positioned.x, positioned.y);
                frame = renderedFrames;
                return false;
            }
            if (renderedFrames <= frame + 1) return false;
            if (!visible.contains(pointerX, pointerY)) {
                nativeInput().correctPosition(screen, positioned.x, positioned.y);
                frame = renderedFrames;
                return false;
            }
            return target.ready.getAsBoolean();
        }
    }
}
