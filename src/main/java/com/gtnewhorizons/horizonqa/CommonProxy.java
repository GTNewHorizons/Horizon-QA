package com.gtnewhorizons.horizonqa;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizons.horizonqa.HorizonQAProperties.PropertyIssue;
import com.gtnewhorizons.horizonqa.command.HorizonQACommand;
import com.gtnewhorizons.horizonqa.internal.DiscoveryResult;
import com.gtnewhorizons.horizonqa.internal.GameTestBatchRunner;
import com.gtnewhorizons.horizonqa.internal.GameTestRegistry;
import com.gtnewhorizons.horizonqa.internal.GameTestSelection;
import com.gtnewhorizons.horizonqa.internal.GameTestSelection.SelectionIssue;
import com.gtnewhorizons.horizonqa.internal.InteractiveTestSession;
import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;
import com.gtnewhorizons.horizonqa.report.ConsoleReporter;
import com.gtnewhorizons.horizonqa.report.IssueResult;
import com.gtnewhorizons.horizonqa.report.JUnitXmlReporter;
import com.gtnewhorizons.horizonqa.report.ReportPathPreflight;
import com.gtnewhorizons.horizonqa.report.RunResult;
import com.gtnewhorizons.horizonqa.report.StatusJsonReporter;
import com.gtnewhorizons.horizonqa.visual.SelectionBoxRenderer;
import com.gtnewhorizons.horizonqa.world.GameTestWorldType;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        HorizonQAMod.LOG.info(Config.greeting);
        HorizonQAMod.LOG.info("I am " + HorizonQAMod.NAME + " at version " + Tags.VERSION);
        HorizonQAMod.LOG.info("Mode (-D{}): {}", HorizonQAProperties.MODE_PROPERTY, HorizonQAProperties.modeName());
        if (HorizonQAProperties.hasModeError()) {
            HorizonQAMod.LOG.error(HorizonQAProperties.modeError());
        } else if (HorizonQAProperties.isInteractive()) {
            logNonFatalPropertyIssues();
        }
        if (HorizonQAProperties.isCi()) {
            HorizonQAMod.LOG.info(
                "Void world registered as '{}' (Forge id {}).",
                GameTestWorldType.INSTANCE.getWorldTypeName(),
                GameTestWorldType.INSTANCE.getWorldTypeID());
        }

        ForgeChunkManager.setForcedChunkLoadingCallback(HorizonQAMod.instance, HorizonQAMod.CHUNK_LOADER);
        GameTestRegistry.setAsmData(event.getAsmData());

        ItemHorizonWand.INSTANCE = new ItemHorizonWand();
        GameRegistry.registerItem(ItemHorizonWand.INSTANCE, "wand");

        if (HorizonQAProperties.isActive()) {
            MinecraftForge.EVENT_BUS.register(new SelectionBoxRenderer());
        }
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        List<PropertyIssue> ciPropertyIssues = HorizonQAProperties.ciInfrastructureIssues();
        if (!ciPropertyIssues.isEmpty() || HorizonQAProperties.isCi()) {
            List<IssueResult> reportPathIssues = ReportPathPreflight
                .check(HorizonQAProperties.junitReportFile(), HorizonQAProperties.statusReportFile());
            if (!reportPathIssues.isEmpty()) {
                logReportPathIssues(reportPathIssues);
                RunResult result = preRunResult(reportPathIssues);
                // The configured report outputs just failed preflight; retrying them can create partial or colliding
                // files, so report this class of failure to the console only.
                ConsoleReporter.report(result);
                FMLCommonHandler.instance()
                    .exitJava(result.exitCode(), false);
                return;
            }
        }
        if (!ciPropertyIssues.isEmpty()) {
            logInfrastructureIssues(ciPropertyIssues);
            RunResult result = preRunResult(toPropertyIssueResults(ciPropertyIssues));
            result = writePreRunReport(result);
            FMLCommonHandler.instance()
                .exitJava(result.exitCode(), false);
            return;
        }
        if (HorizonQAProperties.isOff()) return;

        InteractiveTestSession.reset();
        event.registerServerCommand(new HorizonQACommand());

        HorizonQAMod.LOG.info("Discovering tests...");
        DiscoveryResult discovery = GameTestRegistry.discoverTests();

        if (!HorizonQAProperties.isCi()) return;

        GameTestSelection selection = GameTestSelection.from(discovery);
        List<SelectionIssue> infrastructureIssues = new ArrayList<>(selection.infrastructureIssues());
        if (selection.selectedTests()
            .isEmpty() && infrastructureIssues.isEmpty()
            && !HorizonQAProperties.allowNoTests()) {
            infrastructureIssues.add(GameTestSelection.noSelectedTests(HorizonQAProperties.selectsAllTests()));
        }
        logSelectionIssues(infrastructureIssues);
        List<IssueResult> issues = toIssueResults(infrastructureIssues);

        if (selection.selectedTests()
            .isEmpty()) {
            if (infrastructureIssues.isEmpty()) {
                HorizonQAMod.LOG.warn("No tests found. Nothing to run.");
            } else {
                HorizonQAMod.LOG.error("No selected valid tests. Nothing to run.");
            }
            RunResult result = preRunResult(issues);
            result = writePreRunReport(result);
            FMLCommonHandler.instance()
                .exitJava(result.exitCode(), false);
            return;
        }

        HorizonQAMod.LOG.info(
            "Starting {} test(s) in CI mode.",
            selection.selectedTests()
                .size());
        GameTestBatchRunner batchRunner = new GameTestBatchRunner(
            selection.selectedTests(),
            discovery.beforeBatchMethods(),
            discovery.afterBatchMethods(),
            issues);
        batchRunner.start();
    }

    private static void logInfrastructureIssues(List<PropertyIssue> issues) {
        for (PropertyIssue issue : issues) {
            HorizonQAMod.LOG.error(
                "Infrastructure issue [{}] {} in {}: {}",
                issue.id(),
                issue.kind(),
                issue.property(),
                issue.message());
        }
    }

    private static void logSelectionIssues(List<SelectionIssue> issues) {
        for (SelectionIssue issue : issues) {
            HorizonQAMod.LOG.error(
                "Infrastructure issue [{}] {} in {}: {}",
                issue.id(),
                issue.kind(),
                HorizonQAProperties.TESTS_PROPERTY,
                issue.message());
        }
    }

    private static void logReportPathIssues(List<IssueResult> issues) {
        HorizonQAMod.LOG.error("Report path preflight failed; aborting before test discovery/execution.");
        for (IssueResult issue : issues) {
            HorizonQAMod.LOG.error("Infrastructure issue [{}] {}: {}", issue.id(), issue.name(), issue.message());
        }
    }

    private static RunResult preRunResult(List<IssueResult> issues) {
        File reportFile = HorizonQAProperties.junitReportFile();
        return RunResult.preRun(HorizonQAProperties.modeName(), issues, reportFile.getPath());
    }

    private static RunResult writePreRunReport(RunResult result) {
        File reportFile = HorizonQAProperties.junitReportFile();
        try {
            JUnitXmlReporter.write(result, reportFile);
            HorizonQAMod.LOG.info("JUnit XML report written to {}", reportFile.getAbsolutePath());
        } catch (IOException e) {
            HorizonQAMod.LOG.error("Failed to write JUnit XML report: {}", e.getMessage());
            result = result.withAdditionalIssue(IssueResult.reporting("junit", reportFile.getAbsolutePath(), e));
        }

        File statusFile = HorizonQAProperties.statusReportFile();
        try {
            StatusJsonReporter.write(result, statusFile);
            HorizonQAMod.LOG.info("Status JSON report written to {}", statusFile.getAbsolutePath());
        } catch (IOException e) {
            HorizonQAMod.LOG.error("Failed to write status JSON report: {}", e.getMessage());
            result = result.withAdditionalIssue(IssueResult.reporting("status", statusFile.getAbsolutePath(), e));
        }
        ConsoleReporter.report(result);
        return result;
    }

    private static List<IssueResult> toIssueResults(List<SelectionIssue> issues) {
        List<IssueResult> results = new ArrayList<>();
        for (SelectionIssue issue : issues) {
            results.add(IssueResult.selection(issue));
        }
        return results;
    }

    private static List<IssueResult> toPropertyIssueResults(List<PropertyIssue> issues) {
        List<IssueResult> results = new ArrayList<>();
        for (PropertyIssue issue : issues) {
            results.add(IssueResult.property(issue));
        }
        return results;
    }

    private static void logNonFatalPropertyIssues() {
        for (PropertyIssue issue : HorizonQAProperties.propertyIssues()) {
            HorizonQAMod.LOG.warn(
                "Ignoring non-CI property issue [{}] {} in {}: {}",
                issue.id(),
                issue.kind(),
                issue.property(),
                issue.message());
        }
    }
}
