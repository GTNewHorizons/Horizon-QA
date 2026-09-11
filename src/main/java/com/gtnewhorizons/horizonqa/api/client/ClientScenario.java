package com.gtnewhorizons.horizonqa.api.client;

import java.awt.Point;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizons.horizonqa.internal.GameTestSequence;

/**
 * Fluent authoring for one client test, backed by its existing server-owned sequence.
 * Methods register deferred steps. They do not execute input while the scenario is being built.
 * Build the chain on the server test thread before its execution begins.
 * Obtain a scenario through {@link ClientTest#scenario} and finish it with {@link #succeed()}.
 * Custom callbacks retain access to the same client session and sequence, without a second execution engine.
 */
public final class ClientScenario {

    private static final int DEFAULT_TIMEOUT_TICKS = 100;

    private final GameTestSequence sequence;
    private final ClientTest session;
    private int defaultTimeout = DEFAULT_TIMEOUT_TICKS;
    private Integer nextTimeout;
    private String nextLabel;
    private boolean finished;

    ClientScenario(GameTestSequence sequence, ClientTest session) {
        this.sequence = Objects.requireNonNull(sequence, "sequence");
        this.session = Objects.requireNonNull(session, "session");
    }

    /** Changes the default budget for subsequent client operations and assertion waits. Initially 100 ticks. */
    public ClientScenario defaultTimeoutTicks(int ticks) {
        checkOpen();
        validateTicks(ticks);
        defaultTimeout = ticks;
        return this;
    }

    /** Overrides the budget of the next bounded step only. Synchronous server actions have no tick budget. */
    public ClientScenario withinTicks(int ticks) {
        checkOpen();
        validateTicks(ticks);
        nextTimeout = ticks;
        return this;
    }

    /** Overrides the next step's generated or explicit diagnostic label. */
    public ClientScenario step(String label) {
        checkOpen();
        nextLabel = Objects.requireNonNull(label, "label");
        return this;
    }

    /** Uses the equipped item through its configured world-input binding. */
    public ClientScenario useHeldItem() {
        return async("use held item", ClientTest::useHeldItem);
    }

    /** Waits for the current client screen to have the requested type. */
    public ClientScenario awaitScreen(Class<? extends GuiScreen> type) {
        Objects.requireNonNull(type, "type");
        return awaitClient("await screen " + type.getSimpleName(), c -> c.screen(type));
    }

    /** Left-clicks the live target after a rendered frame and its actual hit-test readiness. */
    public ClientScenario click(ClientTarget target) {
        return async("click " + target.description, c -> c.click(0, target.description, target.resolver));
    }

    /** Right-clicks the live target. */
    public ClientScenario rightClick(ClientTarget target) {
        return async("right click " + target.description, c -> c.click(1, target.description, target.resolver));
    }

    /** Left-clicks the live target while Shift is held. */
    public ClientScenario shiftClick(ClientTarget target) {
        return async("shift click " + target.description, c -> c.shiftClick(0, target.description, target.resolver));
    }

    /** Delivers one wheel event in raw LWJGL units, normally 120 or -120 per notch. */
    public ClientScenario scroll(ClientTarget target, int wheelDelta) {
        if (wheelDelta == 0) throw new IllegalArgumentException("Wheel delta must be nonzero");
        return async("scroll " + target.description, c -> c.scroll(wheelDelta, target.description, target.resolver));
    }

    /** Sends one native key press with its character and releases it. The consumer owns GUI focus. */
    public ClientScenario key(int keyCode, char character) {
        return async("key " + keyCode, c -> c.key(keyCode, character));
    }

    /** Closes the active screen through its normal Escape handling. */
    public ClientScenario escape() {
        return client("Escape", ClientTest::escape);
    }

    /** Captures a rendered framebuffer checkpoint. The existing artifact report retains its path. */
    public ClientScenario capture(String checkpoint) {
        return async("capture " + checkpoint, c -> c.capture(checkpoint));
    }

    /** Begins describing a left-button drag. Complete it with to(...).overFrames(...). */
    public DragStep drag(ClientTarget start) {
        checkOpen();
        return new DragStep(Objects.requireNonNull(start, "start"));
    }

    /** Runs custom code once on the client thread at END. */
    public ClientScenario client(String label, Consumer<ClientTest> action) {
        Objects.requireNonNull(action, "action");
        return async(label, c -> c.run(action));
    }

    /** Retries client-thread assertions until they pass, with at most one attempt in flight. */
    public ClientScenario awaitClient(String label, Consumer<ClientTest> assertion) {
        Objects.requireNonNull(assertion, "assertion");
        return awaitAsync(label, c -> c.run(assertion));
    }

    /** Runs custom server code once at END. Use serverSequence() for other existing phase operations. */
    public ClientScenario server(String label, Runnable action) {
        checkOpen();
        Objects.requireNonNull(action, "action");
        if (nextTimeout != null) throw new IllegalStateException("A synchronous server action has no tick budget");
        sequence.thenExecute(takeLabel(label), action);
        return this;
    }

    /** Retries assertions on the server at END using the next or default bounded-step budget. */
    public ClientScenario awaitServer(String label, Runnable assertion) {
        checkOpen();
        Objects.requireNonNull(assertion, "assertion");
        sequence.thenWaitUntil(takeLabel(label), takeTimeout(), assertion);
        return this;
    }

    /**
     * Waits on server state while requesting 1 to 100 full server ticks per normal loop iteration in CI.
     * The next/default budget still counts simulated server ticks. Other steps retain their normal rate.
     * The assertion must not submit client work. Rendering and client synchronization are not accelerated.
     * Completion, failure and test teardown end this step's request without a separate reset operation.
     */
    public ClientScenario awaitServerAccelerated(String label, int multiplier, Runnable assertion) {
        checkOpen();
        Objects.requireNonNull(assertion, "assertion");
        sequence.thenWaitUntilAccelerated(takeLabel(label), takeTimeout(), multiplier, assertion);
        return this;
    }

    /**
     * Schedules a custom asynchronous operation once from the server thread and awaits completion.
     * Use the supplied session's queued methods for client work. Do not access Minecraft client state directly
     * in this callback. Composed futures remain available for specialized multi-action steps.
     */
    public ClientScenario async(String label, Function<ClientTest, ? extends CompletionStage<?>> action) {
        checkOpen();
        Objects.requireNonNull(action, "action");
        sequence.thenExecuteAsync(takeLabel(label), takeTimeout(), () -> action.apply(session));
        return this;
    }

    /** Like async, but retries completed assertion failures using the existing asynchronous wait semantics. */
    public ClientScenario awaitAsync(String label, Function<ClientTest, ? extends CompletionStage<?>> assertion) {
        checkOpen();
        Objects.requireNonNull(assertion, "assertion");
        sequence.thenWaitUntilAsync(takeLabel(label), takeTimeout(), () -> assertion.apply(session));
        return this;
    }

    /** Registers client-thread cleanup when this step executes. Server cleanup remains on GameTestHelper. */
    public ClientScenario afterTest(Consumer<ClientTest> cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        return client("register client cleanup", c -> c.afterTest(() -> cleanup.accept(c)));
    }

    /**
     * Returns the same server-owned sequence for existing START/END callbacks and other advanced steps.
     * Complete those registrations before returning to this fluent scenario. Pending overrides must be consumed first.
     */
    public GameTestSequence serverSequence() {
        checkOpen();
        checkNoPendingOptions();
        return sequence;
    }

    /** Appends the existing success step and closes this authoring chain. */
    public void succeed() {
        checkOpen();
        checkNoPendingOptions();
        sequence.thenSucceed();
        finished = true;
    }

    private String takeLabel(String fallback) {
        String label = nextLabel == null ? Objects.requireNonNull(fallback, "label") : nextLabel;
        nextLabel = null;
        return label;
    }

    private int takeTimeout() {
        int ticks = nextTimeout == null ? defaultTimeout : nextTimeout;
        nextTimeout = null;
        return ticks;
    }

    private void checkOpen() {
        if (finished) throw new IllegalStateException("Scenario is already complete");
    }

    private void checkNoPendingOptions() {
        if (nextLabel != null || nextTimeout != null) throw new IllegalStateException("Step options have no operation");
    }

    private static void validateTicks(int ticks) {
        if (ticks <= 0) throw new IllegalArgumentException("Tick budget must be positive");
    }

    /** A single drag configuration. Its terminal overFrames call registers the gesture in the scenario. */
    public final class DragStep {

        private final ClientTarget start;
        private Function<ClientTest, Point> endpoint;
        private int button;
        private boolean added;

        private DragStep(ClientTarget start) {
            this.start = start;
        }

        /** Overrides the default left button with an LWJGL mouse button index. */
        public DragStep button(int button) {
            checkMutable();
            this.button = button;
            return this;
        }

        /** Resolves an ordinary screen-coordinate endpoint once, immediately before the press. */
        public DragStep to(Function<ClientTest, Point> endpoint) {
            checkMutable();
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
            return this;
        }

        /** Sets a fixed endpoint in the active screen's GUI coordinates. */
        public DragStep to(int x, int y) {
            return to(c -> new Point(x, y));
        }

        /** Registers the drag across a positive number of rendered movement frames and returns the scenario. */
        public ClientScenario overFrames(int frames) {
            checkMutable();
            if (frames <= 0) throw new IllegalArgumentException("Drag frames must be positive");
            if (endpoint == null) throw new IllegalStateException("Drag endpoint is missing");
            added = true;
            return async(
                "drag " + start.description,
                c -> c.drag(button, start.description, start.resolver, endpoint, frames));
        }

        private void checkMutable() {
            checkOpen();
            if (added) throw new IllegalStateException("Drag step is already registered");
        }
    }
}
