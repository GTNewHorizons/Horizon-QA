package com.gtnewhorizons.horizonqa.report;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.horizonqa.api.event.TestEvent;
import com.gtnewhorizons.horizonqa.internal.GameTestInstance;
import com.gtnewhorizons.horizonqa.internal.GameTestStatus;

public final class ConsoleReporter {

    private static final Logger LOG = LogManager.getLogger("GameTest");

    private ConsoleReporter() {}

    private static final int EVENT_TAIL_LINES = 20;

    public static void report(List<GameTestInstance> instances) {
        int passed = 0, failed = 0, timedOut = 0, other = 0;
        for (GameTestInstance inst : instances) {
            switch (inst.getStatus()) {
                case PASSED:
                    passed++;
                    break;
                case FAILED:
                    failed++;
                    break;
                case TIMED_OUT:
                    timedOut++;
                    break;
                default:
                    other++;
                    break;
            }
        }

        LOG.info("=======================================================");
        LOG.info("  GameTest Results");
        LOG.info("-------------------------------------------------------");
        for (GameTestInstance inst : instances) {
            String id = inst.getDefinition()
                .getTestId();
            GameTestStatus status = inst.getStatus();
            switch (status) {
                case PASSED:
                    LOG.info("  [PASS] {}", id);
                    break;
                case FAILED: {
                    Throwable cause = inst.getFailureCause();
                    String detail = cause != null ? cause.getMessage() : "unknown failure";
                    LOG.error("  [FAIL] {} — {}", id, detail);
                    dumpEventTail(inst);
                    break;
                }
                case TIMED_OUT:
                    LOG.error("  [TIME] {} (timed out after {} ticks)", id, inst.getTickCount());
                    dumpEventTail(inst);
                    break;
                default:
                    LOG.warn("  [SKIP] {} (did not complete, status: {})", id, status);
                    break;
            }
        }
        LOG.info("-------------------------------------------------------");
        LOG.info("  {} passed  {} failed  {} timed out", passed, failed, timedOut);
        if (other > 0) {
            LOG.warn("  {} test(s) did not complete", other);
        }
        if (failed + timedOut == 0) {
            LOG.info("  RUN PASSED");
        } else {
            LOG.error("  RUN FAILED");
        }
        LOG.info("=======================================================");
    }

    private static void dumpEventTail(GameTestInstance inst) {
        List<TestEvent> events = inst.getRecorder()
            .snapshot();
        if (events.isEmpty()) return;
        int from = Math.max(0, events.size() - EVENT_TAIL_LINES);
        if (from > 0) {
            LOG.error("         (showing last {} of {} events)", events.size() - from, events.size());
        }
        for (int i = from; i < events.size(); i++) {
            TestEvent e = events.get(i);
            LOG.error("         [t={}] [{}] {}", e.tick(), e.category(), e.summary());
        }
    }
}
