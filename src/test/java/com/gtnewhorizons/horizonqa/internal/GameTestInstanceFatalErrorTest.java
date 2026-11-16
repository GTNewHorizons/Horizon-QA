package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;

public class GameTestInstanceFatalErrorTest {

    private static int cleanupRuns;

    @After
    public void resetExecution() {
        GameTestRunner.shutdown();
        cleanupRuns = 0;
    }

    @Test
    public void linkageErrorRunsCleanupReleasesOwnershipAndRethrows() throws Exception {
        GameTestInstance instance = instance();
        GameTestInstance sibling = instance();
        instance.start(null);
        sibling.start(null);
        instance.scheduleDelayed(1, () -> { throw new LinkageError("fatal linkage"); });
        GameTestRunner runner = new GameTestRunner();
        assertTrue(runner.tryStart(GameTestRunner.Kind.BATCH, () -> {
            runner.addInstance(instance);
            runner.addInstance(sibling);
        }));

        assertThrows(LinkageError.class, GameTestRunner::handleTickStart);

        assertEquals(2, cleanupRuns);
        assertEquals(GameTestStatus.ERROR, instance.getStatus());
        assertEquals(GameTestStatus.ERROR, sibling.getStatus());
        assertFalse(GameTestRunner.isBatchActive());
    }

    @Test
    public void assertionErrorRemainsAnOrdinaryTestFailure() throws Exception {
        GameTestInstance instance = instance();
        instance.start(null);
        instance.scheduleDelayed(1, () -> { throw new AssertionError("ordinary assertion"); });
        GameTestRunner runner = new GameTestRunner();
        assertTrue(runner.tryStart(GameTestRunner.Kind.BATCH, () -> runner.addInstance(instance)));

        GameTestRunner.handleTickStart();

        assertEquals(1, cleanupRuns);
        assertEquals(GameTestStatus.FAILED, instance.getStatus());
    }

    private static GameTestInstance instance() throws Exception {
        GameTestDefinition definition = new GameTestDefinition(
            "horizonqatest:Fatal.pending",
            GameTestInstanceFatalErrorTest.class.getMethod("pending", GameTestHelper.class),
            "",
            20,
            "",
            true,
            0);
        return new GameTestInstance(definition, 0, 0, 0);
    }

    public static void pending(GameTestHelper helper) {
        helper.afterTest(() -> cleanupRuns++);
    }
}
