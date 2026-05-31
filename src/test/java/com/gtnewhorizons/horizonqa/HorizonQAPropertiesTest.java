package com.gtnewhorizons.horizonqa;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HorizonQAPropertiesTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void resolvesRelativePathsAgainstServerWorkingDirectory() {
        File resolved = HorizonQAProperties.resolveServerFile(temporaryFolder.getRoot(), "reports/TEST-horizonqa.xml");

        Path expected = new File(temporaryFolder.getRoot(), "reports/TEST-horizonqa.xml").toPath()
            .toAbsolutePath()
            .normalize();
        assertEquals(expected, resolved.toPath());
    }

    @Test
    public void leavesAbsolutePathsAnchoredAtTheirOriginalRoot() throws Exception {
        File absolute = temporaryFolder.newFile("custom.xml")
            .getAbsoluteFile();

        File resolved = HorizonQAProperties.resolveServerFile(temporaryFolder.newFolder("server"), absolute.getPath());

        assertEquals(
            absolute.toPath()
                .toAbsolutePath()
                .normalize(),
            resolved.toPath());
    }
}
