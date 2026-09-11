package com.gtnewhorizons.horizonqa.report;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.horizonqa.internal.FatalErrors;

public final class RunReportWriter {

    private RunReportWriter() {}

    public static RunResult write(RunResult result, File junitFile, File statusFile, Logger log) {
        result = writeConsole(result, log);
        result = writeTiming(result, junitFile, statusFile, log);

        try {
            StatusJsonReporter.write(result, statusFile);
            log.info("Status JSON report written to {}", path(statusFile));
        } catch (IOException | RuntimeException e) {
            log.error("Failed to write status JSON report: {}", e.getMessage());
            result = result.withAdditionalIssue(IssueResult.reporting("status", path(statusFile), e));
        } catch (Error e) {
            FatalErrors.rethrow(e);
            log.error("Failed to write status JSON report: {}", e.getMessage());
            result = result.withAdditionalIssue(IssueResult.reporting("status", path(statusFile), e));
        }

        try {
            JUnitXmlReporter.write(result, junitFile);
            log.info("JUnit XML report written to {}", path(junitFile));
        } catch (IOException | RuntimeException e) {
            log.error("Failed to write JUnit XML report: {}", e.getMessage());
            result = result.withAdditionalIssue(IssueResult.reporting("junit", path(junitFile), e));
        } catch (Error e) {
            FatalErrors.rethrow(e);
            log.error("Failed to write JUnit XML report: {}", e.getMessage());
            result = result.withAdditionalIssue(IssueResult.reporting("junit", path(junitFile), e));
        }
        return result;
    }

    private static RunResult writeTiming(RunResult result, File junitFile, File statusFile, Logger log) {
        File timingFile = null;
        try {
            timingFile = HtmlTimingReporter.outputFile(statusFile);
            File target = timingFile.getCanonicalFile();
            if (target.equals(statusFile.getCanonicalFile())
                || (junitFile != null && target.equals(junitFile.getCanonicalFile()))) {
                throw new IOException("Timing HTML must have a different path from status JSON and JUnit XML");
            }
            HtmlTimingReporter.write(result, timingFile);
            log.info("Timing HTML report written to {}", path(timingFile));
        } catch (IOException | RuntimeException e) {
            log.error("Failed to write timing HTML report: {}", e.getMessage());
            result = result.withAdditionalIssue(IssueResult.reporting("timing", path(timingFile), e));
        } catch (Error e) {
            FatalErrors.rethrow(e);
            log.error("Failed to write timing HTML report: {}", e.getMessage());
            result = result.withAdditionalIssue(IssueResult.reporting("timing", path(timingFile), e));
        }
        return result;
    }

    public static RunResult writeConsole(RunResult result, Logger log) {
        try {
            ConsoleReporter.report(result);
        } catch (RuntimeException e) {
            log.error("Failed to write console report: {}", e.getMessage());
            result = result.withAdditionalIssue(IssueResult.reporting("console", "", e));
        } catch (Error e) {
            FatalErrors.rethrow(e);
            log.error("Failed to write console report: {}", e.getMessage());
            result = result.withAdditionalIssue(IssueResult.reporting("console", "", e));
        }
        return result;
    }

    private static String path(File file) {
        return file == null ? "" : file.getAbsolutePath();
    }
}
