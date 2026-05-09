package com.gtnewhorizons.gametest.core;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.gametest.api.GameTestAssertException;
import com.gtnewhorizons.gametest.api.GameTestHelper;

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
    private BooleanSupplier succeedWhen;
    private final List<DelayedAction> delayedActions = new ArrayList<>();

    private int failX, failY, failZ;
    private boolean hasFailPosition;

    public GameTestInstance(GameTestDefinition definition, int originX, int originY, int originZ) {
        this.definition = definition;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

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

    public void tick() {
        if (status != GameTestStatus.RUNNING) return;
        tickCount++;

        if (succeedWhen != null) {
            try {
                if (succeedWhen.getAsBoolean()) {
                    succeed();
                    return;
                }
            } catch (Throwable t) {
                fail(t);
                return;
            }
        }

        if (tickCount > definition.getTimeoutTicks()) {
            if (succeedWhen != null) {
                fail("succeedWhen predicate did not return true within " + definition.getTimeoutTicks() + " ticks");
            } else {
                status = GameTestStatus.TIMED_OUT;
                LOG.warn("TIMEOUT  {} (timed out after {} ticks)", definition.getTestId(), tickCount);
            }
            return;
        }
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

    public void scheduleDelayed(int delayTicks, Runnable action) {
        delayedActions.add(new DelayedAction(tickCount + delayTicks, action));
    }

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

    public void setSequence(GameTestSequence seq) {
        this.sequence = seq;
    }

    public void setSucceedWhen(BooleanSupplier predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("succeedWhen predicate must not be null");
        }
        if (this.succeedWhen != null) {
            throw new IllegalStateException("succeedWhen has already been set on this test");
        }
        this.succeedWhen = predicate;
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

    private static final class DelayedAction {

        final int triggerTick;
        final Runnable action;

        DelayedAction(int triggerTick, Runnable action) {
            this.triggerTick = triggerTick;
            this.action = action;
        }
    }
}
