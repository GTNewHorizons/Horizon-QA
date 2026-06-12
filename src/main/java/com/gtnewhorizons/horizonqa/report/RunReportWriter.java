package com.gtnewhorizons.horizonqa.report;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.horizonqa.HorizonQAProperties;

public final class RunReportWriter {

    private RunReportWriter() {}

    public static RunResult write(RunResult result, Logger log) {
        File reportFile = HorizonQAProperties.junitReportFile();
        try {
            JUnitXmlReporter.write(result, reportFile);
            log.info("JUnit XML report written to {}", reportFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write JUnit XML report: {}", e.getMessage());
            result = result.withAdditionalIssue(IssueResult.reporting("junit", reportFile.getAbsolutePath(), e));
        }

        File statusFile = HorizonQAProperties.statusReportFile();
        try {
            StatusJsonReporter.write(result, statusFile);
            log.info("Status JSON report written to {}", statusFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write status JSON report: {}", e.getMessage());
            result = result.withAdditionalIssue(IssueResult.reporting("status", statusFile.getAbsolutePath(), e));
        }
        ConsoleReporter.report(result);
        return result;
    }
}
