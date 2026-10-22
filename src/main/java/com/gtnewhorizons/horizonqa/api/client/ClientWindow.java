package com.gtnewhorizons.horizonqa.api.client;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizons.horizonqa.client.ClientTaskQueue;
import com.gtnewhorizons.horizonqa.client.ClientTaskQueue.Phase;
import com.gtnewhorizons.horizonqa.client.WindowDriver;

/** One window in a test. Operations share the owning test's queue and retain this window's identity. */
public final class ClientWindow {

    private final ClientTest owner;
    private final ClientTaskQueue operations;
    private final WindowDriver driver;
    private String waitingForInput = "";

    ClientWindow(ClientTest owner, ClientTaskQueue operations, WindowDriver driver) {
        this.owner = owner;
        this.operations = operations;
        this.driver = driver;
    }

    /** Runs an observation in this window's GUI context on the client thread. */
    public CompletableFuture<Void> run(Consumer<ClientWindow> action) {
        return operations.submit(Phase.END, () -> driver.observe(() -> {
            action.accept(this);
            return null;
        }));
    }

    /** Returns this window's screen. Use inside an awaited client operation. */
    public GuiScreen screen() {
        owner.requireClientThread();
        return driver.screen();
    }

    /** Asserts this window's current screen type. */
    public <T extends GuiScreen> T screen(Class<T> type) {
        GuiScreen screen = screen();
        if (!type.isInstance(screen))
            throw new AssertionError("Expected " + type.getName() + " in " + driver.description());
        return type.cast(screen);
    }

    /** Returns whether the original window still exists, without rebinding to a replacement. */
    public boolean isOpen() {
        owner.requireClientThread();
        return driver.isOpen();
    }

    /** Resizes this window through its native event path and waits for its own completed frame. */
    public CompletableFuture<Void> resize(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Window dimensions must be positive");
        return driver.resize(owner, width, height);
    }

    /** Captures a completed frame of this window. */
    public CompletableFuture<File> capture(String checkpoint) {
        return driver.capture(owner, checkpoint);
    }

    /** Closes a test-owned additional window. The main Minecraft window cannot be closed here. */
    public CompletableFuture<Void> close() {
        return operations.submit(Phase.END, () -> {
            driver.close();
            return null;
        });
    }

    String diagnostic() {
        return driver.description() + ": " + waitingForInput;
    }

    void release() {
        driver.release();
    }

    private GuiScreen requireScreen() {
        GuiScreen screen = screen();
        if (screen == null) throw new AssertionError("Expected a screen in " + driver.description());
        return screen;
    }

    /**
     * Resolves a unique target on the client thread, moves the virtual pointer, waits for a normal rendered
     * frame, then dispatches press/release on a following client tick. Coordinates use the active screen scale.
     */
    public CompletableFuture<Void> click(int button, Function<ClientWindow, Point> target) {
        return click(button, target, client -> true);
    }

    /**
     * Also waits for a consumer's observable hit-test readiness after positioning the pointer. False keeps
     * waiting without clicking. Exceptions fail immediately. The enclosing sequence step bounds the wait.
     */
    public CompletableFuture<Void> click(int button, Function<ClientWindow, Point> target,
        Predicate<ClientWindow> ready) {
        return click(button, target, ready, false);
    }

    /**
     * Performs an awaited click while Shift is held through mouse press and release. Uses the same target
     * readiness contract as {@link #click(int, Function, Predicate)} and releases input even when dispatch fails.
     */
    public CompletableFuture<Void> shiftClick(int button, Function<ClientWindow, Point> target,
        Predicate<ClientWindow> ready) {
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
    public CompletableFuture<Void> click(int button, String description, Function<ClientWindow, ClickTarget> resolver) {
        return click(button, description, resolver, false);
    }

    /** Performs a dynamically resolved {@link #click(int, String, Function)} with Shift held through mouse release. */
    public CompletableFuture<Void> shiftClick(int button, String description,
        Function<ClientWindow, ClickTarget> resolver) {
        return click(button, description, resolver, true);
    }

    /**
     * Sends one wheel event after the same target positioning and readiness checks as
     * {@link #click(int, String, Function)}.
     * The nonzero delta uses raw LWJGL wheel units, normally +120 for one upward notch and -120 for one downward notch.
     * The main window receives the exact signed value once, without splitting it into repeated events.
     * Offscreen 0.3 reduces a frame's wheel input to one signed notch. The GUI decides its effect.
     * Completion means the host consumed the event and input was cleared, not that scrolling changed the GUI.
     * Offscreen 0.3 VanillaUI does not forward wheel callbacks. Its MUI2 adapter does.
     */
    public CompletableFuture<Void> scroll(int wheelDelta, String description,
        Function<ClientWindow, ClickTarget> resolver) {
        if (wheelDelta == 0) throw new IllegalArgumentException("Wheel delta must be nonzero");
        return atTarget(description, resolver, point -> {
            try {
                driver.scroll(requireScreen(), point.x, point.y, wheelDelta);
                return driver.inputConsumed()
                    .whenComplete((ignored, error) -> driver.release());
            } catch (RuntimeException | Error error) {
                driver.release();
                throw error;
            }
        });
    }

    private CompletableFuture<Void> click(int button, String description, Function<ClientWindow, ClickTarget> resolver,
        boolean shift) {
        return atTarget(description, resolver, point -> dispatchClick(point.x, point.y, button, shift));
    }

    /**
     * Drags from a live target to a screen-coordinate endpoint along a straight path.
     * Resolves the endpoint once immediately before pressing, without requiring a clickable endpoint.
     * Each of the positive number of movement frames is rendered with the button held before its motion event.
     * Releases at the endpoint. A screen change, resize or handler failure aborts the gesture and clears input.
     * Test teardown also releases held input. Await completion before submitting another input operation.
     * Offscreen 0.3 VanillaUI supports polling-driven gestures but does not forward mouseClickMove.
     * Callback-driven dragging requires a host adapter that implements it, such as MUI2.
     */
    public CompletableFuture<Void> drag(int button, String description, Function<ClientWindow, ClickTarget> start,
        Function<ClientWindow, Point> end, int frames) {
        if (frames <= 0) throw new IllegalArgumentException("Drag frames must be positive");
        Drag drag = new Drag(button, description, Objects.requireNonNull(end, "end"), frames);
        return atTarget(description, start, drag::begin)
            .thenCompose(ignored -> operations.submitWhenReady(Phase.END, drag::advance, () -> null))
            .thenCompose(ignored -> driver.inputConsumed());
    }

    private CompletableFuture<Void> atTarget(String description, Function<ClientWindow, ClickTarget> resolver,
        Function<Point, CompletableFuture<Void>> action) {
        DynamicTarget target = new DynamicTarget(description, resolver);
        return operations.submitWhenReady(Phase.END, () -> driver.observe(target::ready), () -> {
            waitingForInput = "";
            return action.apply(target.positioned.point);
        })
            .thenCompose(result -> result);
    }

    private CompletableFuture<Void> click(int button, Function<ClientWindow, Point> target,
        Predicate<ClientWindow> ready, boolean shift) {
        return operations.submit(Phase.END, () -> {
            Point point = driver.observe(() -> target.apply(this));
            GuiScreen screen = requireScreen();
            validatePoint(screen, point.x, point.y);
            driver.move(screen, point.x, point.y);
            return new PositionedTarget(screen, new Point(point), screen.width, screen.height);
        })
            .thenCompose(
                selected -> operations
                    .submitWhenReady(Phase.END, () -> driver.renderedFrames() > selected.frame, () -> selected))
            .thenCompose(selected -> operations.submitWhenReady(Phase.END, () -> {
                waitingForInput = "waitingForInput=" + selected.point.x
                    + ","
                    + selected.point.y
                    + ", consumer hit-test readiness is false";
                return driver.observe(() -> ready.test(this));
            }, () -> {
                GuiScreen screen = requireScreen();
                if (screen != selected.screen || screen.width != selected.width || screen.height != selected.height) {
                    throw new AssertionError(
                        "Screen changed while positioning the pointer, last screen: " + screen.getClass()
                            .getName());
                }
                waitingForInput = "";
                return dispatchClick(selected.point.x, selected.point.y, button, shift);
            }))
            .thenCompose(result -> result);
    }

    private CompletableFuture<Void> dispatchClick(int x, int y, int button, boolean shift) {
        GuiScreen screen = requireScreen();
        try {
            if (shift) driver.shift(screen, true);
            driver.mouse(screen, x, y, button, true);
            return driver.inputConsumed()
                .thenCompose(ignored -> {
                    GuiScreen next = screen();
                    if (next != null) driver.mouse(next, x, y, button, false);
                    return driver.inputConsumed();
                })
                .thenCompose(ignored -> {
                    GuiScreen next = screen();
                    if (shift && next != null) driver.shift(next, false);
                    return shift ? driver.inputConsumed() : CompletableFuture.completedFuture(null);
                })
                .whenComplete((ignored, error) -> driver.release());
        } catch (RuntimeException | Error error) {
            driver.release();
            throw error;
        }
    }

    private static void validatePoint(GuiScreen screen, int x, int y) {
        if (x < 0 || y < 0 || x >= screen.width || y >= screen.height) {
            throw new IllegalArgumentException("Click lies outside the active screen: " + x + "," + y);
        }
    }

    /** Dispatches Escape press/release through the real screen keyboard lifecycle. */
    public CompletableFuture<Void> escape() {
        return key(Keyboard.KEY_ESCAPE, '\0');
    }

    void escapeNow() {
        dispatchKey(Keyboard.KEY_ESCAPE, '\0').join();
    }

    /**
     * Queues a single key press and release through the active screen's normal input handling.
     * Uses a nonzero LWJGL keycode and the supplied typed character, or '\0' for a non-text key.
     * The consumer owns GUI focus. Completion confirms dispatch and input cleanup, not text acceptance.
     * Requires an active screen and preserves the original handler failure while releasing held input.
     */
    public CompletableFuture<Void> key(int keyCode, char character) {
        return operations.submit(Phase.END, () -> dispatchKey(keyCode, character))
            .thenCompose(result -> result);
    }

    private CompletableFuture<Void> dispatchKey(int keyCode, char character) {
        GuiScreen screen = requireScreen();
        try {
            driver.key(screen, keyCode, character, true);
            return driver.inputConsumed()
                .thenCompose(ignored -> {
                    GuiScreen next = screen();
                    if (next != null) driver.key(next, keyCode, '\0', false);
                    return driver.inputConsumed();
                })
                .whenComplete((ignored, error) -> driver.release());
        } catch (RuntimeException | Error error) {
            driver.release();
            throw error;
        }
    }

    private final class Drag {

        private final int button;
        private final String description;
        private final Function<ClientWindow, Point> endpoint;
        private final int frames;
        private PositionedTarget origin;
        private Point end;
        private Point point;
        private int step;
        private long positionedFrame;

        private Drag(int button, String description, Function<ClientWindow, Point> endpoint, int frames) {
            this.button = button;
            this.description = description;
            this.endpoint = endpoint;
            this.frames = frames;
        }

        private CompletableFuture<Void> begin(Point start) {
            try {
                GuiScreen screen = requireScreen();
                origin = new PositionedTarget(screen, new Point(start), screen.width, screen.height);
                end = new Point(
                    Objects.requireNonNull(driver.observe(() -> endpoint.apply(ClientWindow.this)), "Drag endpoint"));
                validatePoint(screen, end.x, end.y);
                driver.beginDrag(screen, start.x, start.y, button);
                return driver.inputConsumed()
                    .thenRun(() -> {
                        checkScreen();
                        positionNextFrame();
                    })
                    .whenComplete((ignored, error) -> { if (error != null) driver.release(); });
            } catch (RuntimeException | Error error) {
                driver.release();
                throw error;
            }
        }

        private boolean advance() {
            try {
                checkScreen();
                if (driver.renderedFrames() <= positionedFrame) return false;
                driver.dragMove(origin.screen, point.x, point.y);
                checkScreen();
                if (step == frames) {
                    driver.mouse(origin.screen, end.x, end.y, button, false);
                    driver.release();
                    waitingForInput = "";
                    return true;
                }
                positionNextFrame();
                return false;
            } catch (RuntimeException | Error error) {
                driver.release();
                throw error;
            }
        }

        private void checkScreen() {
            if (screen() != origin.screen || origin.screen.width != origin.width
                || origin.screen.height != origin.height
                || !driver.isDragging()) {
                throw new IllegalStateException("Screen changed during drag");
            }
        }

        private void positionNextFrame() {
            step++;
            double progress = (double) step / frames;
            point = new Point(
                origin.point.x + (int) Math.round((end.x - origin.point.x) * progress),
                origin.point.y + (int) Math.round((end.y - origin.point.y) * progress));
            driver.move(origin.screen, point.x, point.y);
            positionedFrame = driver.renderedFrames();
            waitingForInput = "waitingForInput=" + description + ", drag frame " + step + "/" + frames + " at " + point;
        }
    }

    private final class DynamicTarget {

        private final String description;
        private final Function<ClientWindow, ClickTarget> resolver;
        private PositionedTarget positioned;
        private Object identity;
        private long positionedFrame;

        private DynamicTarget(String description, Function<ClientWindow, ClickTarget> resolver) {
            this.description = Objects.requireNonNull(description, "description");
            this.resolver = Objects.requireNonNull(resolver, "resolver");
        }

        private boolean ready() {
            waitingForInput = "waitingForInput=" + description + ", resolving target";
            ClickTarget target = resolver.apply(ClientWindow.this);
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
                driver.move(screen, point.x, point.y);
                positioned = new PositionedTarget(screen, point, screen.width, screen.height);
                identity = target.identity;
                positionedFrame = driver.renderedFrames();
            }
            if (driver.renderedFrames() <= positionedFrame)
                return waiting("awaiting rendered frame at " + positioned.point);
            waiting("hit-test not ready at " + positioned.point + ", bounds=" + visible);
            return target.ready.getAsBoolean();
        }

        private boolean waiting(String reason) {
            waitingForInput = "waitingForInput=" + description + ", " + reason;
            return false;
        }
    }

    private final class PositionedTarget {

        final long frame = driver.renderedFrames();
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
