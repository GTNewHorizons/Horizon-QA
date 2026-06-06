package com.gtnewhorizons.horizonqa.report;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collections;

import org.junit.Test;

public class StatusJsonReporterTest {

    @Test
    public void statusJsonUsesStableTopLevelAndFailureShape() {
        RunResult result = RunResult.completedCases(
            "ci",
            Collections.singletonList(
                new CaseResult(
                    "mod:Suite.fails",
                    "mod:Suite",
                    "fails",
                    CaseResult.Status.FAILED,
                    true,
                    40,
                    2.0,
                    "bad \"json\"\nline",
                    "java.lang.AssertionError",
                    "trace\nline",
                    Collections.emptyList())),
            Collections.emptyList(),
            "reports/TEST.xml");

        File statusFile = new File("reports/status.json");
        String json = StatusJsonReporter.toJson(result, statusFile);
        String escapedStatusPath = statusFile.getPath()
            .replace("\\", "\\\\");

        assertContainsInOrder(
            json,
            "{\n",
            "  \"schemaVersion\": 1",
            "  \"status\": \"failed\"",
            "  \"exitCode\": 1",
            "  \"configuration\": {",
            "  \"counts\": {",
            "    \"selectedTests\": 1",
            "    \"requiredFailures\": 1",
            "    \"junitFailures\": 1",
            "  \"reports\": {",
            "    \"junit\": \"reports/TEST.xml\"",
            "    \"status\": \"" + escapedStatusPath + "\"",
            "  \"issues\": []",
            "  \"tests\": [",
            "      \"id\": \"mod:Suite.fails\"",
            "      \"required\": true",
            "      \"failure\": {",
            "        \"message\": \"bad \\\"json\\\"\\nline\"",
            "        \"type\": \"java.lang.AssertionError\"",
            "        \"stackTrace\": \"trace\\nline\"",
            "\n}\n");
    }

    private static void assertContainsInOrder(String text, String... parts) {
        int offset = 0;
        for (String part : parts) {
            int next = text.indexOf(part, offset);
            assertTrue("Missing JSON fragment after offset " + offset + ": " + part, next >= 0);
            offset = next + part.length();
        }
    }
}
