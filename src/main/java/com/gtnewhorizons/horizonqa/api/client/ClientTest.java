package com.gtnewhorizons.horizonqa.api.client;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.client.ClientTaskQueue;
import com.gtnewhorizons.horizonqa.client.ClientTaskQueue.Phase;
import com.gtnewhorizons.horizonqa.client.FrameCapture;
import com.gtnewhorizons.horizonqa.client.LwjglInput;

/** A test-owned client session. Attach on the server test thread, then await every client operation. */
public final class ClientTest {

    private static volatile ClientTest active;
    private final ClientTaskQueue operations = new ClientTaskQueue();
    private final List<Runnable> cleanup = new ArrayList<>();
    private final List<String> diagnostics = new CopyOnWriteArrayList<>();
    private final FrameCapture frames;
    private final CompletableFuture<Void> teardown = new CompletableFuture<>();
    private volatile boolean closed;
    private volatile Throwable failure;
    private Thread clientThread;
    private String waitingForInput = "";
    private long renderedFrames;

    private ClientTest(GameTestHelper helper) {
        frames = new FrameCapture(
            new File(
                HorizonQAProperties.junitReportFile()
                    .getAbsoluteFile()
                    .getParentFile(),
                "artifacts/" + helper.getTestId()
                    .replaceAll("[^a-zA-Z0-9._-]", "_")));
    }

    /**
     * Attaches the sole client session to this test. Teardown invalidates queued work, captures the final
     * rendered frame, releases input, runs client cleanup, and closes the screen before server cleanup.
     */
    public static synchronized ClientTest attach(GameTestHelper helper) {
        if (!HorizonQAProperties.clientTestsEnabled()) throw new IllegalStateException("Client test mode is disabled");
        if (active != null) throw new IllegalStateException("Another client test still owns the screen");
        ClientTest session = new ClientTest(helper);
        helper.afterTestAsync(100, session::close);
        helper.afterTest(() -> session.diagnostics.forEach(helper::recordDiagnostic));
        helper.onEachTick(
            "client lifecycle",
            () -> { if (session.failure != null) ClientTest.<RuntimeException>rethrow(session.failure); });
        active = session;
        return session;
    }

    private CompletableFuture<Void> close() {
        operations.close();
        closed = true;
        return teardown;
    }

    /** Internal orderly shutdown entry. Completion means client teardown has actually finished. */
    public static CompletableFuture<Void> closeActive() {
        ClientTest session = active;
        return session == null ? CompletableFuture.completedFuture(null) : session.close();
    }

    /** Queues an action on the next client END tick. The result completes after the action returns. */
    public CompletableFuture<Void> run(Consumer<ClientTest> action) {
        return operations.submit(Phase.END, () -> {
            action.accept(this);
            return null;
        });
    }

    /**
     * Presses the configured use-item control for one normal client tick, then releases it and completes at END.
     * Requires a world, a player with an equipped item and no active GUI. The ordinary input loop handles the
     * mouse or keyboard event, including mod input hooks. Normal aiming and block/entity interactions still apply.
     * Completion means input was processed and released, not that a mod accepted the use. Await a separate
     * observable assertion for the result. The enclosing sequence step bounds this operation.
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

    /** Captures the completed display framebuffer before buffer swap and returns the PNG path. */
    public CompletableFuture<File> capture(String checkpoint) {
        if (!checkpoint.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
            throw new IllegalArgumentException("Checkpoint must be a simple artifact name");
        }
        return operations.submit(Phase.FRAME, () -> captureFrame(checkpoint))
            .thenCompose(future -> future);
    }

    /** Returns the real active screen. Call inside {@link #run}. */
    public GuiScreen screen() {
        requireClientThread();
        return Minecraft.getMinecraft().currentScreen;
    }

    /** Asserts and returns the current screen type. A mismatch is retryable in an asynchronous wait step. */
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

    /**
     * Resolves a unique target on the client thread, moves the virtual pointer, waits for a normal rendered
     * frame, then dispatches press/release on a following client tick. Coordinates use the active screen scale.
     */
    public CompletableFuture<Void> click(int button, Function<ClientTest, Point> target) {
        return click(button, target, client -> true);
    }

    /**
     * Also waits for a consumer's observable hit-test readiness after positioning the pointer. False keeps
     * waiting without clicking. Exceptions fail immediately. The enclosing sequence step bounds the wait.
     */
    public CompletableFuture<Void> click(int button, Function<ClientTest, Point> target, Predicate<ClientTest> ready) {
        return click(button, target, ready, false);
    }

    /**
     * Performs an awaited click while Shift is held through mouse press and release. Uses the same target
     * readiness contract as {@link #click(int, Function, Predicate)} and releases input even when dispatch fails.
     */
    public CompletableFuture<Void> shiftClick(int button, Function<ClientTest, Point> target,
        Predicate<ClientTest> ready) {
        return click(button, target, ready, true);
    }

    /**
     * Clicks a named live element, resolving it again on each client tick until the GUI accepts the pointer.
     * Return {@code null} while the element is absent, hidden or disabled. Reject ambiguous matches with an
     * exception. Resolver and readiness exceptions fail immediately with their original cause.
     * <p>
     * The pointer is placed at the center of the visible bounds. A changed screen, target identity, or bounds
     * that no longer contain the pointer causes repositioning and another normal rendered frame. Moving bounds
     * may still be clicked when they contain the pointer and the current actual hit test accepts it.
     * The enclosing sequence step bounds all waiting. Use its label and {@code description} to identify the target.
     */
    public CompletableFuture<Void> click(int button, String description, Function<ClientTest, ClickTarget> resolver) {
        return click(button, description, resolver, false);
    }

    /** Performs a dynamically resolved {@link #click(int, String, Function)} with Shift held through mouse release. */
    public CompletableFuture<Void> shiftClick(int button, String description,
        Function<ClientTest, ClickTarget> resolver) {
        return click(button, description, resolver, true);
    }

    /**
     * Sends one wheel event after the same target positioning and readiness checks as
     * {@link #click(int, String, Function)}.
     * The nonzero delta uses raw LWJGL wheel units, normally +120 for one upward notch and -120 for one downward notch.
     * The exact signed value is delivered once, without splitting it into repeated events. The GUI decides its effect.
     * Completion means the screen handled the event and input was cleared, not that scrolling changed the GUI.
     */
    public CompletableFuture<Void> scroll(int wheelDelta, String description,
        Function<ClientTest, ClickTarget> resolver) {
        if (wheelDelta == 0) throw new IllegalArgumentException("Wheel delta must be nonzero");
        return atTarget(description, resolver, point -> {
            try {
                LwjglInput.scroll(requireScreen(), point.x, point.y, wheelDelta);
            } finally {
                LwjglInput.release();
            }
        });
    }

    private CompletableFuture<Void> click(int button, String description, Function<ClientTest, ClickTarget> resolver,
        boolean shift) {
        return atTarget(description, resolver, point -> dispatchClick(point.x, point.y, button, shift));
    }

    private CompletableFuture<Void> atTarget(String description, Function<ClientTest, ClickTarget> resolver,
        Consumer<Point> action) {
        DynamicTarget target = new DynamicTarget(description, resolver);
        return operations.submitWhenReady(Phase.END, target::ready, () -> {
            waitingForInput = "";
            action.accept(target.positioned.point);
            return null;
        });
    }

    private CompletableFuture<Void> click(int button, Function<ClientTest, Point> target, Predicate<ClientTest> ready,
        boolean shift) {
        return operations.submit(Phase.END, () -> {
            Point point = target.apply(this);
            GuiScreen screen = requireScreen();
            validatePoint(screen, point.x, point.y);
            LwjglInput.move(screen, point.x, point.y);
            return new PositionedTarget(screen, new Point(point), screen.width, screen.height);
        })
            .thenCompose(selected -> operations.submit(Phase.FRAME, () -> selected))
            .thenCompose(selected -> operations.submitWhenReady(Phase.END, () -> {
                waitingForInput = "waitingForInput=" + selected.point.x
                    + ","
                    + selected.point.y
                    + ", consumer hit-test readiness is false";
                return ready.test(this);
            }, () -> {
                GuiScreen screen = requireScreen();
                if (screen != selected.screen || screen.width != selected.width || screen.height != selected.height) {
                    throw new AssertionError(
                        "Screen changed while positioning the pointer, last screen: " + screen.getClass()
                            .getName());
                }
                waitingForInput = "";
                dispatchClick(selected.point.x, selected.point.y, button, shift);
                return null;
            }));
    }

    private void dispatchClick(int x, int y, int button, boolean shift) {
        GuiScreen screen = requireScreen();
        try {
            if (shift) LwjglInput.shift(screen, true);
            LwjglInput.mouse(screen, x, y, button, true);
            GuiScreen afterPress = Minecraft.getMinecraft().currentScreen;
            if (afterPress != null) LwjglInput.mouse(afterPress, x, y, button, false);
            GuiScreen afterRelease = Minecraft.getMinecraft().currentScreen;
            if (shift && afterRelease != null) LwjglInput.shift(afterRelease, false);
        } finally {
            LwjglInput.release();
        }
    }

    private static void validatePoint(GuiScreen screen, int x, int y) {
        if (x < 0 || y < 0 || x >= screen.width || y >= screen.height) {
            throw new IllegalArgumentException("Click lies outside the active screen: " + x + "," + y);
        }
    }

    /** Dispatches Escape press/release through the real screen keyboard lifecycle. */
    public void escape() {
        GuiScreen screen = requireScreen();
        try {
            LwjglInput.escape(screen, true);
            GuiScreen afterPress = Minecraft.getMinecraft().currentScreen;
            if (afterPress != null) LwjglInput.escape(afterPress, false);
        } finally {
            LwjglInput.release();
        }
    }

    /** Registers cleanup on the client thread, run even after failure or timeout. Call inside {@link #run}. */
    public void afterTest(Runnable action) {
        requireClientThread();
        cleanup.add(action);
    }

    private GuiScreen requireScreen() {
        GuiScreen screen = screen();
        if (screen == null) throw new AssertionError("Expected an active screen, last screen: <none>");
        return screen;
    }

    private void requireClientThread() {
        if (Thread.currentThread() != clientThread) throw new IllegalStateException("Use an awaited client operation");
    }

    /** Internal lifecycle entry, called by the client runtime only. */
    public static void tick(Phase phase) {
        ClientTest session = active;
        if (session == null) return;
        session.clientThread = Thread.currentThread();
        if (session.closed) {
            if (phase == Phase.FRAME) session.finish();
            return;
        }
        if (phase == Phase.FRAME) session.renderedFrames++;
        session.operations.dispatch(phase);
    }

    /** Records a real update/render failure without replacing it with a secondary capture error. */
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

    /** Internal lifecycle diagnostics, retained in the active case and in the log even before a case attaches. */
    public static void recordLifecycleDiagnostic(String message) {
        HorizonQAMod.LOG.info("CLIENT_TEST {}", message);
        ClientTest session = active;
        if (session != null) session.diagnostics.add(message);
    }

    private void finish() {
        if (!waitingForInput.isEmpty()) diagnostics.add(waitingForInput);
        CompletableFuture<File> finalCapture = CompletableFuture.completedFuture(null);
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        diagnostics.add(
            "lastScreen=" + (screen == null ? "<none>"
                : screen.getClass()
                    .getName()));
        try {
            if (failure == null) finalCapture = captureFrame("final");
            else diagnostics.add("captureSkipped=client update/render failed, no completed frame available");
        } catch (Throwable t) {
            finalCapture = new CompletableFuture<>();
            finalCapture.completeExceptionally(t);
        }
        List<Runnable> teardownActions = new ArrayList<>();
        teardownActions.add(LwjglInput::end);
        teardownActions.addAll(cleanup);
        teardownActions.add(
            () -> Minecraft.getMinecraft()
                .displayGuiScreen(null));
        Throwable cleanupError = runCleanup(teardownActions);
        active = null;
        finalCapture.whenComplete((file, captureError) -> {
            if (captureError != null) diagnostics.add("captureFailure=" + captureError);
            if (cleanupError != null) teardown.completeExceptionally(cleanupError);
            else if (captureError != null) teardown.completeExceptionally(captureError);
            else teardown.complete(null);
        });
    }

    private static Throwable runCleanup(List<Runnable> actions) {
        Throwable error = null;
        for (Runnable action : actions) {
            try {
                action.run();
            } catch (Throwable t) {
                if (error == null) error = t;
                else if (error != t) error.addSuppressed(t);
            }
        }
        return error;
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

    private final class DynamicTarget {

        private final String description;
        private final Function<ClientTest, ClickTarget> resolver;
        private PositionedTarget positioned;
        private Object identity;
        private long positionedFrame;

        private DynamicTarget(String description, Function<ClientTest, ClickTarget> resolver) {
            this.description = Objects.requireNonNull(description, "description");
            this.resolver = Objects.requireNonNull(resolver, "resolver");
        }

        private boolean ready() {
            waitingForInput = "waitingForInput=" + description + ", resolving target";
            ClickTarget target = resolver.apply(ClientTest.this);
            GuiScreen screen = screen();
            if (target == null || screen == null) {
                positioned = null;
                return waiting("target unavailable");
            }
            Rectangle visible = target.bounds.intersection(new Rectangle(0, 0, screen.width, screen.height));
            if (visible.isEmpty()) {
                positioned = null;
                return waiting("target outside screen or empty, bounds=" + target.bounds);
            }
            if (positioned == null || screen != positioned.screen
                || screen.width != positioned.width
                || screen.height != positioned.height
                || !Objects.equals(identity, target.identity)
                || !visible.contains(positioned.point)) {
                Point point = new Point(visible.x + visible.width / 2, visible.y + visible.height / 2);
                LwjglInput.move(screen, point.x, point.y);
                positioned = new PositionedTarget(screen, point, screen.width, screen.height);
                identity = target.identity;
                positionedFrame = renderedFrames;
            }
            if (renderedFrames <= positionedFrame) return waiting("awaiting rendered frame at " + positioned.point);
            waiting("hit-test not ready at " + positioned.point + ", bounds=" + visible);
            return target.ready.getAsBoolean();
        }

        private boolean waiting(String reason) {
            waitingForInput = "waitingForInput=" + description + ", " + reason;
            return false;
        }
    }

    private static final class PositionedTarget {

        final GuiScreen screen;
        final Point point;
        final int width;
        final int height;

        PositionedTarget(GuiScreen screen, Point point, int width, int height) {
            this.screen = screen;
            this.point = point;
            this.width = width;
            this.height = height;
        }
    }

}
