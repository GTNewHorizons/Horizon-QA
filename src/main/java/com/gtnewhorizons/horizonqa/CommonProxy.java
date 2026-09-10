package com.gtnewhorizons.horizonqa;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.horizonqa.block.BlockDebugIInventory;
import com.gtnewhorizons.horizonqa.block.BlockDebugInvInterface;
import com.gtnewhorizons.horizonqa.block.TileDebugIInventory;
import com.gtnewhorizons.horizonqa.block.TileDebugInvInterface;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizons.horizonqa.HorizonQAProperties.PropertyIssue;
import com.gtnewhorizons.horizonqa.command.HorizonQACommand;
import com.gtnewhorizons.horizonqa.internal.GameTestCatalog;
import com.gtnewhorizons.horizonqa.internal.GameTestRegistry;
import com.gtnewhorizons.horizonqa.internal.GameTestRunner;
import com.gtnewhorizons.horizonqa.internal.GameTestSelection;
import com.gtnewhorizons.horizonqa.internal.GameTestSelection.SelectionIssue;
import com.gtnewhorizons.horizonqa.internal.InteractiveTestSession;
import com.gtnewhorizons.horizonqa.internal.ReportedRun;
import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;
import com.gtnewhorizons.horizonqa.network.HorizonQANetwork;
import com.gtnewhorizons.horizonqa.report.IssueResult;
import com.gtnewhorizons.horizonqa.visual.SelectionBoxRenderer;
import com.gtnewhorizons.horizonqa.world.GameTestWorldType;

import cpw.mods.fml.common.discovery.ASMDataTable;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    private ASMDataTable asmData;

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        HorizonQAMod.LOG.info(Config.greeting);
        HorizonQAMod.LOG.info("I am " + HorizonQAMod.NAME + " at version " + Tags.VERSION);
        HorizonQAMod.LOG.info("Mode (-D{}): {}", HorizonQAProperties.MODE_PROPERTY, HorizonQAProperties.modeName());
        HorizonQAMod.LOG.info(
            "Resolved Horizon-QA behavior: world={}, autoRun={}, stopServer={}, turbo={}, gridOrigin={}, "
                + "interactiveFeatures={}",
            HorizonQAProperties.worldPolicyName(),
            HorizonQAProperties.autoRunTests(),
            HorizonQAProperties.stopServerAfterRun(),
            HorizonQAProperties.turboMultiplier(),
            HorizonQAProperties.gridOriginName(),
            HorizonQAProperties.interactiveFeaturesEnabled());
        if (HorizonQAProperties.hasModeError()) {
            HorizonQAMod.LOG.error(HorizonQAProperties.modeError());
        } else if (!HorizonQAProperties.autoRunTests()) {
            logNonFatalPropertyIssues();
        }
        if (HorizonQAProperties.allowLegacyNumericItemIds()) {
            if (HorizonQAProperties.isInteractive()) {
                HorizonQAMod.LOG.warn(
                    "-D{}=true trusts this environment's numeric item registry only for interactive /horizonqa load "
                        + "migration. Test execution remains strict; remove the flag after re-exporting as format 2.",
                    HorizonQAProperties.ALLOW_LEGACY_NUMERIC_ITEM_IDS_PROPERTY);
            } else {
                HorizonQAMod.LOG.warn(
                    "-D{}=true is ignored outside interactive mode; legacy numeric ItemStack IDs remain rejected.",
                    HorizonQAProperties.ALLOW_LEGACY_NUMERIC_ITEM_IDS_PROPERTY);
            }
        }
        if (HorizonQAProperties.usesVoidWorld()) {
            HorizonQAMod.LOG.info(
                "Void world policy registered as '{}' (Forge id {}).",
                GameTestWorldType.INSTANCE.getWorldTypeName(),
                GameTestWorldType.INSTANCE.getWorldTypeID());
        }

        ForgeChunkManager.setForcedChunkLoadingCallback(HorizonQAMod.instance, HorizonQAMod.CHUNK_LOADER);
        asmData = event.getAsmData();

        ItemHorizonWand.INSTANCE = new ItemHorizonWand();
        GameRegistry.registerItem(ItemHorizonWand.INSTANCE, "wand");

        BlockDebugIInventory.INSTANCE = new BlockDebugIInventory();
        GameRegistry.registerBlock(BlockDebugIInventory.INSTANCE, "iinv_debug");
        BlockDebugInvInterface.INSTANCE = new BlockDebugInvInterface();
        GameRegistry.registerBlock(BlockDebugInvInterface.INSTANCE, "debug_inv_interface");

        GameRegistry.registerTileEntity(TileDebugIInventory.class, "iinv_debug");
        GameRegistry.registerTileEntity(TileDebugInvInterface.class, "debug_inv_interface");

        if (HorizonQAProperties.isActive()) {
            MinecraftForge.EVENT_BUS.register(new SelectionBoxRenderer());
        }
    }

    public void init(FMLInitializationEvent event) {
        HorizonQANetwork.init();
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        ReportedRun.clearLastResult();
        if (HorizonQAProperties.hasModeError()) {
            ReportedRun.configurationFailure(CommonProxy::ciConfigurationIssues)
                .start();
            return;
        }
        if (HorizonQAProperties.isOff()) return;

        InteractiveTestSession.reset();

        HorizonQAMod.LOG.info("Discovering tests...");
        GameTestCatalog catalog = GameTestRegistry.discoverTests(asmData);
        event.registerServerCommand(new HorizonQACommand(catalog));

        if (!HorizonQAProperties.autoRunTests()) return;

        GameTestSelection selection = catalog
            .select(HorizonQAProperties.selectsAllTests(), HorizonQAProperties.testSelectors());
        List<SelectionIssue> infrastructureIssues = new ArrayList<>(selection.infrastructureIssues());
        if (selection.selectedTests()
            .isEmpty() && infrastructureIssues.isEmpty()
            && !HorizonQAProperties.allowNoTests()) {
            infrastructureIssues.add(
                GameTestSelection
                    .noSelectedTests(HorizonQAProperties.selectsAllTests(), HorizonQAProperties.rawTests()));
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
        }
        ReportedRun.StartStatus status = new ReportedRun(
            catalog,
            selection.selectedTests(),
            issues,
            CommonProxy::ciConfigurationIssues).start();
        if (status == ReportedRun.StartStatus.ALREADY_ACTIVE) {
            HorizonQAMod.LOG.error("Could not start automatic reported run because execution is already active.");
        } else if (status == ReportedRun.StartStatus.STARTED) {
            HorizonQAMod.LOG.info(
                "Starting {} selected test(s) in auto-run mode.",
                selection.selectedTests()
                    .size());
        }
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        boolean reportedRunReleasedChunks = ReportedRun.shutdown();
        InteractiveTestSession.reset();
        GameTestRunner.shutdown();
        if (!reportedRunReleasedChunks) HorizonQAMod.CHUNK_LOADER.releaseAll();
        ReportedRun.clearLastResult();
    }

    private static List<IssueResult> ciConfigurationIssues() {
        List<PropertyIssue> issues = HorizonQAProperties.ciInfrastructureIssues();
        logInfrastructureIssues(issues);
        return toPropertyIssueResults(issues);
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
                "Deferring non-autorun property issue [{}] {} in {}: {}",
                issue.id(),
                issue.kind(),
                issue.property(),
                issue.message());
        }
    }
}
