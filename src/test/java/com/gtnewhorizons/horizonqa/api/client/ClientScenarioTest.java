package com.gtnewhorizons.horizonqa.api.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.internal.GameTestDefinition;
import com.gtnewhorizons.horizonqa.internal.GameTestInstance;
import com.gtnewhorizons.horizonqa.internal.GameTestSequence;

public class ClientScenarioTest {

    @Test
    public void operationMetadataComesFromWrappersInsteadOfLabels() throws Exception {
        Fixture fixture = new Fixture();
        fixture.scenario.server("capture", () -> {})
            .capture("server operation")
            .awaitClient("wait", c -> {});
        assertEquals(
            "SERVER_ACTION",
            fixture.sequence.stepResults()
                .get(0)
                .operation());
        assertEquals(
            "SERVER",
            fixture.sequence.stepResults()
                .get(0)
                .executionSide());
        assertEquals(
            "CAPTURE",
            fixture.sequence.stepResults()
                .get(1)
                .operation());
        assertEquals(
            "CLIENT",
            fixture.sequence.stepResults()
                .get(1)
                .executionSide());
        assertEquals(
            "CLIENT_WAIT",
            fixture.sequence.stepResults()
                .get(2)
                .operation());
    }

    @Test
    public void customAsyncWorkIsDeferredAndOrdersServerSteps() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<Void> pending = new CompletableFuture<>();
        AtomicInteger submitted = new AtomicInteger();
        AtomicInteger following = new AtomicInteger();
        fixture.scenario.async("custom input", c -> {
            submitted.incrementAndGet();
            return pending;
        })
            .server("verify server result", following::incrementAndGet);
        assertEquals(0, submitted.get());
        fixture.tick();
        fixture.tick();
        assertEquals(1, submitted.get());
        assertEquals(0, following.get());
        pending.complete(null);
        assertEquals(0, following.get());
        fixture.tick();
        assertEquals(1, following.get());
    }

    @Test
    public void labelsAndTimeoutsApplyToOneStepAndKeepCallerSource() throws Exception {
        Fixture fixture = new Fixture();
        fixture.scenario.defaultTimeoutTicks(60)
            .step("custom label")
            .withinTicks(7)
            .key(30, 'a')
            .key(48, 'b');
        GameTestSequence.SequenceStepSnapshot first = fixture.sequence.getSteps()
            .get(0);
        assertEquals("custom label", first.label());
        assertTrue(
            first.source()
                .toString()
                .contains("ClientScenarioTest.java"));
        assertEquals(7, first.maxTicks());
        assertEquals(
            60,
            fixture.sequence.getSteps()
                .get(1)
                .maxTicks());
    }

    @Test
    public void targetLookupDoesNotRunWhileBuildingScenario() throws Exception {
        Fixture fixture = new Fixture();
        AtomicInteger resolved = new AtomicInteger();
        ClientTarget target = ClientTarget.of("moving control", c -> {
            resolved.incrementAndGet();
            return null;
        });
        fixture.scenario.click(target)
            .drag(target)
            .to(80, 90)
            .overFrames(4);
        assertEquals(0, resolved.get());
        assertEquals(
            2,
            fixture.sequence.getSteps()
                .size());
    }

    @Test
    public void invalidConfigurationFailsBeforeAddingSteps() throws Exception {
        Fixture fixture = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> fixture.scenario.withinTicks(0));
        assertThrows(IllegalArgumentException.class, () -> fixture.scenario.defaultTimeoutTicks(-1));
        assertThrows(
            IllegalStateException.class,
            () -> fixture.scenario.withinTicks(2)
                .server("sync", () -> {}));
        assertEquals(
            0,
            fixture.sequence.getSteps()
                .size());
    }

    @Test
    public void rawSequenceEscapeHatchUsesSameExecutionOwner() throws Exception {
        Fixture fixture = new Fixture();
        assertSame(fixture.sequence, fixture.scenario.serverSequence());
        fixture.scenario.succeed();
        assertThrows(IllegalStateException.class, () -> fixture.scenario.key(30, 'a'));
    }

    public static void runningTest(GameTestHelper helper) {}

    private static final class Fixture {

        private final GameTestInstance instance;
        private final GameTestSequence sequence;
        private final ClientScenario scenario;

        private Fixture() throws Exception {
            GameTestDefinition definition = new GameTestDefinition(
                "test:ClientScenario",
                ClientScenarioTest.class.getMethod("runningTest", GameTestHelper.class),
                "",
                200,
                "",
                true,
                0);
            instance = new GameTestInstance(definition, 0, 0, 0);
            instance.start(null);
            GameTestHelper helper = new GameTestHelper(instance, null, 0, 0, 0);
            sequence = helper.startSequence();
            scenario = new ClientScenario(sequence, new ClientTest(helper));
        }

        private void tick() {
            instance.tickStart();
            instance.tickEnd();
        }
    }
}
