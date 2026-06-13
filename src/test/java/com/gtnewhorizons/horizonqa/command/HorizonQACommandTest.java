package com.gtnewhorizons.horizonqa.command;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.command.HorizonQACommandUtils.CellRecord;
import com.gtnewhorizons.horizonqa.internal.DiscoveryIssue;
import com.gtnewhorizons.horizonqa.internal.DiscoveryResult;
import com.gtnewhorizons.horizonqa.internal.DuplicateTestId;
import com.gtnewhorizons.horizonqa.internal.GameTestDefinition;
import com.gtnewhorizons.horizonqa.internal.GameTestRegistry;
import com.gtnewhorizons.horizonqa.internal.InteractiveTestSession;
import com.gtnewhorizons.horizonqa.internal.InvalidBatchHook;
import com.gtnewhorizons.horizonqa.internal.InvalidTestDefinition;

public class HorizonQACommandTest {

    @After
    public void clearRegistry() throws Exception {
        seedRegistry(Collections.emptyList(), Collections.emptyList());
        InteractiveTestSession.reset();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void tabCompletionListsOnlyRunnableTests() throws Exception {
        seedRegistry(
            Collections.singletonList(definition("good:Suite.valid")),
            Collections.singletonList(invalid("bad:Broken.invalid")));

        HorizonQACommand command = new HorizonQACommand();

        List<String> runCompletions = command
            .addTabCompletionOptions(new RecordingSender(), new String[] { "run", "" });
        assertTrue(runCompletions.contains("good:Suite.valid"));
        assertFalse(runCompletions.contains("bad:Broken.invalid"));

        List<String> runAllCompletions = command
            .addTabCompletionOptions(new RecordingSender(), new String[] { "runall", "" });
        assertTrue(runAllCompletions.contains("good"));
        assertFalse(runAllCompletions.contains("bad"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void teleportTabCompletionListsOnlyPlacedCells() throws Exception {
        seedRegistry(
            Arrays.asList(definition("good:Suite.placed"), definition("good:Suite.notPlaced")),
            Collections.emptyList());
        Map<String, CellRecord> knownCells = (Map<String, CellRecord>) sessionField("knownCells")
            .get(InteractiveTestSession.get());
        knownCells.put("good:Suite.placed", new CellRecord("good:Suite.placed", 0, 64, 0, 0, 64, 0, 4, 68, 4));

        List<String> completions = new HorizonQACommand()
            .addTabCompletionOptions(new RecordingSender(), new String[] { "tp", "" });

        assertTrue(completions.contains("good:Suite.placed"));
        assertFalse(completions.contains("good:Suite.notPlaced"));
    }

    @Test
    public void runKnownInvalidTestReportsInvalidInsteadOfUnknown() throws Exception {
        String invalidId = "bad:Broken.invalid";
        seedRegistry(
            Collections.singletonList(definition("good:Suite.valid")),
            Collections.singletonList(invalid(invalidId)));

        RecordingSender sender = new RecordingSender();

        new HorizonQACommand().processCommand(sender, new String[] { "run", invalidId });

        String messages = sender.messages();
        assertTrue(messages.contains("Invalid test"));
        assertTrue(messages.contains(invalidId));
        assertTrue(messages.contains("must be public static"));
        assertFalse(messages.contains("Unknown test"));
    }

    private static GameTestDefinition definition(String testId) throws Exception {
        return new GameTestDefinition(testId, dummyMethod(), "", 20, "", true, 0);
    }

    private static InvalidTestDefinition invalid(String testId) throws Exception {
        DiscoveryIssue issue = new DiscoveryIssue(
            "discovery:invalidTest:" + testId + ":modifiers",
            "DISCOVERY_ERROR",
            "Skipping @GameTest method 'invalid' in 'DummyTests': must be public static.");
        return new InvalidTestDefinition(testId, dummyMethod(), Collections.singletonList(issue));
    }

    private static Method dummyMethod() throws Exception {
        return DummyTests.class.getMethod("test", GameTestHelper.class);
    }

    @SuppressWarnings("unchecked")
    private static void seedRegistry(List<GameTestDefinition> validTests, List<InvalidTestDefinition> invalidTests)
        throws Exception {

        List<GameTestDefinition> allTests = (List<GameTestDefinition>) field("ALL_TESTS").get(null);
        allTests.clear();
        allTests.addAll(validTests);

        ((Map<String, List<Method>>) field("BEFORE_BATCH_METHODS").get(null)).clear();
        ((Map<String, List<Method>>) field("AFTER_BATCH_METHODS").get(null)).clear();

        field("lastDiscoveryResult").set(
            null,
            new DiscoveryResult(
                validTests,
                Collections.emptyMap(),
                Collections.emptyMap(),
                invalidTests,
                Collections.<InvalidBatchHook>emptyList(),
                Collections.<DuplicateTestId>emptyList(),
                Collections.<DiscoveryIssue>emptyList()));
    }

    private static Field field(String name) throws Exception {
        Field field = GameTestRegistry.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Field sessionField(String name) throws Exception {
        Field field = InteractiveTestSession.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public static final class DummyTests {

        public static void test(GameTestHelper helper) {}
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
