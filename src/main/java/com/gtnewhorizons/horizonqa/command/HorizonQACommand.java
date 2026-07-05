package com.gtnewhorizons.horizonqa.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.HorizonQAProperties.PropertyIssue;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.gt.GTNHGameTestHelper;
import com.gtnewhorizons.horizonqa.command.HorizonQACommandUtils.CellRecord;
import com.gtnewhorizons.horizonqa.internal.DiscoveryIssue;
import com.gtnewhorizons.horizonqa.internal.GameTestBatchRunner;
import com.gtnewhorizons.horizonqa.internal.GameTestDefinition;
import com.gtnewhorizons.horizonqa.internal.GameTestRegistry;
import com.gtnewhorizons.horizonqa.internal.InteractiveTestSession;
import com.gtnewhorizons.horizonqa.internal.InvalidTestDefinition;
import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;
import com.gtnewhorizons.horizonqa.item.ItemHorizonWand.LabelMutationResult;
import com.gtnewhorizons.horizonqa.report.CaseResult;
import com.gtnewhorizons.horizonqa.report.ConsoleReporter;
import com.gtnewhorizons.horizonqa.report.IssueResult;
import com.gtnewhorizons.horizonqa.report.ReportPathPreflight;
import com.gtnewhorizons.horizonqa.report.RunReportWriter;
import com.gtnewhorizons.horizonqa.report.RunResult;
import com.gtnewhorizons.horizonqa.structure.HybridStructureLoader;
import com.gtnewhorizons.horizonqa.structure.HybridStructureTemplate;
import com.gtnewhorizons.horizonqa.structure.StructureExporter;
import com.gtnewhorizons.horizonqa.structure.StructurePlacer;

public class HorizonQACommand extends CommandBase {

    private static final String[] SUBCOMMANDS = { "run", "runall", "runfailed", "tp", "runthis", "runthat", "pos",
        "clearall", "load", "export", "clear", "label", "labels" };
    private static final String[] LABEL_SUBCOMMANDS = { "list", "remove", "clear" };
    private static final Set<String> LAST_REPORTED_FAILED_IDS = new LinkedHashSet<>();
    private static volatile boolean reportBatchRunning;

    @Override
    public String getCommandName() {
        return "horizonqa";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/horizonqa <run|runall|runfailed|tp|runthis|runthat|pos|clearall|load|export|clear|label|labels>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public List getCommandAliases() {
        return Arrays.asList("qa");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender
                .addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "Usage: " + getCommandUsage(sender)));
            return;
        }
        switch (args[0]) {
            case "run":
                handleRun(sender, args);
                break;
            case "runall":
                handleRunAll(sender, args);
                break;
            case "runfailed":
                handleRunFailed(sender, args);
                break;
            case "tp":
                handleTeleport(sender, args);
                break;
            case "runthis":
                handleRunThis(sender, args);
                break;
            case "runthat":
                handleRunThat(sender, args);
                break;
            case "pos":
                handlePos(sender, args);
                break;
            case "clearall":
                handleClearAll(sender, args);
                break;
            case "load":
                handleLoad(sender, args);
                break;
            case "export":
                handleExport(sender, args);
                break;
            case "clear":
                handleClear(sender, args);
                break;
            case "label":
                handleLabel(sender, args);
                break;
            case "labels":
                handleLabels(sender, args);
                break;
            default:
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED + "Unknown subcommand '" + args[0] + "'. " + getCommandUsage(sender)));
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        }
        if (args.length == 2) {
            if ("run".equals(args[0])) {
                List<GameTestDefinition> runnable = GameTestRegistry.getAllTests();
                String[] ids = new String[runnable.size()];
                for (int i = 0; i < runnable.size(); i++) ids[i] = runnable.get(i)
                    .getTestId();
                return getListOfStringsMatchingLastWord(args, ids);
            }
            if ("runall".equals(args[0])) {
                Set<String> namespaces = new LinkedHashSet<>();
                for (GameTestDefinition def : GameTestRegistry.getAllTests()) {
                    String id = def.getTestId();
                    int colon = id.indexOf(':');
                    if (colon > 0) namespaces.add(id.substring(0, colon));
                }
                return getListOfStringsMatchingLastWord(args, namespaces.toArray(new String[0]));
            }
            if ("tp".equals(args[0])) {
                return getListOfStringsMatchingLastWord(args, knownCellIds());
            }
            if ("load".equals(args[0])) {
                return getListOfStringsMatchingLastWord(args, knownTemplateNames());
            }
            if ("labels".equals(args[0])) {
                return getListOfStringsMatchingLastWord(args, LABEL_SUBCOMMANDS);
            }
        }
        if (args.length == 3 && "labels".equals(args[0]) && "remove".equals(args[1])) {
            EntityPlayer player = sender instanceof EntityPlayer ? (EntityPlayer) sender : null;
            ItemStack wand = player != null ? findWand(player) : null;
            if (wand != null) {
                Set<String> names = ItemHorizonWand.getLabels(wand)
                    .keySet();
                return getListOfStringsMatchingLastWord(args, names.toArray(new String[0]));
            }
        }
        return null;
    }

    private void handleRun(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Usage: /horizonqa run <testId>"));
            return;
        }
        String testId = args[1];
        GameTestDefinition def = findDefinition(testId);
        if (def == null) {
            InvalidTestDefinition invalidTest = findInvalidTest(testId);
            if (invalidTest != null) {
                reportInvalidTest(sender, invalidTest);
                return;
            }
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Unknown test: '"
                        + EnumChatFormatting.YELLOW
                        + testId
                        + EnumChatFormatting.RED
                        + "'. Use /horizonqa runall to list available tests."));
            return;
        }
        if (HorizonQAProperties.usesReportedCommandBatches()) {
            startReportedBatch(
                sender,
                Collections.singletonList(def),
                EnumChatFormatting.GREEN + "Launched report batch: " + EnumChatFormatting.YELLOW + def.getTestId());
            return;
        }
        if (rejectBatchRunning(sender)) return;
        int launched = InteractiveTestSession.get()
            .launchTest(def);
        if (launched > 0) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "Launched: " + EnumChatFormatting.YELLOW + def.getTestId()));
        } else {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Could not launch '"
                        + EnumChatFormatting.YELLOW
                        + def.getTestId()
                        + EnumChatFormatting.RED
                        + "'. Check the server log for details."));
        }
    }

    private void handleRunAll(ICommandSender sender, String[] args) {
        List<GameTestDefinition> tests;
        if (args.length >= 2) {
            String ns = args[1];
            tests = GameTestRegistry.getTestsForNamespace(ns);
            if (tests.isEmpty()) {
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED + "No tests found for namespace '"
                            + EnumChatFormatting.YELLOW
                            + ns
                            + EnumChatFormatting.RED
                            + "'."));
                return;
            }
        } else {
            tests = GameTestRegistry.getAllTests();
            if (tests.isEmpty()) {
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.YELLOW + "No tests discovered. Make sure your mod is loaded "
                            + "and annotated with @GameTestHolder."));
                return;
            }
        }
        if (HorizonQAProperties.usesReportedCommandBatches()) {
            startReportedBatch(
                sender,
                tests,
                EnumChatFormatting.GREEN + "Launched report batch with "
                    + EnumChatFormatting.YELLOW
                    + tests.size()
                    + EnumChatFormatting.GREEN
                    + " test(s).");
            return;
        }
        if (rejectBatchRunning(sender)) return;
        InteractiveTestSession session = InteractiveTestSession.get();
        session.clearAll();
        int launched = session.launchTests(tests);
        if (launched > 0) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "Launched "
                        + EnumChatFormatting.YELLOW
                        + launched
                        + EnumChatFormatting.GREEN
                        + " test(s)."));
        } else {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Could not launch tests. The full test area could not be loaded."));
        }
    }

    private void handleRunFailed(ICommandSender sender, String[] args) {
        if (HorizonQAProperties.usesReportedCommandBatches() && reportBatchRunning) {
            reportBatchAlreadyRunning(sender);
            return;
        }
        Set<String> failedIds = failedIdsForCurrentMode();
        if (failedIds.isEmpty()) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "No failed tests to re-run."));
            return;
        }
        List<GameTestDefinition> defs = new ArrayList<>();
        for (String id : failedIds) {
            GameTestDefinition def = findDefinition(id);
            if (def != null) defs.add(def);
        }
        defs.sort(Comparator.comparing(GameTestDefinition::getTestId));
        if (defs.isEmpty()) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.YELLOW + "Could not find definitions for the failed tests — "
                        + "were they unloaded?"));
            return;
        }
        if (HorizonQAProperties.usesReportedCommandBatches()) {
            startReportedBatch(
                sender,
                defs,
                EnumChatFormatting.GREEN + "Launched report batch with "
                    + EnumChatFormatting.YELLOW
                    + defs.size()
                    + EnumChatFormatting.GREEN
                    + " failed test(s).");
            return;
        }
        if (rejectBatchRunning(sender)) return;
        int launched = InteractiveTestSession.get()
            .launchTests(defs);
        if (launched > 0) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "Re-running "
                        + EnumChatFormatting.YELLOW
                        + launched
                        + EnumChatFormatting.GREEN
                        + " failed test(s)."));
        } else {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Could not re-run failed tests. The full test area could not be loaded."));
        }
    }

    private void handleTeleport(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Usage: /horizonqa tp <testId>"));
            return;
        }

        EntityPlayer player = requirePlayer(sender);
        if (player == null) return;
        if (!(player instanceof EntityPlayerMP serverPlayer)) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "This command must be run by a server-side player."));
            return;
        }

        List<CellRecord> cells = new ArrayList<>(
            InteractiveTestSession.get()
                .getKnownCells());
        if (cells.isEmpty()) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "No test cells found. Run /horizonqa runall first."));
            return;
        }

        String testId = args[1];
        CellRecord cell = HorizonQACommandUtils.findTestById(testId, cells);
        if (cell == null) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "No placed test cell for '"
                        + EnumChatFormatting.YELLOW
                        + testId
                        + EnumChatFormatting.RED
                        + "'. Use tab completion after /horizonqa runall."));
            return;
        }

        if (player.worldObj == null || player.worldObj.provider == null || player.worldObj.provider.dimensionId != 0) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Test cells are in the overworld. Go to dimension 0 first."));
            return;
        }

        double targetX = (cell.minX + cell.maxX + 1.0) * 0.5;
        double targetY = cell.maxY + 2.0;
        double targetZ = (cell.minZ + cell.maxZ + 1.0) * 0.5;

        if (serverPlayer.ridingEntity != null) {
            serverPlayer.mountEntity(null);
        }
        serverPlayer.playerNetServerHandler
            .setPlayerLocation(targetX, targetY, targetZ, player.rotationYaw, player.rotationPitch);

        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + "Teleported to: " + EnumChatFormatting.YELLOW + cell.testId));
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GRAY + String.format("Cell target: (%.1f, %.1f, %.1f)", targetX, targetY, targetZ)));

    }

    private static Set<String> failedIdsForCurrentMode() {
        if (!HorizonQAProperties.usesReportedCommandBatches()) {
            return InteractiveTestSession.get()
                .getFailedIds();
        }
        return new LinkedHashSet<>(LAST_REPORTED_FAILED_IDS);
    }

    private static void startReportedBatch(ICommandSender sender, List<GameTestDefinition> tests,
        String launchedMessage) {
        if (reportBatchRunning || GameTestBatchRunner.isBatchRunning()) {
            reportBatchAlreadyRunning(sender);
            return;
        }
        if (!preflightReportOutputs(sender)) {
            return;
        }
        try {
            GameTestBatchRunner batchRunner = new GameTestBatchRunner(
                tests,
                GameTestRegistry.getBeforeBatchMethods(),
                GameTestRegistry.getAfterBatchMethods(),
                Collections.emptyList(),
                result -> {
                    try {
                        rememberReportedBatchResult(result);
                    } finally {
                        reportBatchRunning = false;
                    }
                });
            reportBatchRunning = true;
            batchRunner.start();
        } catch (IllegalStateException e) {
            reportBatchRunning = false;
            reportBatchAlreadyRunning(sender);
            return;
        } catch (RuntimeException | Error e) {
            reportBatchRunning = false;
            throw e;
        }
        sender.addChatMessage(new ChatComponentText(launchedMessage));
    }

    private static boolean preflightReportOutputs(ICommandSender sender) {
        List<PropertyIssue> propertyIssues = HorizonQAProperties.reportInfrastructureIssues();
        if (!propertyIssues.isEmpty()) {
            logPropertyIssues(propertyIssues);
            File reportFile = HorizonQAProperties.junitReportFile();
            RunResult result = RunResult
                .preRun(HorizonQAProperties.modeName(), toPropertyIssueResults(propertyIssues), reportFile.getPath());
            RunReportWriter.write(result, HorizonQAMod.LOG);
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Reported-batch configuration is invalid; tests were not launched. "
                        + "Check the report files and server log for details."));
            return false;
        }

        List<IssueResult> reportPathIssues = ReportPathPreflight
            .check(HorizonQAProperties.junitReportFile(), HorizonQAProperties.statusReportFile());
        if (reportPathIssues.isEmpty()) {
            return true;
        }

        HorizonQAMod.LOG.error("Report path preflight failed; reported batch was not launched.");
        for (IssueResult issue : reportPathIssues) {
            HorizonQAMod.LOG.error("Infrastructure issue [{}] {}: {}", issue.id(), issue.name(), issue.message());
        }
        File reportFile = HorizonQAProperties.junitReportFile();
        RunResult result = RunResult.preRun(HorizonQAProperties.modeName(), reportPathIssues, reportFile.getPath());
        ConsoleReporter.report(result);
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.RED + "Report path preflight failed; tests were not launched. "
                    + "Check the server log for details."));
        return false;
    }

    public static void rememberReportedBatchResult(RunResult result) {
        LAST_REPORTED_FAILED_IDS.clear();
        if (result == null) {
            return;
        }
        for (CaseResult resultCase : result.cases()) {
            if (resultCase.failed() || resultCase.timedOut() || resultCase.error()) {
                LAST_REPORTED_FAILED_IDS.add(resultCase.id());
            }
        }
    }

    public static void resetReportBatchState() {
        reportBatchRunning = false;
        LAST_REPORTED_FAILED_IDS.clear();
    }

    private static void reportBatchAlreadyRunning(ICommandSender sender) {
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.RED + "A GameTest batch is already running. Wait for it to finish first."));
    }

    private static boolean rejectBatchRunning(ICommandSender sender) {
        if (!GameTestBatchRunner.isBatchRunning()) {
            return false;
        }
        reportBatchAlreadyRunning(sender);
        return true;
    }

    private static boolean rejectInteractiveOnlyInNonInteractiveMode(ICommandSender sender, String replacement) {
        if (HorizonQAProperties.interactiveFeaturesEnabled()) {
            return false;
        }
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.RED + "That command is only available in interactive mode. "
                    + "Use "
                    + replacement
                    + " for reported batches."));
        return true;
    }

    private static void logPropertyIssues(List<PropertyIssue> issues) {
        for (PropertyIssue issue : issues) {
            HorizonQAMod.LOG.error(
                "Infrastructure issue [{}] {} in {}: {}",
                issue.id(),
                issue.kind(),
                issue.property(),
                issue.message());
        }
    }

    private static List<IssueResult> toPropertyIssueResults(List<PropertyIssue> issues) {
        List<IssueResult> results = new ArrayList<>();
        for (PropertyIssue issue : issues) {
            results.add(IssueResult.property(issue));
        }
        return results;
    }

    private void handleRunThis(ICommandSender sender, String[] args) {
        if (rejectInteractiveOnlyInNonInteractiveMode(sender, "/horizonqa run <testId>")) return;
        if (rejectBatchRunning(sender)) return;
        EntityPlayer player = requirePlayer(sender);
        if (player == null) return;

        int px = (int) Math.floor(player.posX);
        int py = (int) Math.floor(player.posY);
        int pz = (int) Math.floor(player.posZ);

        CellRecord cell = HorizonQACommandUtils.findTestContaining(
            px,
            py,
            pz,
            InteractiveTestSession.get()
                .getKnownCells());
        if (cell == null) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "You are not inside any known test cell. "
                        + "Run /horizonqa runall first to place cells."));
            return;
        }
        relaunchCell(sender, cell);
    }

    private void handleRunThat(ICommandSender sender, String[] args) {
        if (rejectInteractiveOnlyInNonInteractiveMode(sender, "/horizonqa run <testId>")) return;
        if (rejectBatchRunning(sender)) return;
        EntityPlayer player = requirePlayer(sender);
        if (player == null) return;

        CellRecord cell = HorizonQACommandUtils.findTestAlongLook(
            player,
            InteractiveTestSession.get()
                .getKnownCells());
        if (cell == null) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "No test cell in your line of sight (within 64 blocks). "
                        + "Run /horizonqa runall first."));
            return;
        }
        relaunchCell(sender, cell);
    }

    private static void relaunchCell(ICommandSender sender, CellRecord cell) {
        GameTestDefinition def = findDefinition(cell.testId);
        if (def == null) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "Definition not found for '" + cell.testId + "'."));
            return;
        }
        boolean launched = InteractiveTestSession.get()
            .relaunchAtCell(def);
        if (launched) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "Re-running: " + EnumChatFormatting.YELLOW + def.getTestId()));
        } else {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Could not re-run '"
                        + EnumChatFormatting.YELLOW
                        + def.getTestId()
                        + EnumChatFormatting.RED
                        + "'. Check the server log for details."));
        }
    }

    private void handlePos(ICommandSender sender, String[] args) {
        if (rejectInteractiveOnlyInNonInteractiveMode(sender, "/horizonqa run <testId>")) return;
        EntityPlayer player = requirePlayer(sender);
        if (player == null) return;

        int px = (int) Math.floor(player.posX);
        int py = (int) Math.floor(player.posY);
        int pz = (int) Math.floor(player.posZ);

        CellRecord cell = HorizonQACommandUtils.findTestContaining(
            px,
            py,
            pz,
            InteractiveTestSession.get()
                .getKnownCells());
        if (cell == null) {
            cell = HorizonQACommandUtils.findNearestTest(
                px,
                py,
                pz,
                InteractiveTestSession.get()
                    .getKnownCells());
        }
        if (cell == null) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "No test cells found. Run /horizonqa runall first."));
            return;
        }

        int relX = px - cell.originX;
        int relY = py - cell.originY;
        int relZ = pz - cell.originZ;
        String call = String.format("helper.absolute(%d, %d, %d)", relX, relY, relZ);

        sender.addChatMessage(
            new ChatComponentText(EnumChatFormatting.AQUA + "Test: " + EnumChatFormatting.YELLOW + cell.testId));
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.AQUA + "World:    "
                    + EnumChatFormatting.WHITE
                    + String.format("(%d, %d, %d)", px, py, pz)));
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.AQUA + "Relative: "
                    + EnumChatFormatting.WHITE
                    + String.format("(%d, %d, %d)", relX, relY, relZ)));

        ChatComponentText clickable = new ChatComponentText(
            EnumChatFormatting.GREEN + call + EnumChatFormatting.GRAY + "  \u00ab click to copy to chat");
        clickable
            .setChatStyle(new ChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, call)));
        sender.addChatMessage(clickable);
    }

    private void handleClearAll(ICommandSender sender, String[] args) {
        if (rejectInteractiveOnlyInNonInteractiveMode(sender, "/horizonqa runall")) return;
        if (rejectBatchRunning(sender)) return;
        int count = InteractiveTestSession.get()
            .getKnownCells()
            .size();
        InteractiveTestSession.get()
            .clearAll();
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + "Cleared "
                    + EnumChatFormatting.YELLOW
                    + count
                    + EnumChatFormatting.GREEN
                    + " test cell(s)."));
    }

    private void handleLoad(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Usage: /horizonqa load <namespace:path> [rotation 0-3]"));
            return;
        }
        if (rejectInteractiveOnly(sender)) return;
        if (rejectBatchRunning(sender)) return;

        EntityPlayer player = requirePlayer(sender);
        if (player == null) return;
        ItemStack wand = findWand(player);
        if (wand == null) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "Hold (or have in inventory) a Horizon Wand first."));
            return;
        }
        if (!(player.worldObj instanceof WorldServer world)) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "This command must be run by a server-side player."));
            return;
        }

        String templateName = args[1];
        String exportName = exportNameFromTemplateName(templateName);
        if (exportName == null) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Template must be a namespace:path, and path must be exportable. "
                        + StructureExporter.templatePathRules()));
            return;
        }

        Integer rotation = parseRotation(sender, args);
        if (rotation == null) return;

        int[] origin = ItemHorizonWand.getTargetedPosition(player);
        try {
            HybridStructureTemplate template = HybridStructureLoader.load(templateName);
            StructurePlacer.placeStrict(
                templateName,
                template,
                world,
                origin[0],
                origin[1],
                origin[2],
                rotation,
                GTNHGameTestHelper::rotateStructureTileNbt);

            int labelCount = armWandForTemplate(wand, template, origin[0], origin[1], origin[2], rotation, exportName);
            int placedSizeX = StructurePlacer.placedSizeX(template, rotation);
            int placedSizeZ = StructurePlacer.placedSizeZ(template, rotation);

            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "Loaded '"
                        + EnumChatFormatting.YELLOW
                        + templateName
                        + EnumChatFormatting.GREEN
                        + "' at "
                        + EnumChatFormatting.WHITE
                        + formatBlockPos(origin[0], origin[1], origin[2])
                        + EnumChatFormatting.GREEN
                        + "."));
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GRAY + "Wand selection set to "
                        + placedSizeX
                        + "x"
                        + template.getSizeY()
                        + "x"
                        + placedSizeZ
                        + " with "
                        + labelCount
                        + " label(s)."));

            ChatComponentText exportHint = new ChatComponentText(
                EnumChatFormatting.GRAY + "Edit it, then "
                    + EnumChatFormatting.GREEN
                    + "/qa export"
                    + EnumChatFormatting.GRAY
                    + " to write "
                    + EnumChatFormatting.YELLOW
                    + exportName
                    + EnumChatFormatting.GRAY
                    + ".");
            exportHint.setChatStyle(
                new ChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/qa export")));
            sender.addChatMessage(exportHint);
        } catch (IOException e) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Load failed: " + e.getMessage()));
            HorizonQAMod.LOG.error("Structure load failed for '{}'", templateName, e);
        } catch (RuntimeException e) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Load failed: " + e.getMessage()));
            HorizonQAMod.LOG.error("Structure placement failed for '{}'", templateName, e);
        }
    }

    private void handleExport(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayer)) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "This command must be run by a player."));
            return;
        }

        EntityPlayer player = (EntityPlayer) sender;
        ItemStack wand = findWand(player);

        if (wand == null) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "Hold (or have in inventory) a Horizon Wand first."));
            return;
        }

        NBTTagCompound nbt = wand.getTagCompound();
        if (nbt == null || !nbt.getBoolean(ItemHorizonWand.TAG_POS1_SET)
            || !nbt.getBoolean(ItemHorizonWand.TAG_POS2_SET)) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED
                        + "Wand selection incomplete — left-click Pos1 and right-click Pos2 first."));
            return;
        }

        String name = args.length >= 2 ? args[1] : rememberedExportName(wand);
        if (name == null || name.isEmpty()) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Usage: /horizonqa export [name]"));
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GRAY + "Run "
                        + EnumChatFormatting.WHITE
                        + "/qa load <namespace:path>"
                        + EnumChatFormatting.GRAY
                        + " first, or pass an explicit export name."));
            return;
        }
        if (!StructureExporter.isValidTemplatePath(name)) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + StructureExporter.templatePathRules()));
            return;
        }

        int x1 = nbt.getInteger(ItemHorizonWand.TAG_POS1_X);
        int y1 = nbt.getInteger(ItemHorizonWand.TAG_POS1_Y);
        int z1 = nbt.getInteger(ItemHorizonWand.TAG_POS1_Z);
        int x2 = nbt.getInteger(ItemHorizonWand.TAG_POS2_X);
        int y2 = nbt.getInteger(ItemHorizonWand.TAG_POS2_Y);
        int z2 = nbt.getInteger(ItemHorizonWand.TAG_POS2_Z);

        int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2), maxY = Math.max(y1, y2), maxZ = Math.max(z1, z2);

        WorldServer world = (WorldServer) player.worldObj;
        File outputDir = MinecraftServer.getServer()
            .getFile("horizonqastructures");

        try {
            StructureExporter.ExportResult result = StructureExporter
                .export(world, minX, minY, minZ, maxX, maxY, maxZ, outputDir, name, ItemHorizonWand.getLabels(wand));
            rememberExportName(wand, name);
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "Exported '"
                        + EnumChatFormatting.YELLOW
                        + name
                        + EnumChatFormatting.GREEN
                        + "' \u2192 "
                        + EnumChatFormatting.WHITE
                        + outputDir.getAbsolutePath()));
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.GRAY + "  " + name + ".json        (block layout)"));
            if (result.labelCount() > 0) {
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GRAY + "  labels: "
                            + EnumChatFormatting.YELLOW
                            + result.labelCount()
                            + EnumChatFormatting.GRAY
                            + " coordinate label(s)"));
            }
            if (result.structureDataWritten()) {
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GRAY + "  "
                            + name
                            + result.structureDataExtension()
                            + "        ("
                            + result.tileEntityCount()
                            + " tile entities, "
                            + result.entityCount()
                            + " entities)"));
            }
        } catch (IOException e) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Export failed: " + e.getMessage()));
            HorizonQAMod.LOG.error("StructureExporter failed for '{}'", name, e);
        }
    }

    private static boolean rejectInteractiveOnly(ICommandSender sender) {
        if (HorizonQAProperties.interactiveFeaturesEnabled()) {
            return false;
        }
        sender.addChatMessage(
            new ChatComponentText(EnumChatFormatting.RED + "That command is only available in interactive mode."));
        return true;
    }

    private static Integer parseRotation(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            return 0;
        }
        try {
            int rotation = Integer.parseInt(args[2]);
            if (rotation >= 0 && rotation <= 3) {
                return rotation;
            }
        } catch (NumberFormatException ignored) {}
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.RED + "Rotation must be 0, 1, 2, or 3 quarter-turns clockwise."));
        return null;
    }

    private static int armWandForTemplate(ItemStack wand, HybridStructureTemplate template, int originX, int originY,
        int originZ, int rotation, String exportName) {
        int placedSizeX = StructurePlacer.placedSizeX(template, rotation);
        int placedSizeZ = StructurePlacer.placedSizeZ(template, rotation);

        NBTTagCompound nbt = ItemHorizonWand.getOrCreateNBT(wand);
        nbt.setInteger(ItemHorizonWand.TAG_POS1_X, originX);
        nbt.setInteger(ItemHorizonWand.TAG_POS1_Y, originY);
        nbt.setInteger(ItemHorizonWand.TAG_POS1_Z, originZ);
        nbt.setBoolean(ItemHorizonWand.TAG_POS1_SET, true);
        nbt.setInteger(ItemHorizonWand.TAG_POS2_X, originX + placedSizeX - 1);
        nbt.setInteger(ItemHorizonWand.TAG_POS2_Y, originY + template.getSizeY() - 1);
        nbt.setInteger(ItemHorizonWand.TAG_POS2_Z, originZ + placedSizeZ - 1);
        nbt.setBoolean(ItemHorizonWand.TAG_POS2_SET, true);
        nbt.setBoolean(ItemHorizonWand.TAG_PENDING, false);
        nbt.removeTag(ItemHorizonWand.TAG_LABELS);
        nbt.setString(ItemHorizonWand.TAG_EXPORT_NAME, exportName);

        int labelCount = 0;
        for (Map.Entry<String, TestPos> entry : template.getAnnotations()
            .labels()
            .entrySet()) {
            TestPos pos = entry.getValue();
            int x = originX + StructurePlacer.rotatedLocalX(pos.x(), pos.z(), template.getSizeX(), template.getSizeZ(),
                rotation);
            int y = originY + pos.y();
            int z = originZ + StructurePlacer.rotatedLocalZ(pos.x(), pos.z(), template.getSizeX(), template.getSizeZ(),
                rotation);
            ItemHorizonWand.setLabel(wand, entry.getKey(), x, y, z);
            labelCount++;
        }
        return labelCount;
    }

    static String exportNameFromTemplateName(String templateName) {
        if (templateName == null) {
            return null;
        }
        int separator = templateName.indexOf(':');
        if (separator <= 0 || separator == templateName.length() - 1) {
            return null;
        }
        String namespace = templateName.substring(0, separator);
        String path = templateName.substring(separator + 1);
        if (!namespace.matches("[A-Za-z0-9_.-]+") || !StructureExporter.isValidTemplatePath(path)) {
            return null;
        }
        return path;
    }

    private static String rememberedExportName(ItemStack wand) {
        NBTTagCompound nbt = wand != null ? wand.getTagCompound() : null;
        if (nbt == null || !nbt.hasKey(ItemHorizonWand.TAG_EXPORT_NAME)) {
            return null;
        }
        return nbt.getString(ItemHorizonWand.TAG_EXPORT_NAME);
    }

    private static void rememberExportName(ItemStack wand, String name) {
        ItemHorizonWand.getOrCreateNBT(wand)
            .setString(ItemHorizonWand.TAG_EXPORT_NAME, name);
    }

    private static String formatBlockPos(int x, int y, int z) {
        return "(" + x + ", " + y + ", " + z + ")";
    }

    private static void reportInvalidTest(ICommandSender sender, InvalidTestDefinition invalidTest) {
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.RED + "Invalid test: '"
                    + EnumChatFormatting.YELLOW
                    + invalidTest.intendedTestId()
                    + EnumChatFormatting.RED
                    + "' was excluded during discovery."));

        List<DiscoveryIssue> issues = invalidTest.issues();
        if (!issues.isEmpty()) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Reason: "
                        + issues.get(0)
                            .message()));
            if (issues.size() > 1) {
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GRAY + "Also has "
                            + (issues.size() - 1)
                            + " other discovery issue(s). Check the server log for details."));
            }
        }
    }

    private void handleClear(ICommandSender sender, String[] args) {
        EntityPlayer player = requirePlayer(sender);
        if (player == null) return;
        ItemStack wand = findWand(player);
        if (wand == null) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + StatCollector.translateToLocal("horizonqa.command.clear.no_wand")));
            return;
        }

        wand.setTagCompound(null);

        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + StatCollector.translateToLocal("horizonqa.command.clear.success")));
    }

    private void handleLabel(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Usage: /horizonqa label <name>"));
            return;
        }
        EntityPlayer player = requirePlayer(sender);
        if (player == null) return;
        ItemStack wand = findWand(player);
        if (wand == null) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + StatCollector.translateToLocal("horizonqa.command.clear.no_wand")));
            return;
        }

        int[] target = ItemHorizonWand.getTargetedPosition(player);
        applyLabel(sender, wand, args[1], target[0], target[1], target[2]);
    }

    private void handleLabels(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "Usage: /horizonqa labels <list|remove|clear>"));
            return;
        }
        EntityPlayer player = requirePlayer(sender);
        if (player == null) return;
        ItemStack wand = findWand(player);
        if (wand == null) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + StatCollector.translateToLocal("horizonqa.command.clear.no_wand")));
            return;
        }

        switch (args[1]) {
            case "list":
                handleLabelsList(sender, wand);
                break;
            case "remove":
                if (args.length < 3) {
                    sender.addChatMessage(
                        new ChatComponentText(EnumChatFormatting.RED + "Usage: /horizonqa labels remove <name>"));
                    return;
                }
                removeLabel(sender, wand, args[2]);
                break;
            case "clear":
                int cleared = ItemHorizonWand.clearLabels(wand);
                if (cleared == 0) {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "No labels to clear."));
                } else {
                    sender.addChatMessage(
                        new ChatComponentText(
                            EnumChatFormatting.GREEN + "Cleared "
                                + EnumChatFormatting.YELLOW
                                + cleared
                                + EnumChatFormatting.GREEN
                                + " label(s)."));
                }
                break;
            default:
                sender.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.RED + "Usage: /horizonqa labels <list|remove|clear>"));
        }
    }

    private static void handleLabelsList(ICommandSender sender, ItemStack wand) {
        java.util.Map<String, int[]> labels = ItemHorizonWand.getLabels(wand);
        if (labels.isEmpty()) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "No labels on this wand."));
            return;
        }
        int outside = ItemHorizonWand.outsideSelectionLabelCount(wand);
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + "Labels: "
                    + EnumChatFormatting.YELLOW
                    + labels.size()
                    + (outside > 0 ? EnumChatFormatting.RED + " (" + outside + " outside selection)" : "")));
        for (java.util.Map.Entry<String, int[]> entry : labels.entrySet()) {
            int[] pos = entry.getValue();
            boolean inside = ItemHorizonWand.isInsideSelection(wand, pos[0], pos[1], pos[2]);
            sender.addChatMessage(
                new ChatComponentText(
                    (inside ? EnumChatFormatting.GRAY : EnumChatFormatting.RED) + "  "
                        + entry.getKey()
                        + " -> "
                        + pos[0]
                        + ", "
                        + pos[1]
                        + ", "
                        + pos[2]
                        + (inside || !ItemHorizonWand.hasCompleteSelection(wand) ? "" : " outside")));
        }
    }

    public static void applyLabel(ICommandSender sender, ItemStack wand, String name, int x, int y, int z) {
        LabelMutationResult result = ItemHorizonWand.setLabel(wand, name, x, y, z);
        switch (result.status) {
            case INVALID_NAME:
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED + "Invalid label name '"
                            + EnumChatFormatting.YELLOW
                            + name
                            + EnumChatFormatting.RED
                            + "'. Use [A-Za-z_][A-Za-z0-9_]*."));
                break;
            case DUPLICATE_NAME:
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED + "Label '"
                            + EnumChatFormatting.YELLOW
                            + name
                            + EnumChatFormatting.RED
                            + "' already exists at ("
                            + result.x
                            + ", "
                            + result.y
                            + ", "
                            + result.z
                            + ")."));
                break;
            case SUCCESS:
                String action = result.oldName != null && !result.oldName.equals(name)
                    ? "Renamed label " + EnumChatFormatting.YELLOW
                        + result.oldName
                        + EnumChatFormatting.GREEN
                        + " to "
                        + EnumChatFormatting.YELLOW
                        + name
                    : "Labeled " + EnumChatFormatting.YELLOW + name;
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GREEN + action
                            + EnumChatFormatting.GREEN
                            + " at ("
                            + x
                            + ", "
                            + y
                            + ", "
                            + z
                            + ")."));
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GRAY + "Use "
                            + EnumChatFormatting.WHITE
                            + "helper.pos(\""
                            + name
                            + "\")"
                            + EnumChatFormatting.GRAY
                            + " in tests."));
                break;
        }
    }

    public static void removeLabel(ICommandSender sender, ItemStack wand, String name) {
        if (ItemHorizonWand.removeLabel(wand, name)) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "Removed label "
                        + EnumChatFormatting.YELLOW
                        + name
                        + EnumChatFormatting.GREEN
                        + "."));
        } else {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "No label named '"
                        + EnumChatFormatting.YELLOW
                        + name
                        + EnumChatFormatting.RED
                        + "'."));
        }
    }

    private static GameTestDefinition findDefinition(String testId) {
        for (GameTestDefinition def : GameTestRegistry.getAllTests()) {
            if (def.getTestId()
                .equals(testId)) return def;
        }
        return null;
    }

    private static InvalidTestDefinition findInvalidTest(String testId) {
        for (InvalidTestDefinition invalidTest : GameTestRegistry.getInvalidTests()) {
            if (invalidTest.intendedTestId()
                .equals(testId)) return invalidTest;
        }
        return null;
    }

    private static String[] knownCellIds() {
        List<String> ids = new ArrayList<>();
        for (CellRecord cell : InteractiveTestSession.get()
            .getKnownCells()) {
            ids.add(cell.testId);
        }
        ids.sort(String::compareTo);
        return ids.toArray(new String[0]);
    }

    private static String[] knownTemplateNames() {
        Set<String> names = new LinkedHashSet<>();
        for (GameTestDefinition def : GameTestRegistry.getAllTests()) {
            String templateName = def.getTemplateName();
            if (templateName != null && !templateName.isEmpty()) {
                names.add(templateName);
            }
        }
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String::compareTo);
        return sorted.toArray(new String[0]);
    }

    private static EntityPlayer requirePlayer(ICommandSender sender) {
        if (!(sender instanceof EntityPlayer)) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "This command must be run by a player."));
            return null;
        }
        return (EntityPlayer) sender;
    }

    private static ItemStack findWand(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof ItemHorizonWand) {
            return held;
        }
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemHorizonWand) {
                return stack;
            }
        }
        return null;
    }
}
