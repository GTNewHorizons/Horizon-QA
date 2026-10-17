package com.gtnewhorizons.horizonqa.api.client;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizons.horizonqa.internal.GameTestSequence;

/**
 * Fluent registrations on one test's existing server-owned sequence and client session.
 * Build on the server test thread through {@link ClientTest#scenario}; finish with {@link #succeed()}.
 * Registration never executes client work. Actions run once; assertion waits retry within their budget.
 * All bounded budgets are positive server END tick counts, including time in flight.
 * Client input, cancellation and bounded cleanup
 * retain the {@link ClientTest} contract. Custom mutations must register restoration before changing state.
 */
public final class ClientScenario {

    private final GameTestSequence sequence;
    private final ClientTest session;
    private int defaultTimeout = 100;
    private boolean finished;

    ClientScenario(GameTestSequence sequence, ClientTest session) {
        this.sequence = Objects.requireNonNull(sequence, "sequence");
        this.session = Objects.requireNonNull(session, "session");
    }

    /** Sets the positive budget of subsequently registered bounded steps; initially 100 END ticks. */
    public ClientScenario defaultTimeoutTicks(int ticks) {
        checkOpen();
        if (ticks <= 0) throw new IllegalArgumentException("Tick budget must be positive");
        defaultTimeout = ticks;
        return this;
    }

    /** Uses the equipped item through its configured binding, with the default budget. */
    public ClientScenario useHeldItem() {
        return useHeldItem(defaultTimeout);
    }

    /** Uses normal world input once; fixture setup may equip/aim the player, but must not call item handlers. */
    public ClientScenario useHeldItem(int maxTicks) {
        return async("use held item", maxTicks, ClientTest::useItem);
    }

    /** Waits for the live screen type, with the default budget. */
    public ClientScenario awaitScreen(Class<? extends GuiScreen> type) {
        return awaitScreen(type, defaultTimeout);
    }

    /** Retries screen-type assertions on the client thread; no screen instance is retained. */
    public ClientScenario awaitScreen(Class<? extends GuiScreen> type, int maxTicks) {
        Objects.requireNonNull(type, "type");
        return awaitClient("await screen " + type.getSimpleName(), maxTicks, c -> c.screen(type));
    }

    /** Left-clicks a live target, with the default budget. */
    public ClientScenario click(ClientTarget target) {
        return click(target, defaultTimeout);
    }

    /** Waits for live readiness, clicks once, and awaits release. Assert resulting state separately. */
    public ClientScenario click(ClientTarget target, int maxTicks) {
        Objects.requireNonNull(target, "target");
        return async("click " + target.description, maxTicks, c -> c.click(0, target.description, target.resolver));
    }

    /** Sends two native left clicks at one ready position, with the default budget. */
    public ClientScenario doubleClick(ClientTarget target) {
        return doubleClick(target, defaultTimeout);
    }

    /** Sends a native double-click and awaits normal input processing and release within this operation's budget. */
    public ClientScenario doubleClick(ClientTarget target, int maxTicks) {
        Objects.requireNonNull(target, "target");
        return async(
            "double click " + target.description,
            maxTicks,
            c -> c.doubleClick(target.description, target.resolver));
    }

    /** Right-clicks a live target, with the default budget. */
    public ClientScenario rightClick(ClientTarget target) {
        return rightClick(target, defaultTimeout);
    }

    /** Sends one native right-click and awaits release, with this operation's budget. */
    public ClientScenario rightClick(ClientTarget target, int maxTicks) {
        Objects.requireNonNull(target, "target");
        return async(
            "right click " + target.description,
            maxTicks,
            c -> c.click(1, target.description, target.resolver));
    }

    /** Scrolls a live target with the default budget; positive OS notches scroll down, negative up. */
    public ClientScenario scroll(ClientTarget target, int notches) {
        return scroll(target, notches, defaultTimeout);
    }

    /** Sends 1..100 native wheel notches in either direction, then awaits normal input processing. */
    public ClientScenario scroll(ClientTarget target, int notches, int maxTicks) {
        Objects.requireNonNull(target, "target");
        return async(
            "scroll " + target.description,
            maxTicks,
            c -> c.scroll(notches, target.description, target.resolver));
    }

    /** Drags a live target to a client-resolved endpoint over rendered movement frames, with the default budget. */
    public ClientScenario drag(ClientTarget target, Function<ClientTest, Point> endpoint, int movementFrames) {
        return drag(target, endpoint, movementFrames, defaultTimeout);
    }

    /**
     * Sends one native left-button drag and observes release. Endpoints use current scaled GUI pixels.
     * Movement frames define the gesture trajectory; maxTicks bounds readiness and completion.
     */
    public ClientScenario drag(ClientTarget target, Function<ClientTest, Point> endpoint, int movementFrames,
        int maxTicks) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(endpoint, "endpoint");
        return async(
            "drag " + target.description,
            maxTicks,
            c -> c.drag(target.description, target.resolver, endpoint, movementFrames));
    }

    /** Resizes the real window and awaits normal game resize/rendering, with the default budget. */
    public ClientScenario resizeWindow(int width, int height) {
        return resizeWindow(width, height, defaultTimeout);
    }

    /** Resizes without calling Minecraft's resize handler; client teardown restores the original window size. */
    public ClientScenario resizeWindow(int width, int height, int maxTicks) {
        return async("resize window to " + width + "x" + height, maxTicks, c -> c.resizeWindow(width, height));
    }

    /** Shift-clicks a live target, with the default budget. */
    public ClientScenario shiftClick(ClientTarget target) {
        return shiftClick(target, defaultTimeout);
    }

    /** Owns Shift for one native left-click, including observed modifier release before completion. */
    public ClientScenario shiftClick(ClientTarget target, int maxTicks) {
        Objects.requireNonNull(target, "target");
        return async(
            "shift click " + target.description,
            maxTicks,
            c -> c.shiftClick(target.description, target.resolver));
    }

    /** Presses/releases an AWT {@code KeyEvent.VK_*} key, with the default budget. */
    public ClientScenario key(int awtKeyCode) {
        return key(awtKeyCode, defaultTimeout);
    }

    /** Sends one native key press/release. This is physical key input, not a text-entry contract. */
    public ClientScenario key(int awtKeyCode, int maxTicks) {
        return async("key " + awtKeyCode, maxTicks, c -> c.key(awtKeyCode));
    }

    /** Sends Escape through normal input, with the default budget. */
    public ClientScenario escape() {
        return escape(defaultTimeout);
    }

    /** Awaits native Escape release; separately await the expected screen or gameplay state. */
    public ClientScenario escape(int maxTicks) {
        return async("Escape", maxTicks, c -> c.key(KeyEvent.VK_ESCAPE));
    }

    /** Captures a completed framebuffer checkpoint, with the default budget. */
    public ClientScenario capture(String checkpoint) {
        return capture(checkpoint, defaultTimeout);
    }

    /** Awaits the normal rendered frame and off-thread PNG encoding, with this operation's budget. */
    public ClientScenario capture(String checkpoint, int maxTicks) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        return async("capture " + checkpoint, maxTicks, c -> c.capture(checkpoint));
    }

    /** Runs a custom client END callback once, with the default budget. */
    public ClientScenario client(String label, Consumer<ClientTest> action) {
        return client(label, defaultTimeout, action);
    }

    /** Runs once on the client thread. Register restoration with {@code c.afterTest} before mutation. */
    public ClientScenario client(String label, int maxTicks, Consumer<ClientTest> action) {
        Objects.requireNonNull(action, "action");
        return async(label, maxTicks, c -> c.run(action));
    }

    /** Retries client assertions, with the default budget and at most one attempt in flight. */
    public ClientScenario awaitClient(String label, Consumer<ClientTest> assertion) {
        return awaitClient(label, defaultTimeout, assertion);
    }

    /** Retries only completed assertion failures on the client thread. Observations must not mutate state. */
    public ClientScenario awaitClient(String label, int maxTicks, Consumer<ClientTest> assertion) {
        checkOpen();
        Objects.requireNonNull(assertion, "assertion");
        sequence.thenWaitUntilAsync(Objects.requireNonNull(label, "label"), maxTicks, () -> session.run(assertion));
        return this;
    }

    /** Runs custom server code once at END. Synchronous callbacks must not block and have no tick budget. */
    public ClientScenario server(String label, Runnable action) {
        checkOpen();
        sequence.thenExecute(Objects.requireNonNull(label, "label"), Objects.requireNonNull(action, "action"));
        return this;
    }

    /** Retries server assertions at END, with the default budget. */
    public ClientScenario awaitServer(String label, Runnable assertion) {
        return awaitServer(label, defaultTimeout, assertion);
    }

    /** Polls authoritative server state after world ticks, with this operation's positive budget. */
    public ClientScenario awaitServer(String label, int maxTicks, Runnable assertion) {
        checkOpen();
        sequence.thenWaitUntil(
            Objects.requireNonNull(label, "label"),
            maxTicks,
            Objects.requireNonNull(assertion, "assertion"));
        return this;
    }

    /**
     * Uses the default budget and the simulation constraints of
     * {@link #awaitServerAccelerated(String, int, int, Runnable)}.
     */
    public ClientScenario awaitServerAccelerated(String label, int multiplier, Runnable assertion) {
        return awaitServerAccelerated(label, defaultTimeout, multiplier, assertion);
    }

    /**
     * Retries authoritative server assertions at END after every complete world tick. The multiplier is
     * 1..100; 1 uses normal speed. All loaded server worlds advance. The positive budget and enclosing
     * test timeout count simulated server ticks. Acceleration ends before the next step or teardown,
     * including on failure and cancellation;
     * client input, rendering and subsequent client-operation budgets retain their normal cadence.
     * Use only when the assertion and simulation are independent of wall-clock timing and client progress.
     * The callback observes state and must not mutate it or wait for client work.
     */
    public ClientScenario awaitServerAccelerated(String label, int maxTicks, int multiplier, Runnable assertion) {
        checkOpen();
        sequence.thenWaitUntilAccelerated(
            Objects.requireNonNull(label, "label"),
            maxTicks,
            multiplier,
            Objects.requireNonNull(assertion, "assertion"));
        return this;
    }

    /** Schedules custom queued work once, with the default budget. The supplier starts on the server thread. */
    public ClientScenario async(String label, Function<ClientTest, ? extends CompletionStage<?>> action) {
        return async(label, defaultTimeout, action);
    }

    /**
     * Starts a custom asynchronous operation once on the server thread and awaits it without blocking.
     * Use the supplied session's queued methods for client work; client state belongs inside {@code c.run}.
     * This operation's label and budget never affect adjacent steps.
     */
    public ClientScenario async(String label, int maxTicks, Function<ClientTest, ? extends CompletionStage<?>> action) {
        checkOpen();
        Objects.requireNonNull(action, "action");
        sequence.thenExecuteAsync(Objects.requireNonNull(label, "label"), maxTicks, () -> action.apply(session));
        return this;
    }

    /**
     * Returns the same sequence for advanced START/END or asynchronous registrations.
     * Register those steps before continuing the scenario. No second sequence or runner is created.
     */
    public GameTestSequence serverSequence() {
        checkOpen();
        return sequence;
    }

    /** Appends the existing success step and closes registration. Reporting still waits for bounded cleanup. */
    public void succeed() {
        checkOpen();
        sequence.thenSucceed();
        finished = true;
    }

    private void checkOpen() {
        if (finished) throw new IllegalStateException("Scenario is already complete");
    }
}
