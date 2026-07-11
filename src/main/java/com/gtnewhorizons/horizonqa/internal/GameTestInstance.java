package com.gtnewhorizons.horizonqa.internal;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.GameTestAssertException;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;
import com.gtnewhorizons.horizonqa.api.LabelResolutionException;
import com.gtnewhorizons.horizonqa.api.TestIsolationViolation;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.event.AssertionFailed;
import com.gtnewhorizons.horizonqa.api.event.IsolationViolation;
import com.gtnewhorizons.horizonqa.api.event.TestFinished;
import com.gtnewhorizons.horizonqa.api.event.TestStarted;
import com.gtnewhorizons.horizonqa.structure.HybridStructureTemplate;
import com.gtnewhorizons.horizonqa.structure.StructureAnnotations;
import com.gtnewhorizons.horizonqa.structure.StructurePlacer;

public class GameTestInstance {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private final GameTestDefinition definition;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final StructureAnnotations annotations;
    private final int templateSizeX;
    private final int templateSizeZ;
    private final int rotation;

    private GameTestStatus status = GameTestStatus.NOT_STARTED;
    private int tickCount = 0;
    private Throwable failureCause;
    private Throwable cleanupFailureCause;
    private GameTestSequence sequence;
    private BooleanSupplier succeedWhen;
    private boolean succeedAtTimeout;
    private final List<Runnable> eachTickCallbacks = new ArrayList<>();
    private final List<DelayedAction> delayedActions = new ArrayList<>();
    private final List<Runnable> cleanupCallbacks = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final TestEventRecorder recorder = new TestEventRecorder();

    private int failX, failY, failZ;
    private boolean hasFailPosition;

    public GameTestInstance(GameTestDefinition definition, int originX, int originY, int originZ) {
        this(definition, originX, originY, originZ, null);
    }

    public GameTestInstance(GameTestDefinition definition, int originX, int originY, int originZ,
        HybridStructureTemplate template) {
        this.definition = definition;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.annotations = template != null ? template.getAnnotations() : StructureAnnotations.EMPTY;
        this.templateSizeX = template != null ? template.getSizeX() : 0;
        this.templateSizeZ = template != null ? template.getSizeZ() : 0;
        this.rotation = definition != null ? definition.getRotation() : 0;
    }

    public void start(WorldServer world) {
        status = GameTestStatus.RUNNING;
        GameTestHelper helper = new GameTestHelper(this, world, originX, originY, originZ);
        recorder.record(
            () -> new TestStarted(
                recorder.clock()
                    .tick(),
                definition.getTestId(),
                new TestPos(originX, originY, originZ)));
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

    public void tickStart() {
        if (status != GameTestStatus.RUNNING) return;
        tickCount++;
        recorder.clock()
            .advance();

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
                sequence.tick(tickCount, TestPhase.START);
            } catch (Throwable t) {
                fail(t);
            }
        }
    }

    public void tickEnd() {
        if (status != GameTestStatus.RUNNING) return;

        for (Runnable callback : eachTickCallbacks) {
            try {
                callback.run();
            } catch (Throwable t) {
                fail(t);
                return;
            }
        }

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

        if (sequence != null) {
            try {
                sequence.tick(tickCount, TestPhase.END);
            } catch (Throwable t) {
                fail(t);
                return;
            }
        }
        if (status != GameTestStatus.RUNNING) return;

        if (tickCount >= definition.getTimeoutTicks()) {
            if (succeedAtTimeout) {
                succeed();
            } else if (succeedWhen != null) {
                fail("succeedWhen predicate did not return true within " + definition.getTimeoutTicks() + " ticks");
            } else {
                timeout();
            }
        }
    }

    public void scheduleDelayed(int delayTicks, Runnable action) {
        delayedActions.add(new DelayedAction(tickCount + delayTicks, action));
    }

    public void succeed() {
        if (status != GameTestStatus.RUNNING) return;
        status = GameTestStatus.PASSED;
        runCleanup();
        recordFinished();
        if (status == GameTestStatus.PASSED) {
            LOG.info("PASSED   {}", definition.getTestId());
        }
    }

    public void fail(String message) {
        fail(new GameTestAssertException(message, originX, originY, originZ));
    }

    public void fail(Throwable cause) {
        if (status != GameTestStatus.RUNNING) return;
        status = cause instanceof GameTestInfrastructureException ? GameTestStatus.ERROR : GameTestStatus.FAILED;
        failureCause = cause;
        if (cause instanceof GameTestAssertException gae && gae.hasPosition()) {
            failX = gae.getX();
            failY = gae.getY();
            failZ = gae.getZ();
            hasFailPosition = true;
        }
        final Throwable c = cause;
        recorder.record(() -> {
            String msg = c != null ? String.valueOf(c.getMessage()) : "unknown";
            String type = c != null ? c.getClass()
                .getName() : "java.lang.AssertionError";
            TestPos pos = hasFailPosition ? new TestPos(failX, failY, failZ) : null;
            return new AssertionFailed(
                recorder.clock()
                    .tick(),
                msg,
                type,
                pos);
        });
        String detail = cause != null ? cause.getMessage() : "unknown";
        LOG.error("{}   {} - {}", status == GameTestStatus.ERROR ? "ERROR " : "FAILED", definition.getTestId(), detail);
        if (cause != null && !(cause instanceof GameTestAssertException)) {
            LOG.error("Caused by:", cause);
        }
        runCleanup();
        recordFinished();
    }

    private void timeout() {
        if (status != GameTestStatus.RUNNING) return;
        String message = "Timed out after " + tickCount + " ticks";
        AssertionError lastAssertion = null;
        if (sequence != null) {
            message += ". " + sequence.describeActiveStep(tickCount);
            GameTestSequence.SequenceStepSnapshot activeStep = sequence.getActiveStep();
            if (activeStep != null) lastAssertion = activeStep.lastAssertion();
        }
        failureCause = new GameTestTimeoutException(message, lastAssertion);
        status = GameTestStatus.TIMED_OUT;
        LOG.warn("TIMEOUT  {} - {}", definition.getTestId(), message);
        runCleanup();
        recordFinished();
    }

    public void addCleanup(Runnable callback) {
        if (callback == null) throw new IllegalArgumentException("cleanup callback must not be null");
        cleanupCallbacks.add(callback);
    }

    public void addWarning(String message) {
        if (message != null) warnings.add(message);
    }

    public List<String> getWarnings() {
        return warnings;
    }

    private void runCleanup() {
        Throwable cleanupFailure = null;
        for (Runnable cb : cleanupCallbacks) {
            try {
                cb.run();
            } catch (TestIsolationViolation e) {
                cleanupFailure = appendCleanupFailure(cleanupFailure, e);
                recordIsolationViolation(e);
                LOG.error("Exception in cleanup callback for {}: {}", definition.getTestId(), e.getMessage(), e);
            } catch (Throwable t) {
                cleanupFailure = appendCleanupFailure(cleanupFailure, t);
                LOG.error("Exception in cleanup callback for {}: {}", definition.getTestId(), t.getMessage(), t);
            }
        }
        cleanupCallbacks.clear();
        if (cleanupFailure != null) {
            cleanupFailureCause = cleanupFailure;
            status = GameTestStatus.ERROR;
        }
    }

    private static Throwable appendCleanupFailure(Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (next != null && next != first) {
            first.addSuppressed(next);
        }
        return first;
    }

    private void recordIsolationViolation(TestIsolationViolation violation) {
        final TestIsolationViolation iv = violation;
        recorder.record(
            () -> new IsolationViolation(
                recorder.clock()
                    .tick(),
                iv.getClass()
                    .getSimpleName(),
                null,
                iv.getMessage()));
        LOG.error("ISOLATION {} - {}", definition.getTestId(), violation.getMessage());
    }

    private void recordFinished() {
        recorder.record(
            () -> new TestFinished(
                recorder.clock()
                    .tick(),
                definition.getTestId(),
                finishedStatusName(),
                recorder.clock()
                    .tick()));
    }

    private String finishedStatusName() {
        return switch (status) {
            case PASSED -> "passed";
            case FAILED -> "failed";
            case TIMED_OUT -> "timed out";
            case ERROR -> "error";
            default -> status.name()
                .toLowerCase();
        };
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

    public void setSucceedAtTimeout() {
        succeedAtTimeout = true;
    }

    public void addEachTickCallback(Runnable callback) {
        if (callback == null) {
            throw new IllegalArgumentException("onEachTick callback must not be null");
        }
        eachTickCallbacks.add(callback);
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

    public Throwable getCleanupFailureCause() {
        return cleanupFailureCause;
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

    public TestEventRecorder getRecorder() {
        return recorder;
    }

    public TestPos resolveLabel(String label) {
        TestPos pos = annotations.get(label);
        if (pos == null) {
            String templateName = definition != null ? definition.getTemplateName() : "<unknown>";
            String testId = definition != null ? definition.getTestId() : "<unknown>";
            throw new LabelResolutionException(
                "Unknown label '" + label
                    + "' in template '"
                    + templateName
                    + "' for test '"
                    + testId
                    + "'; available: "
                    + annotations.availableLabels());
        }
        int rx = StructurePlacer.rotatedLocalX(pos.x(), pos.z(), templateSizeX, templateSizeZ, rotation);
        int rz = StructurePlacer.rotatedLocalZ(pos.x(), pos.z(), templateSizeX, templateSizeZ, rotation);
        return new TestPos(rx, pos.y(), rz);
    }

    @Desugar
    private record DelayedAction(int triggerTick, Runnable action) {

    }
}
