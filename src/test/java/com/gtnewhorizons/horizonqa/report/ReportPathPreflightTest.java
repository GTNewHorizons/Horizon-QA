package com.gtnewhorizons.horizonqa.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReportPathPreflightTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createsParentsAndDeletesSentinelFiles() {
        File parent = new File(temporaryFolder.getRoot(), "reports/nested");
        File junit = new File(parent, "TEST-horizonqa.xml");
        File status = new File(parent, "horizonqa-result.json");

        List<IssueResult> issues = ReportPathPreflight.check(junit, status);

        assertTrue(issues.isEmpty());
        assertTrue(parent.isDirectory());
        File[] sentinels = parent.listFiles((dir, name) -> name.startsWith(".horizonqa-preflight-"));
        assertEquals(0, sentinels == null ? 0 : sentinels.length);
    }

    @Test
    public void rejectsSameJunitAndStatusPath() throws Exception {
        File output = temporaryFolder.newFile("same-output.xml");

        List<IssueResult> issues = ReportPathPreflight.check(output, output);

        assertEquals(1, issues.size());
        assertEquals(
            "reportPath:sameOutput",
            issues.get(0)
                .id());
        assertEquals(
            "REPORT_PATH_ERROR",
            issues.get(0)
                .kind());
    }

    @Test
    public void rejectsTargetDirectories() throws Exception {
        File junit = temporaryFolder.newFolder("junit-directory");
        File status = new File(temporaryFolder.getRoot(), "status.json");

        List<IssueResult> issues = ReportPathPreflight.check(junit, status);

        assertEquals(1, issues.size());
        assertEquals(
            "reportPath:junit:targetDirectory",
            issues.get(0)
                .id());
    }
}
