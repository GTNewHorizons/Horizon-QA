package com.gtnewhorizons.horizonqa.examples.tests;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.AfterBatch;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Select all three exact IDs together. The deadline must retain one passed, one aborted and one unstarted case. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientCancellationTests {

    private static final String SELECTION = "horizonqaexamples:ClientCancellationTests.aCompleted,"
        + "horizonqaexamples:ClientCancellationTests.bInterrupted,"
        + "horizonqaexamples:ClientCancellationTests.cNotStarted";
    private static final AtomicInteger CLEANUP_PHASE = new AtomicInteger();

    private ClientCancellationTests() {}

    @GameTest(template = "client_smoke", batch = "client_cancellation", timeoutTicks = 400)
    public static void aCompleted(GameTestHelper helper) {
        requireExplicitSelection(helper);
        ClientTest.scenario(helper)
            .succeed();
    }

    @GameTest(template = "client_smoke", batch = "client_cancellation", timeoutTicks = 400)
    public static void bInterrupted(GameTestHelper helper) {
        requireExplicitSelection(helper);
        helper.afterTest(() -> {
            if (!CLEANUP_PHASE.compareAndSet(1, 2)) throw new AssertionError("Server cleanup preceded client cleanup");
            helper.recordDiagnostic("cancellationServerCleanup=afterClientCleanup");
        });
        ClientTest.scenario(helper)
            .afterTest(
                client -> {
                    if (!CLEANUP_PHASE.compareAndSet(0, 1))
                        throw new AssertionError("Client cleanup ran more than once");
                })
            .client("request external deadline", client -> {
                try {
                    Files.createFile(
                        HorizonQAProperties.junitReportFile()
                            .getAbsoluteFile()
                            .getParentFile()
                            .toPath()
                            .resolve("stop-client"));
                } catch (IOException error) {
                    throw new UncheckedIOException(error);
                }
            })
            .awaitClient("await cancellation", client -> { throw new AssertionError("Waiting for external deadline"); })
            .succeed();
    }

    @GameTest(template = "client_smoke", batch = "client_cancellation", timeoutTicks = 400)
    public static void cNotStarted(GameTestHelper helper) {
        requireExplicitSelection(helper);
        throw new AssertionError("Cancellation allowed another test to start");
    }

    @AfterBatch("client_cancellation")
    public static void afterCancellation() {
        if (!SELECTION.equals(System.getProperty("horizonqa.tests"))) return;
        int phase = CLEANUP_PHASE.getAndSet(0);
        if (phase != 2) throw new AssertionError("Batch cleanup preceded server cleanup");
        HorizonQAMod.LOG.info("CANCELLATION batch cleanup followed client and server cleanup");
    }

    private static void requireExplicitSelection(GameTestHelper helper) {
        helper.assumeTrue(
            SELECTION.equals(System.getProperty("horizonqa.tests")),
            "Select all cancellation cases explicitly");
    }
}
