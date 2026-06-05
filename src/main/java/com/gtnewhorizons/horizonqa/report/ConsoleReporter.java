package com.gtnewhorizons.horizonqa.report;

import java.util.List;
import java.util.Locale;

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
        LOG.info("  passed: {}", result.passed());
        LOG.info("  required failed: {}", result.requiredFailed());
        LOG.info("  required timed out: {}", result.requiredTimedOut());
        LOG.info("  optional failed: {}", result.optionalFailed());
        LOG.info("  optional timed out: {}", result.optionalTimedOut());
        LOG.info("  skipped by setup: {}", result.skippedBySetup());
        LOG.info("  infrastructure errors: {}", result.infrastructureErrors());
        LOG.info("=======================================================");
        LOG.info(summaryLine(result));
        if (result.passedRun()) {
            LOG.info(runLine(result));
        } else {
            LOG.error(runLine(result));
        }
    }

    static String summaryLine(RunResult result) {
        return "HorizonQA RESULT status=" + statusToken(result)
            + " exitCode="
            + result.exitCode()
            + " mode="
            + result.mode()
            + " passed="
            + result.passed()
            + " requiredFailed="
            + result.requiredFailed()
            + " requiredTimedOut="
            + result.requiredTimedOut()
            + " optionalFailed="
            + result.optionalFailed()
            + " optionalTimedOut="
            + result.optionalTimedOut()
            + " skippedBySetup="
            + result.skippedBySetup()
            + " infrastructureErrors="
            + result.infrastructureErrors();
    }

    static String runLine(RunResult result) {
        return "RUN " + statusToken(result);
    }

    private static String statusToken(RunResult result) {
        return result.status()
            .toUpperCase(Locale.ROOT);
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
