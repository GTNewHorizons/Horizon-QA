package com.gtnewhorizons.horizonqa.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestArguments;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.annotation.MethodSource;
import com.gtnewhorizons.horizonqa.internal.GameTestCatalog;
import com.gtnewhorizons.horizonqa.internal.GameTestRegistry;
import com.gtnewhorizons.horizonqa.internal.GameTestRunner;
import com.gtnewhorizons.horizonqa.internal.InteractiveTestSession;
import com.gtnewhorizons.horizonqa.internal.ReportedRun;
import com.gtnewhorizons.horizonqa.internal.TestCell;
import com.gtnewhorizons.horizonqa.report.CaseResult;
import com.gtnewhorizons.horizonqa.report.RunResult;

import cpw.mods.fml.common.discovery.ASMDataTable;

public class HorizonQACommandTest {

    @After
    public void clearExecution() {
        GameTestRunner.shutdown();
        InteractiveTestSession.reset();
        ReportedRun.clearLastResult();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void tabCompletionListsOnlyRunnableTests() {
        HorizonQACommand command = new HorizonQACommand(catalog(RunnableTests.class, InvalidTests.class));

        List<String> runCompletions = command
            .addTabCompletionOptions(new RecordingSender(), new String[] { "run", "" });
        assertTrue(runCompletions.contains("good:RunnableTests.valid"));
        assertFalse(runCompletions.contains("bad:InvalidTests.invalid"));

        List<String> runAllCompletions = command
            .addTabCompletionOptions(new RecordingSender(), new String[] { "runall", "" });
        assertTrue(runAllCompletions.contains("good"));
        assertTrue(runAllCompletions.contains("RunnableTests"));
        assertTrue(runAllCompletions.contains("good:RunnableTests"));
        assertTrue(runAllCompletions.contains("good:RunnableTests.valid"));
        assertFalse(runAllCompletions.contains("bad"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void runAllCompletionUsesParameterizedBaseIdentity() {
        List<String> completions = new HorizonQACommand(catalog(ParameterizedTests.class))
            .addTabCompletionOptions(new RecordingSender(), new String[] { "runall", "" });

        assertTrue(completions.contains("good:ParameterizedTests.acceptsVoltage"));
        assertTrue(completions.contains("good:ParameterizedTests.acceptsVoltage[tier.4]"));
        assertFalse(completions.contains("good:ParameterizedTests.acceptsVoltage[tier"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void teleportTabCompletionListsOnlyPlacedCells() throws ReflectiveOperationException {
        java.util.Map<String, TestCell> knownCells = (java.util.Map<String, TestCell>) sessionField("knownCells")
            .get(InteractiveTestSession.get());
        knownCells
            .put("good:RunnableTests.placed", new TestCell("good:RunnableTests.placed", 0, 64, 0, 0, 64, 0, 4, 68, 4));

        List<String> completions = new HorizonQACommand(catalog(RunnableTests.class))
            .addTabCompletionOptions(new RecordingSender(), new String[] { "tp", "" });

        assertTrue(completions.contains("good:RunnableTests.placed"));
        assertFalse(completions.contains("good:RunnableTests.notPlaced"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void loadTabCompletionListsDiscoveredTemplates() {
        List<String> completions = new HorizonQACommand(catalog(RunnableTests.class))
            .addTabCompletionOptions(new RecordingSender(), new String[] { "load", "" });

        assertTrue(completions.contains("good:machines/ebf"));
        assertFalse(completions.contains(""));
    }

    @Test
    public void templateNameMapsToSafeExportPath() {
        assertEquals("machines/ebf", HorizonQACommand.exportNameFromTemplateName("good:machines/ebf"));
        assertEquals("single_stone", HorizonQACommand.exportNameFromTemplateName("good:single_stone"));

        assertNull(HorizonQACommand.exportNameFromTemplateName("missing_namespace"));
        assertNull(HorizonQACommand.exportNameFromTemplateName(":missing_namespace"));
        assertNull(HorizonQACommand.exportNameFromTemplateName("good:../outside"));
        assertNull(HorizonQACommand.exportNameFromTemplateName("good:machines//ebf"));
    }

    @Test
    public void runKnownInvalidTestReportsInvalidInsteadOfUnknown() {
        String invalidId = "bad:InvalidTests.invalid";

        RecordingSender sender = new RecordingSender();

        new HorizonQACommand(catalog(RunnableTests.class, InvalidTests.class))
            .processCommand(sender, new String[] { "run", invalidId });

        String messages = sender.messages();
        assertTrue(messages.contains("Invalid test"));
        assertTrue(messages.contains(invalidId));
        assertTrue(messages.contains("must be public static"));
        assertFalse(messages.contains("Unknown test"));
    }

    @Test
    public void reportedResultSuppliesFailedIdsForRunfailed() {
        RunResult result = RunResult.completedCases(
            "ci",
            Arrays.asList(
                caseResult("mod:Suite.passed", CaseResult.Status.PASSED),
                caseResult("mod:Suite.skipped", CaseResult.Status.SKIPPED),
                caseResult("mod:Suite.failed", CaseResult.Status.FAILED),
                caseResult("mod:Suite.timedOut", CaseResult.Status.TIMED_OUT),
                caseResult("mod:Suite.error", CaseResult.Status.ERROR)),
            Collections.emptyList(),
            "TEST.xml");

        Set<String> failedIds = HorizonQACommand.failedIds(result);
        assertFalse(failedIds.contains("mod:Suite.passed"));
        assertFalse(failedIds.contains("mod:Suite.skipped"));
        assertTrue(failedIds.contains("mod:Suite.failed"));
        assertTrue(failedIds.contains("mod:Suite.timedOut"));
        assertTrue(failedIds.contains("mod:Suite.error"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void clearAllClearsInteractiveFailedIds() throws ReflectiveOperationException {
        Set<String> failedIds = (Set<String>) sessionField("failedIds").get(InteractiveTestSession.get());
        failedIds.add("mod:Suite.failed");

        InteractiveTestSession.get()
            .clearAll();

        assertTrue(failedIds.isEmpty());
    }

    private static CaseResult caseResult(String testId, CaseResult.Status status) {
        int colon = testId.indexOf(':');
        int dot = testId.lastIndexOf('.');
        String classname = dot > colon ? testId.substring(0, dot) : testId;
        String name = dot >= 0 && dot < testId.length() - 1 ? testId.substring(dot + 1) : testId;
        return new CaseResult(testId, classname, name, status, true, 0, 0.0, "", "", "", Collections.emptyList());
    }

    private static GameTestCatalog catalog(Class<?>... holders) {
        ASMDataTable table = new ASMDataTable();
        for (Class<?> holder : holders) {
            table.addASMData(
                null,
                GameTestHolder.class.getName(),
                holder.getName(),
                holder.getName(),
                Collections.emptyMap());
        }
        return GameTestRegistry.discoverTests(table);
    }

    private static Field sessionField(String name) throws NoSuchFieldException {
        Field field = InteractiveTestSession.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @GameTestHolder("good")
    public static final class RunnableTests {

        @GameTest
        public static void valid(GameTestHelper helper) {}

        @GameTest
        public static void placed(GameTestHelper helper) {}

        @GameTest
        public static void notPlaced(GameTestHelper helper) {}

        @GameTest(template = "machines/ebf")
        public static void withTemplate(GameTestHelper helper) {}

        @GameTest
        public static void empty(GameTestHelper helper) {}
    }

    @GameTestHolder("good")
    public static final class ParameterizedTests {

        @GameTest
        @MethodSource("rows")
        public static void acceptsVoltage(GameTestHelper helper, int voltage) {}

        public static List<GameTestArguments> rows() {
            return Collections.singletonList(GameTestArguments.named("tier.4", 32));
        }
    }

    @GameTestHolder("bad")
    public static final class InvalidTests {

        @GameTest
        private static void invalid(GameTestHelper helper) {}
    }

    private static final class RecordingSender implements ICommandSender {

        private final List<String> messages = new ArrayList<>();

        @Override
        public String getCommandSenderName() {
            return "test";
        }

        @Override
        public IChatComponent func_145748_c_() {
            return new ChatComponentText(getCommandSenderName());
        }

        @Override
        public void addChatMessage(IChatComponent component) {
            messages.add(component.getUnformattedText());
        }

        @Override
        public boolean canCommandSenderUseCommand(int permissionLevel, String commandName) {
            return true;
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return new ChunkCoordinates(0, 0, 0);
        }

        @Override
        public World getEntityWorld() {
            return null;
        }

        String messages() {
            StringBuilder out = new StringBuilder();
            for (String message : messages) {
                out.append(message)
                    .append('\n');
            }
            return out.toString();
        }
    }
}
