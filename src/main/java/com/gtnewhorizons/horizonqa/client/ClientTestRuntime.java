package com.gtnewhorizons.horizonqa.client;

import java.io.File;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import org.lwjgl.opengl.Display;

import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.gtnewhorizons.horizonqa.internal.ReportedRun;
import com.gtnewhorizons.horizonqa.report.IssueResult;
import com.gtnewhorizons.horizonqa.report.RunReportWriter;
import com.gtnewhorizons.horizonqa.report.RunResult;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Bootstraps a unique scratch save and coordinates the two game threads without blocking either on test work. */
public final class ClientTestRuntime {

    private final Runnable startTests;
    private long started;
    private volatile boolean ready;
    private boolean launched;
    private boolean startedTests;
    // Publishes a completed immutable report from the server thread to client shutdown.
    @SuppressWarnings("java:S3077")
    private volatile RunResult finished;
    private CompletableFuture<Void> shutdown;
    private int originalGuiScale;
    private boolean settingsChanged;
    private String originalTitle;
    private String displayedProgress;

    public ClientTestRuntime(Runnable startTests) {
        this.startTests = startTests;
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ClientTest.tick(ClientTaskQueue.Phase.START);
            return;
        }
        if (started == 0) started = System.nanoTime();
        updateProgressTitle();
        Minecraft mc = Minecraft.getMinecraft();
        File stop = new File(
            HorizonQAProperties.junitReportFile()
                .getAbsoluteFile()
                .getParentFile(),
            "stop-client");
        if (finished == null && stop.isFile()) failStartup("External wall-clock deadline exceeded");
        if (finished != null) {
            shutdownClient(mc);
            return;
        }
        if (!launched && mc.currentScreen instanceof GuiMainMenu) {
            launched = true;
            launchScratchWorld(mc);
            if (finished != null) return;
        }
        if (!ready && mc.thePlayer != null && mc.theWorld != null && mc.getIntegratedServer() != null) {
            ready = true;
            mc.displayGuiScreen(null);
        }
        if (!ready && System.nanoTime() - started > TimeUnit.SECONDS.toNanos(120)) {
            failStartup("Integrated client/player did not become ready within 120 seconds");
        }
        ClientTest.tick(ClientTaskQueue.Phase.END);
    }

    private void launchScratchWorld(Minecraft mc) {
        if (!HorizonQAProperties.isCi() || !HorizonQAProperties.autoRunTests()
            || !HorizonQAProperties.stopServerAfterRun()
            || HorizonQAProperties.usesVoidWorld()
            || HorizonQAProperties.turboMultiplier() != 1) {
            failStartup("Client tests require CI mode, world=normal, autoRun=true, stopServer=true and turbo=1");
            return;
        }
        String save = "horizonqa-" + UUID.randomUUID();
        File directory = new File(mc.mcDataDir, "saves/" + save);
        if (directory.exists()) {
            failStartup("Scratch save unexpectedly exists: " + directory);
            return;
        }
        originalGuiScale = mc.gameSettings.guiScale;
        settingsChanged = true;
        mc.gameSettings.guiScale = 1;
        HorizonQAMod.LOG.info("CLIENT_TEST scratchWorld={}", directory.getAbsolutePath());
        WorldSettings settings = new WorldSettings(0L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT);
        settings.enableCommands();
        mc.launchIntegratedServer(save, save, settings);
    }

    private void updateProgressTitle() {
        String progress = ReportedRun.progressText();
        if (progress != null) {
            if (originalTitle == null) originalTitle = Display.getTitle();
            if (!progress.equals(displayedProgress)) {
                Display.setTitle(progress);
                displayedProgress = progress;
            }
        } else if (originalTitle != null) {
            restoreProgressTitle();
        }
    }

    private void restoreProgressTitle() {
        if (originalTitle == null) return;
        Display.setTitle(originalTitle);
        originalTitle = null;
        displayedProgress = null;
    }

    private void shutdownClient(Minecraft mc) {
        restoreProgressTitle();
        if (shutdown == null) shutdown = ClientTest.closeActive();
        if (!shutdown.isDone()) return;
        mc.loadWorld(null);
        if (settingsChanged) {
            mc.gameSettings.guiScale = originalGuiScale;
            mc.gameSettings.saveOptions();
        }
        mc.shutdown();
        FMLCommonHandler.instance()
            .exitJava(finished.exitCode(), false);
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ready && !startedTests) {
            startedTests = true;
            startTests.run();
        }
    }

    public void finish(RunResult result) {
        if (finished == null) finished = result;
    }

    private void failStartup(String message) {
        IssueResult issue = new IssueResult(
            "client:startup",
            "CLIENT_STARTUP",
            "horizonqa.client",
            "startup",
            message,
            message,
            true);
        finished = RunReportWriter.write(
            RunResult.preRun(
                HorizonQAProperties.modeName(),
                Collections.singletonList(issue),
                HorizonQAProperties.junitReportFile()
                    .getPath()),
            HorizonQAProperties.junitReportFile(),
            HorizonQAProperties.statusReportFile(),
            HorizonQAMod.LOG);
    }
}
