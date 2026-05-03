package com.gtnewhorizons.gametest.core;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.gametest.api.GameTestAssertException;
import com.gtnewhorizons.gametest.api.GameTestHelper;

/**
 * Runtime state of a single test execution. Transitions through
 * {@code NOT_STARTED → RUNNING → PASSED | FAILED | TIMED_OUT}.
 */
public class GameTestInstance {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private final GameTestDefinition definition;
    private final int originX;
    private final int originY;
    private final int originZ;

    private GameTestStatus status = GameTestStatus.NOT_STARTED;
    private int tickCount = 0;
    private Throwable failureCause;
    private GameTestSequence sequence;
    private final List<DelayedAction> delayedActions = new ArrayList<>();

    /** World-space coordinates of the block that triggered the failure (if applicable). */
    private int failX, failY, failZ;
    private boolean hasFailPosition;

    public GameTestInstance(GameTestDefinition definition, int originX, int originY, int originZ) {
        this.definition = definition;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    /**
     * Invoke the test method. If it sets up a {@link GameTestSequence}, the sequence will be ticked
     * each server tick until the test completes or times out.
     */
    public void start(WorldServer world) {
        status = GameTestStatus.RUNNING;
        GameTestHelper helper = new GameTestHelper(this, world, originX, originY, originZ);
        try {
            definition.getMethod()
                .invoke(null, helper);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            fail(cause != null ? cause : e);
        } catch (Exception e) {
            fail(e);
        }
    }

    /**
     * Called once per server tick while the instance is {@link GameTestStatus#RUNNING}. Enforces the
     * timeout and advances the sequence.
     */
    public void tick() {
        if (status != GameTestStatus.RUNNING) return;
        tickCount++;
        if (tickCount > definition.getTimeoutTicks()) {
            status = GameTestStatus.TIMED_OUT;
            LOG.warn("TIMEOUT  {} (timed out after {} ticks)", definition.getTestId(), tickCount);
            return;
        }
        // Process delayed actions (e.g. redstone pulses)
        Iterator<DelayedAction> it = delayedActions.iterator();
        while (it.hasNext()) {
            DelayedAction action = it.next();
            if (tickCount >= action.triggerTick) {
                try {
                    action.action.run();
                } catch (Throwable t) {
                    fail(t);
                    return;
                }
                it.remove();
            }
        }

        if (sequence != null) {
            try {
                sequence.tick(tickCount);
            } catch (GameTestAssertException e) {
                fail(e);
            } catch (Throwable t) {
                fail(t);
            }
        }
    }

    /**
     * Schedule an action to run after {@code delayTicks} from the current tick count.
     * Used by helpers like {@link com.gtnewhorizons.gametest.api.GameTestHelper#pulseRedstone}.
     */
    public void scheduleDelayed(int delayTicks, Runnable action) {
        delayedActions.add(new DelayedAction(tickCount + delayTicks, action));
    }

    // -------------------------------------------------------------------------
    // Outcome methods (called by GameTestHelper / GameTestSequence)
    // -------------------------------------------------------------------------

    public void succeed() {
        if (status != GameTestStatus.RUNNING) return;
        status = GameTestStatus.PASSED;
        LOG.info("PASSED   {}", definition.getTestId());
    }

    public void fail(String message) {
        fail(new GameTestAssertException(message, originX, originY, originZ));
    }

    public void fail(Throwable cause) {
        if (status != GameTestStatus.RUNNING) return;
        status = GameTestStatus.FAILED;
        failureCause = cause;
        if (cause instanceof GameTestAssertException) {
            GameTestAssertException gae = (GameTestAssertException) cause;
            failX = gae.getX();
            failY = gae.getY();
            failZ = gae.getZ();
            hasFailPosition = true;
        }
        String detail = cause != null ? cause.getMessage() : "unknown";
        LOG.error("FAILED   {} - {}", definition.getTestId(), detail);
        if (cause != null && !(cause instanceof GameTestAssertException)) {
            LOG.error("Caused by:", cause);
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public void setSequence(GameTestSequence seq) {
        this.sequence = seq;
    }

    public boolean isDone() {
        return status.isDone();
    }

    public GameTestStatus getStatus() {
        return status;
    }

    public GameTestDefinition getDefinition() {
        return definition;
    }

    public Throwable getFailureCause() {
        return failureCause;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public int getOriginZ() {
        return originZ;
    }

    public int getTickCount() {
        return tickCount;
    }

    public boolean hasFailPosition() {
        return hasFailPosition;
    }

    public int getFailX() {
        return failX;
    }

    public int getFailY() {
        return failY;
    }

    public int getFailZ() {
        return failZ;
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    private static final class DelayedAction {

        final int triggerTick;
        final Runnable action;

        DelayedAction(int triggerTick, Runnable action) {
            this.triggerTick = triggerTick;
            this.action = action;
        }
    }
}
