package com.gtnewhorizons.horizonqa.report;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ConsoleReporter {

    private static final Logger LOG = LogManager.getLogger("GameTest");
    private static final int EVENT_TAIL_LINES = 20;

    private ConsoleReporter() {}

    public static void report(RunResult result) {
        LOG.info("=======================================================");
        LOG.info("  GameTest Results");
        LOG.info("-------------------------------------------------------");

        if (!result.issues()
            .isEmpty()) {
            LOG.error("  Diagnostics");
            for (IssueResult issue : result.issues()) {
                LOG.error("  [ISSUE] {} - {}", issue.name(), issue.message());
            }
            LOG.info("-------------------------------------------------------");
        }

        for (CaseResult resultCase : result.cases()) {
            switch (resultCase.status()) {
                case PASSED:
                    LOG.info("  [PASS] {}", resultCase.id());
                    break;
                case FAILED:
                    LOG.error("  [FAIL] {} - {}", resultCase.id(), detail(resultCase));
                    dumpOutputTail(resultCase);
                    break;
                case TIMED_OUT:
                    LOG.error("  [TIME] {} (timed out after {} ticks)", resultCase.id(), resultCase.tickCount());
                    dumpOutputTail(resultCase);
                    break;
                case ERROR:
                    LOG.error("  [ERROR] {} - {}", resultCase.id(), detail(resultCase));
                    dumpOutputTail(resultCase);
                    break;
                default:
                    if (hasText(resultCase.blockedByIssueId())) {
                        LOG.warn("  [SKIP] {} (blocked by {})", resultCase.id(), resultCase.blockedByIssueId());
                    } else {
                        LOG.warn("  [SKIP] {} (did not complete, status: {})", resultCase.id(), resultCase.status());
                    }
                    break;
            }
        }

        LOG.info("-------------------------------------------------------");
        LOG.info("  {} passed  {} failed  {} timed out", result.passed(), result.failed(), result.timedOut());
        if (result.incomplete() > 0) {
            LOG.warn("  {} test(s) did not complete", result.incomplete());
        }
        long caseInfrastructureErrors = result.infrastructureErrors() - result.issues()
            .size();
        if (caseInfrastructureErrors > 0) {
            LOG.error("  {} test infrastructure error(s)", caseInfrastructureErrors);
        }
        if (result.diagnosticErrors() > 0) {
            LOG.error("  {} diagnostic error(s)", result.diagnosticErrors());
        }
        if (result.optionalFailures() > 0) {
            LOG.warn("  {} optional test failure(s)", result.optionalFailures());
        }
        if (result.passedRun()) {
            LOG.info("  RUN PASSED");
        } else {
            LOG.error("  RUN FAILED");
        }
        LOG.info("=======================================================");
    }

    private static void dumpOutputTail(CaseResult resultCase) {
        List<String> lines = resultCase.outputLines();
        if (lines.isEmpty()) return;

        int from = Math.max(0, lines.size() - EVENT_TAIL_LINES);
        if (from > 0) {
            LOG.error("         (showing last {} of {} output lines)", lines.size() - from, lines.size());
        }
        for (int i = from; i < lines.size(); i++) {
            LOG.error("         {}", lines.get(i));
        }
    }

    private static String detail(CaseResult resultCase) {
        String message = resultCase.failureMessage();
        return message == null || message.isEmpty() ? "unknown failure" : message;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }
}
