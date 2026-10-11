package com.gtnewhorizons.horizonqa.client;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.gtnewhorizons.horizonqa.client.ClientTaskQueue.Phase;
import com.gtnewhorizons.horizonqa.internal.FatalErrors;
import com.gtnewhorizons.horizonqa.internal.ReportedRun;
import com.gtnewhorizons.horizonqa.report.IssueResult;
import com.gtnewhorizons.horizonqa.report.RunReportWriter;
import com.gtnewhorizons.horizonqa.report.RunResult;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Waits for a launcher-owned integrated world; all Minecraft client state stays on the client thread. */
public final class ClientTestRuntime {

    private final Runnable startTests;
    private long started;
    private World world;
    private volatile boolean ready;
    private volatile boolean startedTests;
    private final AtomicBoolean lifecycleClaimed = new AtomicBoolean();
    @SuppressWarnings("java:S3077")
    private volatile RunResult finished;
    @SuppressWarnings("java:S3077")
    private volatile IssueResult runtimeFailure;
    private CompletableFuture<Void> shutdown;
    private long shutdownStarted;
    private boolean previousPauseOnLostFocus;
    private boolean settingsCaptured;

    public ClientTestRuntime(Runnable startTests) {
        this.startTests = startTests;
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            ClientTest.tick(Phase.START);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (started == 0) {
            started = System.nanoTime();
            previousPauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
            settingsCaptured = true;
            mc.gameSettings.pauseOnLostFocus = false;
            if (!HorizonQAProperties.isCi() || !HorizonQAProperties.autoRunTests()
                || !HorizonQAProperties.stopServerAfterRun()
                || HorizonQAProperties.usesVoidWorld()
                || HorizonQAProperties.turboMultiplier() != 1) {
                fail(
                    "CLIENT_CONFIG",
                    "Client scenarios require mode=ci, autoRun=true, stopServer=true, world=normal, turbo=1");
            }
        }
        if (finished == null && new java.io.File(
            HorizonQAProperties.junitReportFile()
                .getParentFile(),
            "stop-client").isFile()) {
            fail("CLIENT_CANCELLED", "The owning launcher requested cancellation");
        }
        if (finished == null && ready && mc.theWorld != world)
            fail("CLIENT_WORLD_LOST", "Client disconnected or its world unloaded");
        if (finished == null && !ready && System.nanoTime() - started > TimeUnit.MINUTES.toNanos(10)) {
            fail("CLIENT_STARTUP", "No ready integrated world with a player and rendered frame within 600 seconds");
        }
        ClientTest.tick(Phase.END);
        if (runtimeFailure != null && finished == null && startedTests)
            ReportedRun.requestAbort(runtimeFailure.message());
        if (finished != null) shutdownClient(mc);
    }

    /** First ordinary world frame publishes readiness; no bootstrap time consumes test budgets. */
    public void completedFrame() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!ready && finished == null
            && mc.thePlayer != null
            && mc.theWorld != null
            && mc.getIntegratedServer() != null
            && mc.currentScreen == null) {
            world = mc.theWorld;
            ready = true;
        }
        ClientTest.tick(Phase.FRAME);
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ready
            && finished == null
            && lifecycleClaimed.compareAndSet(false, true)) {
            startTests.run();
            startedTests = true;
        }
    }

    public void finish(RunResult result) {
        if (finished == null) finished = result;
    }

    /** Called on the client thread by the loop boundary. Real game failures always abort as infrastructure. */
    public void crashed(Throwable error) {
        ClientTest.crashed(error);
        fail("CLIENT_CRASH", "Client loop crashed: " + error, error);
        FatalErrors.rethrow(error);
        Minecraft.getMinecraft()
            .displayGuiScreen(null);
    }

    private void fail(String kind, String message) {
        fail(kind, message, null);
    }

    private void fail(String kind, String message, Throwable cause) {
        if (runtimeFailure == null) {
            StringWriter trace = new StringWriter();
            if (cause != null) cause.printStackTrace(new PrintWriter(trace));
            runtimeFailure = new IssueResult(
                "client:runtime",
                kind,
                "horizonqa.client",
                "runtime",
                message,
                message,
                true,
                trace.toString());
            HorizonQAMod.LOG.error(message);
        }
        if (finished == null && ReportedRun.requestAbort(message)) return;
        if (finished == null && lifecycleClaimed.compareAndSet(false, true)) finish(
            RunReportWriter.write(
                RunResult.preRun(
                    HorizonQAProperties.modeName(),
                    Collections.singletonList(runtimeFailure),
                    HorizonQAProperties.junitReportFile()
                        .getPath()),
                HorizonQAProperties.junitReportFile(),
                HorizonQAProperties.statusReportFile(),
                HorizonQAMod.LOG));
    }

    private void shutdownClient(Minecraft mc) {
        if (shutdown == null) {
            shutdown = ClientTest.closeActive();
            shutdownStarted = System.nanoTime();
        }
        if (!shutdown.isDone() && System.nanoTime() - shutdownStarted < TimeUnit.SECONDS.toNanos(5)) return;
        if (!shutdown.isDone()) {
            fail("CLIENT_SHUTDOWN", "Client cleanup did not settle within the shutdown budget");
            ClientTest.interruptCleanup();
        }
        if (runtimeFailure != null) {
            finished = RunReportWriter.write(
                finished.withAdditionalIssue(runtimeFailure),
                HorizonQAProperties.junitReportFile(),
                HorizonQAProperties.statusReportFile(),
                HorizonQAMod.LOG);
        }
        if (settingsCaptured) mc.gameSettings.pauseOnLostFocus = previousPauseOnLostFocus;
        mc.loadWorld(null);
        mc.shutdown();
        FMLCommonHandler.instance()
            .exitJava(finished.exitCode(), false);
    }
}
