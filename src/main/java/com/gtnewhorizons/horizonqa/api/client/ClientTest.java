package com.gtnewhorizons.horizonqa.api.client;

import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.opengl.Display;

import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;
import com.gtnewhorizons.horizonqa.client.ClientTaskQueue;
import com.gtnewhorizons.horizonqa.client.ClientTaskQueue.Phase;
import com.gtnewhorizons.horizonqa.client.ClientTestRuntime;
import com.gtnewhorizons.horizonqa.client.FrameCapture;
import com.gtnewhorizons.horizonqa.client.LwjglInput;
import com.gtnewhorizons.horizonqa.client.LwjglWindow;
import com.gtnewhorizons.horizonqa.internal.FatalErrors;

/** A test-owned client session. Attach on the server test thread, then await every client operation */
public final class ClientTest {

    // Publishes the session. Client state stays on the client thread, queued work is synchronized,
    // and cross-thread diagnostics and completion use concurrent collections and futures
    @SuppressWarnings("java:S3077")
    private static volatile ClientTest active;
    private final ClientTaskQueue operations = new ClientTaskQueue();
    private final List<Runnable> cleanup = new ArrayList<>();
    private final List<String> diagnostics = new CopyOnWriteArrayList<>();
    private final FrameCapture frames;
    private final CompletableFuture<Void> teardown = new CompletableFuture<>();
    private volatile boolean closed;
    // Publishes the original client failure for the server tick to rethrow, not mutable test state
    @SuppressWarnings("java:S3077")
    private volatile Throwable failure;
    // Captured before closed publishes the cleanup boundary to the client thread
    private Throwable failureAtClose;
    private Thread clientThread;
    private String waitingForInput = "";
    private long renderedFrames;
    private final java.util.Map<Integer, ClientWindow> additionalWindows = new java.util.LinkedHashMap<>();
    private final ClientWindow mainWindow = new ClientWindow(
        this,
        operations,
        new com.gtnewhorizons.horizonqa.client.MainWindowDriver(() -> renderedFrames));
    private Point originalWindowSize;
    private WindowResize restoringWindow;
    private CompletableFuture<File> finalCapture;
    private Throwable cleanupError;
    private Error fatalCleanupError;

    ClientTest(GameTestHelper helper) {
        frames = new FrameCapture(
            new File(
                HorizonQAProperties.junitReportFile()
                    .getAbsoluteFile()
                    .getParentFile(),
                "artifacts/" + helper.getTestId()
                    .replaceAll("[^a-zA-Z0-9._-]", "_")));
    }

    /** Starts fluent authoring on this test's existing sequence and attaches its sole client session */
    public static ClientScenario scenario(GameTestHelper helper) {
        return new ClientScenario(helper.startSequence(), attach(helper));
    }

    /**
     * Attaches the sole client session to this test. Teardown invalidates queued work, captures the final
     * rendered frame, releases input, runs client cleanup, and closes the screen before server cleanup
     */
    public static synchronized ClientTest attach(GameTestHelper helper) {
        if (!HorizonQAProperties.clientTestsEnabled()) throw new IllegalStateException("Client test mode is disabled");
        if (active != null) throw new IllegalStateException("Another client test still owns the screen");
        ClientTest session = new ClientTest(helper);
        helper.afterTestAsync(100, session::close);
        helper.afterTest(() -> ClientTestRuntime.setMobsEnabled(false));
        helper.afterTest(() -> session.diagnostics.forEach(helper::recordDiagnostic));
        helper.onEachTick(
            "client lifecycle",
            () -> { if (session.failure != null) ClientTest.<RuntimeException>rethrow(session.failure); });
        active = session;
        return session;
    }

    private synchronized CompletableFuture<Void> close() {
        if (closed) return teardown;
        operations.close();
        failureAtClose = failure;
        closed = true;
        return teardown;
    }

    /** Internal orderly shutdown entry. Completion means client teardown has actually finished */
    public static CompletableFuture<Void> closeActive() {
        ClientTest session = active;
        return session == null ? CompletableFuture.completedFuture(null) : session.close();
    }

    /** Internal shutdown entry, ending teardown on the client thread without awaiting frames or capture completion */
    public static void interruptCleanup() {
        ClientTest session = active;
        if (session == null) return;
        session.requireClientThread();
        session.close();
        GameTestInfrastructureException failure = new GameTestInfrastructureException(
            "CLIENT_SHUTDOWN",
            "Client teardown was interrupted after its shutdown deadline");
        try {
            if (session.finalCapture == null) session.beginCleanup();
        } catch (Throwable error) {
            failure.addSuppressed(error);
            session.retainFatalCleanup(error);
        }
        if (session.cleanupError != null) failure.addSuppressed(session.cleanupError);
        if (session.finalCapture != null && !session.finalCapture.isDone()) session.finalCapture.cancel(false);
        session.completeTeardown(failure);
    }

    /** Queues an action on the next client END tick. The result completes after the action returns */
    public CompletableFuture<Void> run(Consumer<ClientTest> action) {
        return operations.submit(Phase.END, () -> {
            action.accept(this);
            return null;
        });
    }

    /**
     * Resizes the existing resizable, windowed Display through its native OS event path.
     * Completes after both LWJGL and Minecraft observe the requested positive pixel dimensions and a normal frame.
     * The first call records the original dimensions. Client teardown restores them through the same path and
     * waits for observation before the next test. Await this operation before submitting other client input
     */
    public CompletableFuture<Void> resizeWindow(int width, int height) {
        LwjglWindow.validateSize(width, height);
        WindowResize resize = new WindowResize(width, height);
        return operations.submit(Phase.END, () -> {
            resize.request();
            return null;
        })
            .thenCompose(ignored -> operations.submitWhenReady(Phase.FRAME, resize::ready, () -> {
                waitingForInput = "";
                return null;
            }));
    }

    /**
     * Requests native resize immediately from an active client-thread callback, without queueing or awaiting it.
     * Use this for external window events during an in-flight operation. Observe actual dimensions separately.
     * Target resolvers must remain observation-only. The session retains its original dimensions for teardown.
     * Ordinary scenarios should use {@link #resizeWindow(int, int)} for queued, awaited resizing
     */
    public void requestWindowResize(int width, int height) {
        LwjglWindow.validateSize(width, height);
        requireClientThread();
        if (closed) throw new IllegalStateException("Client test has ended");
        new WindowResize(width, height).request();
    }

    /**
     * Presses the configured use-item control for one normal client tick, then releases it and completes at END.
     * Requires a world, a player with an equipped item and no active GUI. The ordinary input loop handles the
     * mouse or keyboard event, including mod input hooks. Normal aiming and block/entity interactions still apply.
     * Completion means input was processed and released, not that a mod accepted the use. Await a separate
     * observable assertion for the result. The enclosing sequence step bounds this operation
     */
    public CompletableFuture<Void> useHeldItem() {
        return operations.submit(Phase.START, () -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null || mc.thePlayer == null) {
                throw new IllegalStateException("Using a held item requires an active world and player");
            }
            if (mc.currentScreen != null) throw new IllegalStateException(
                "Using a held item requires no active screen, found " + mc.currentScreen.getClass()
                    .getName());
            if (mc.thePlayer.getHeldItem() == null) throw new IllegalStateException("No item is equipped");
            int key = mc.gameSettings.keyBindUseItem.getKeyCode();
            if (key == 0) throw new IllegalStateException("The use-item control is unbound");
            LwjglInput.end();
            LwjglInput.binding(key, true);
            return key;
        })
            .thenCompose(key -> operations.submit(Phase.START, () -> {
                LwjglInput.binding(key, false);
                return null;
            }))
            .thenCompose(ignored -> operations.submit(Phase.END, () -> {
                LwjglInput.release();
                return null;
            }));
    }

    /** Captures the completed display framebuffer before buffer swap and returns the PNG path */
    public CompletableFuture<File> capture(String checkpoint) {
        if (!checkpoint.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
            throw new IllegalArgumentException("Checkpoint must be a simple artifact name");
        }
        return operations.submit(Phase.FRAME, () -> captureFrame(checkpoint))
            .thenCompose(future -> future);
    }

    /** Returns the real active screen. Call inside {@link #run} */
    public GuiScreen screen() {
        requireClientThread();
        return Minecraft.getMinecraft().currentScreen;
    }

    /** Asserts and returns the current screen type. A mismatch is retryable in an asynchronous wait step */
    public <T extends GuiScreen> T screen(Class<T> type) {
        GuiScreen screen = screen();
        if (!type.isInstance(screen)) throw new AssertionError(
            "Expected " + type.getName()
                + ", last screen: "
                + (screen == null ? "<none>"
                    : screen.getClass()
                        .getName()));
        return type.cast(screen);
    }

    /** Returns the main game window owned by this test */
    public ClientWindow mainWindow() {
        return mainWindow;
    }

    /** Binds an existing Offscreen window by SDL ID. Call on the client thread after opening it */
    public ClientWindow offscreenWindow(int windowId) {
        requireClientThread();
        return additionalWindows.computeIfAbsent(
            windowId,
            id -> new ClientWindow(
                this,
                operations,
                new com.gtnewhorizons.horizonqa.client.OffscreenWindowDriver(id, frames, diagnostics)));
    }

    public CompletableFuture<Void> click(int button, Function<ClientTest, Point> target) {
        return mainWindow.click(button, w -> target.apply(this));
    }

    public CompletableFuture<Void> click(int button, Function<ClientTest, Point> target, Predicate<ClientTest> ready) {
        return mainWindow.click(button, w -> target.apply(this), w -> ready.test(this));
    }

    public CompletableFuture<Void> shiftClick(int button, Function<ClientTest, Point> target,
        Predicate<ClientTest> ready) {
        return mainWindow.shiftClick(button, w -> target.apply(this), w -> ready.test(this));
    }

    public CompletableFuture<Void> click(int button, String description, Function<ClientTest, ClickTarget> resolver) {
        return mainWindow.click(button, description, w -> resolver.apply(this));
    }

    public CompletableFuture<Void> shiftClick(int button, String description,
        Function<ClientTest, ClickTarget> resolver) {
        return mainWindow.shiftClick(button, description, w -> resolver.apply(this));
    }

    public CompletableFuture<Void> scroll(int delta, String description, Function<ClientTest, ClickTarget> resolver) {
        return mainWindow.scroll(delta, description, w -> resolver.apply(this));
    }

    public CompletableFuture<Void> drag(int button, String description, Function<ClientTest, ClickTarget> start,
        Function<ClientTest, Point> end, int frames) {
        return mainWindow.drag(button, description, w -> start.apply(this), w -> end.apply(this), frames);
    }

    public void escape() {
        mainWindow.escapeNow();
    }

    public CompletableFuture<Void> key(int key, char character) {
        return mainWindow.key(key, character);
    }

    /** Registers cleanup on the client thread, run even after failure or timeout. Call inside {@link #run} */
    public void afterTest(Runnable action) {
        requireClientThread();
        cleanup.add(action);
    }

    void requireClientThread() {
        if (Thread.currentThread() != clientThread) throw new IllegalStateException("Use an awaited client operation");
    }

    /** Internal lifecycle entry, called by the client runtime only */
    public static void tick(Phase phase) {
        ClientTest session = active;
        if (session == null) return;
        session.clientThread = Thread.currentThread();
        if (phase == Phase.FRAME) session.renderedFrames++;
        if (session.closed) {
            if (phase == Phase.FRAME) session.finish();
            return;
        }
        session.operations.dispatch(phase);
    }

    /** Records a real update/render failure without replacing it with a secondary capture error */
    public static boolean failActive(Throwable error) {
        ClientTest session = active;
        if (session == null) return false;
        if (session.failure == null) {
            session.failure = error;
            GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            session.diagnostics.add(
                "failedScreen=" + (screen == null ? "<none>"
                    : screen.getClass()
                        .getName()));
        }
        return true;
    }

    /** Internal lifecycle diagnostics, retained in the active case and in the log even before a case attaches */
    public static void recordLifecycleDiagnostic(String message) {
        HorizonQAMod.LOG.info("CLIENT_TEST {}", message);
        ClientTest session = active;
        if (session != null) session.diagnostics.add(message);
    }

    private void finish() {
        if (finalCapture == null) beginCleanup();
        if (cleanupError == null && restoringWindow != null && !restoringWindow.ready()) return;
        if (!finalCapture.isDone()) return;
        Throwable captureError = null;
        try {
            finalCapture.join();
        } catch (CompletionException | CancellationException e) {
            captureError = e instanceof CompletionException ? e.getCause() : e;
        }
        retainFatalCleanup(captureError);
        if (captureError != null) diagnostics.add("captureFailure=" + captureError);
        Throwable error = failure != failureAtClose ? failure : cleanupError;
        if (error != null && cleanupError != null && error != cleanupError) error.addSuppressed(cleanupError);
        if (error != null && captureError != null && error != captureError) error.addSuppressed(captureError);
        completeTeardown(error == null ? captureError : error);
    }

    private void completeTeardown(Throwable error) {
        retainFatalCleanup(error);
        active = null;
        if (error != null) teardown.completeExceptionally(error);
        else teardown.complete(null);
        FatalErrors.rethrow(fatalCleanupError);
    }

    private void beginCleanup() {
        if (!waitingForInput.isEmpty()) diagnostics.add(waitingForInput);
        diagnostics.add(mainWindow.diagnostic());
        additionalWindows.values()
            .forEach(window -> diagnostics.add(window.diagnostic()));
        finalCapture = CompletableFuture.completedFuture(null);
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        diagnostics.add(
            "lastScreen=" + (screen == null ? "<none>"
                : screen.getClass()
                    .getName()));
        try {
            if (failure == null) finalCapture = captureFrame("final");
            else diagnostics.add("captureSkipped=client update/render failed, no completed frame available");
        } catch (Throwable t) {
            retainFatalCleanup(t);
            finalCapture = new CompletableFuture<>();
            finalCapture.completeExceptionally(t);
        }
        List<Runnable> teardownActions = new ArrayList<>();
        teardownActions.add(LwjglInput::end);
        for (ClientWindow window : additionalWindows.values()) {
            teardownActions.add(window::release);
        }
        if (!additionalWindows.isEmpty()) {
            teardownActions.add(com.gtnewhorizons.horizonqa.client.OffscreenWindowDriver::releaseAll);
        }
        teardownActions.addAll(cleanup);
        teardownActions.add(() -> ClientTestRuntime.setSoundEnabled(HorizonQAProperties.clientSoundEnabled()));
        teardownActions.add(
            () -> Minecraft.getMinecraft()
                .displayGuiScreen(null));
        if (originalWindowSize != null) teardownActions.add(() -> {
            restoringWindow = new WindowResize(originalWindowSize.x, originalWindowSize.y);
            restoringWindow.request();
        });
        cleanupError = runCleanup(teardownActions);
    }

    private final class WindowResize {

        private final int width;
        private final int height;
        private long readyFrame = -1;

        private WindowResize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        private void request() {
            Point previous = new Point(Display.getWidth(), Display.getHeight());
            LwjglInput.end();
            LwjglWindow.resize(width, height);
            if (originalWindowSize == null) originalWindowSize = previous;
        }

        private boolean ready() {
            Minecraft mc = Minecraft.getMinecraft();
            waitingForInput = "waitingForWindow=" + width
                + "x"
                + height
                + ", display="
                + Display.getWidth()
                + "x"
                + Display.getHeight()
                + ", minecraft="
                + mc.displayWidth
                + "x"
                + mc.displayHeight;
            if (Display.getWidth() != width || Display.getHeight() != height
                || mc.displayWidth != width
                || mc.displayHeight != height) {
                readyFrame = -1;
                return false;
            }
            if (readyFrame < 0) readyFrame = renderedFrames;
            return renderedFrames > readyFrame;
        }
    }

    private Throwable runCleanup(List<Runnable> actions) {
        Throwable error = null;
        for (Runnable action : actions) {
            try {
                action.run();
            } catch (Throwable t) {
                retainFatalCleanup(t);
                if (error == null) error = t;
                else if (error != t) error.addSuppressed(t);
            }
        }
        return error;
    }

    private void retainFatalCleanup(Throwable error) {
        if (FatalErrors.isFatal(error) && fatalCleanupError == null) fatalCleanupError = (Error) error;
    }

    private CompletableFuture<File> captureFrame(String checkpoint) {
        if (failure != null) ClientTest.<RuntimeException>rethrow(failure);
        return frames.capture(checkpoint)
            .whenComplete(
                (file, error) -> { if (file != null) diagnostics.add("artifact=" + file.getAbsolutePath()); });
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void rethrow(Throwable error) throws T {
        throw (T) error;
    }

}
