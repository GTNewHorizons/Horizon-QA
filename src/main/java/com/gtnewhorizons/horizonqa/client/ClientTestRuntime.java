package com.gtnewhorizons.horizonqa.client;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.EntityLiving;
import net.minecraft.network.NetworkManager;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.lwjgl.opengl.Display;

import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.gtnewhorizons.horizonqa.internal.ReportedRun;
import com.gtnewhorizons.horizonqa.report.IssueResult;
import com.gtnewhorizons.horizonqa.report.RunReportWriter;
import com.gtnewhorizons.horizonqa.report.RunResult;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoaderState;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.netty.channel.Channel;

/** Bootstraps a unique scratch save and coordinates the two game threads without blocking either on test work */
public final class ClientTestRuntime {

    private static volatile boolean running = HorizonQAProperties.clientTestsEnabled();
    private static volatile boolean soundEnabled = HorizonQAProperties.clientSoundEnabled();
    private static boolean mobsEnabled;
    // Client-thread diagnostic state, released after client cleanup has drained
    private static NetworkManager startupConnection;
    private final Runnable startTests;
    private long started;
    private volatile boolean ready;
    private boolean launched;
    private volatile boolean startedTests;
    // Publishes a completed immutable report from the server thread to client shutdown
    @SuppressWarnings("java:S3077")
    private volatile RunResult finished;
    private CompletableFuture<Void> shutdown;
    private long shutdownStarted;
    private int originalGuiScale;
    private boolean settingsChanged;
    private String originalTitle;
    private String displayedProgress;

    public ClientTestRuntime(Runnable startTests) {
        this.startTests = startTests;
        soundEnabled = HorizonQAProperties.clientSoundEnabled();
        mobsEnabled = false;
        running = true;
    }

    /** Effective client audio level. The configured value remains available to options persistence */
    public static float soundLevel(SoundCategory category, float configuredLevel) {
        return running && !soundEnabled ? 0.0F : configuredLevel;
    }

    /** Internal client-thread override. Saved volume settings remain unchanged */
    public static void setSoundEnabled(boolean enabled) {
        soundEnabled = enabled;
        Minecraft mc = Minecraft.getMinecraft();
        if (!enabled) mc.getSoundHandler()
            .stopSounds();
        mc.getSoundHandler()
            .setSoundLevel(SoundCategory.MASTER, mc.gameSettings.getSoundLevel(SoundCategory.MASTER));
    }

    /** Internal server-thread override for the owned scratch world's loaded dimensions */
    public static void setMobsEnabled(boolean enabled) {
        mobsEnabled = enabled;
        for (WorldServer world : DimensionManager.getWorlds()) {
            world.getGameRules()
                .setOrCreateGameRule("doMobSpawning", Boolean.toString(enabled));
            if (!enabled) {
                for (Object entity : new ArrayList<>(world.loadedEntityList)) {
                    if (entity instanceof EntityLiving) ((EntityLiving) entity).setDead();
                }
            }
        }
    }

    @SubscribeEvent
    public void worldLoad(WorldEvent.Load event) {
        if (!event.world.isRemote) {
            event.world.getGameRules()
                .setOrCreateGameRule("doMobSpawning", Boolean.toString(mobsEnabled));
        }
    }

    @SubscribeEvent
    public void entityJoin(EntityJoinWorldEvent event) {
        if (!event.world.isRemote && !mobsEnabled && event.entity instanceof EntityLiving) event.setCanceled(true);
    }

    /** Records the actual local connection returned by Minecraft's integrated-server bootstrap */
    public static void recordStartupConnection(NetworkManager connection) {
        if (!HorizonQAProperties.clientTestsEnabled()) return;
        startupConnection = connection;
        Minecraft mc = Minecraft.getMinecraft();
        HorizonQAMod.LOG.info(
            "CLIENT_TEST integratedConnection screen={} {}",
            mc.currentScreen == null ? "<none>"
                : mc.currentScreen.getClass()
                    .getName(),
            connectionDetails(connection));
    }

    /** Identifies the caller when a startup connection is left behind the main menu */
    public static void recordStartupScreenChange(GuiScreen requested) {
        if (!HorizonQAProperties.clientTestsEnabled() || !(requested instanceof GuiMainMenu)) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null && mc.getIntegratedServer() != null) {
            HorizonQAMod.LOG.warn(
                "CLIENT_TEST main menu requested during integrated startup\n{}",
                ExceptionUtils.getStackTrace(new Throwable("Main menu requested before client world readiness")));
        }
    }

    private static String connectionDetails(NetworkManager connection) {
        if (connection == null) return "connection=<none>";
        Channel channel = connection.channel();
        if (channel == null) return "connection=" + connection + ", channel=<none>";
        return "channelOpen=" + channel.isOpen()
            + ", channelActive="
            + channel.isActive()
            + ", autoRead="
            + channel.config()
                .isAutoRead()
            + ", connectionState="
            + channel.attr(NetworkManager.attrKeyConnectionState)
                .get()
            + ", handler="
            + (connection.getNetHandler() == null ? "<none>"
                : connection.getNetHandler()
                    .getClass()
                    .getName())
            + ", pipeline="
            + channel.pipeline()
                .names()
            + ", disconnect="
            + (connection.getExitMessage() == null ? "<none>"
                : connection.getExitMessage()
                    .getUnformattedText());
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            LwjglInput.isolateInput();
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
        if (finished == null && stop.isFile()) {
            failClient("CLIENT_DEADLINE", "External wall-clock deadline exceeded");
        }
        if (finished == null && ready && mc.theWorld == null) {
            failClient("CLIENT_WORLD_LOST", "Client world was unloaded before the test run completed");
        }
        if (finished != null) {
            shutdownClient(mc);
            return;
        }
        if (!launched && mc.theWorld == null
            && mc.getIntegratedServer() == null
            && Loader.instance()
                .hasReachedState(LoaderState.AVAILABLE)) {
            launched = true;
            launchScratchWorld(mc);
            if (finished != null) return;
        }
        if (!ready && mc.thePlayer != null && mc.theWorld != null && mc.getIntegratedServer() != null) {
            mc.displayGuiScreen(null);
        }
        if (!ready && System.nanoTime() - started > TimeUnit.SECONDS.toNanos(120)) {
            failStartup("Integrated client/player did not become ready within 120 seconds");
        }
        ClientTest.tick(ClientTaskQueue.Phase.END);
    }

    /** Publish readiness only after the first world frame, so bootstrap does not consume test or cleanup budgets */
    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            LwjglInput.applyPointer();
            return;
        }
        if (ready || !launched) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && mc.theWorld != null && mc.getIntegratedServer() != null) ready = true;
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
        if (shutdown == null) {
            shutdown = ClientTest.closeActive();
            shutdownStarted = System.nanoTime();
        }
        if (!shutdown.isDone() && System.nanoTime() - shutdownStarted < TimeUnit.SECONDS.toNanos(5)) return;
        if (!shutdown.isDone()) {
            String message = "Client teardown did not finish within the five-second shutdown budget";
            IssueResult issue = new IssueResult(
                "client:shutdown",
                "CLIENT_SHUTDOWN",
                "horizonqa.client",
                "shutdown",
                message,
                clientDetails(message),
                true);
            ClientTest.interruptCleanup();
            finished = RunReportWriter.write(
                finished.withAdditionalIssue(issue),
                HorizonQAProperties.junitReportFile(),
                HorizonQAProperties.statusReportFile(),
                HorizonQAMod.LOG);
        }
        startupConnection = null;
        LwjglInput.restoreInput();
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
    public synchronized void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ready && !startedTests && finished == null) {
            startedTests = true;
            startTests.run();
        }
    }

    public synchronized void finish(RunResult result) {
        if (finished == null) finished = result;
        running = false;
    }

    private void failStartup(String message) {
        failClient("CLIENT_STARTUP", message);
    }

    private static String clientDetails(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        StringBuilder details = new StringBuilder(message).append("\nscreen=")
            .append(
                mc.currentScreen == null ? "<none>"
                    : mc.currentScreen.getClass()
                        .getName())
            .append("\nworldPresent=")
            .append(mc.theWorld != null)
            .append("\nplayerPresent=")
            .append(mc.thePlayer != null)
            .append("\nserverPresent=")
            .append(mc.getIntegratedServer() != null)
            .append('\n')
            .append(connectionDetails(startupConnection));
        Thread.getAllStackTraces()
            .forEach((thread, stack) -> {
                Throwable trace = new Throwable("Thread " + thread.getName() + " state=" + thread.getState());
                trace.setStackTrace(stack);
                details.append('\n')
                    .append(ExceptionUtils.getStackTrace(trace));
            });
        return details.toString();
    }

    private synchronized void failClient(String kind, String message) {
        if (finished != null) return;
        if (startedTests) {
            if (ReportedRun.requestAbort(message)) return;
            RunResult result = ReportedRun.lastResult();
            if (result != null) {
                finish(result);
                return;
            }
        }
        IssueResult issue = new IssueResult(
            "client:" + (ready ? "runtime" : "startup"),
            kind,
            "horizonqa.client",
            ready ? "runtime" : "startup",
            message,
            ready ? message : clientDetails(message),
            true);
        finish(
            RunReportWriter.write(
                RunResult.preRun(
                    HorizonQAProperties.modeName(),
                    Collections.singletonList(issue),
                    HorizonQAProperties.junitReportFile()
                        .getPath()),
                HorizonQAProperties.junitReportFile(),
                HorizonQAProperties.statusReportFile(),
                HorizonQAMod.LOG));
    }
}
