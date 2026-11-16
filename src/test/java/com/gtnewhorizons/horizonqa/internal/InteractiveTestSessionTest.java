package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Set;

import org.junit.After;
import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;
import com.gtnewhorizons.horizonqa.report.CaseResult;
import com.gtnewhorizons.horizonqa.structure.HybridStructureLoader;

public class InteractiveTestSessionTest {

    @After
    public void tearDown() {
        GameTestRunner.shutdown();
        InteractiveTestSession.reset();
    }

    @Test
    public void preparationFailureCreatesVisibleTemplateErrorMarker() {
        GameTestDefinition definition = definition("horizonqatest:legacy_numeric_stack");
        IOException error = assertThrows(
            IOException.class,
            () -> HybridStructureLoader.load(definition.getTemplateName()));
        InteractiveTestSession session = InteractiveTestSession.get();
        GameTestInfrastructureException failure = new GameTestInfrastructureException(
            CaseResult.TEMPLATE_ERROR,
            error.getMessage());
        failure.initCause(error);
        TestCell cell = new TestCell(definition.getTestId(), 0, 64, 0, 0, 64, 0, 4, 68, 4);

        session.recordPreparationFailure(FixturePreparation.Result.failed(definition, cell, failure));

        assertEquals(
            1,
            session.getKnownCells()
                .size());
        assertSame(
            cell,
            session.getKnownCells()
                .iterator()
                .next());
        GameTestInstance instance = session.getLastInstance(definition.getTestId());
        assertNotNull(instance);
        assertEquals(GameTestStatus.ERROR, instance.getStatus());
        assertNull(instance.getCleanupFailureCause());
        assertTrue(instance.getFailureCause() instanceof GameTestInfrastructureException);
        GameTestInfrastructureException recordedFailure = (GameTestInfrastructureException) instance.getFailureCause();
        assertEquals(CaseResult.TEMPLATE_ERROR, recordedFailure.kind());
        assertTrue(
            recordedFailure.getMessage()
                .contains("unsafe numeric ItemStack ID"));
        assertTrue(
            recordedFailure.getMessage()
                .contains("$.entities[0].Item"));
        assertEquals(
            CaseResult.TEMPLATE_ERROR,
            CaseResult.from(instance)
                .failureType());
        assertFalse(worldOwnedCellIds(session).contains(definition.getTestId()));
    }

    @Test
    public void resetReleasesInteractiveOwnership() {
        InteractiveTestSession session = InteractiveTestSession.get();
        GameTestRunner runner = runner(session);
        assertTrue(runner.tryStart(GameTestRunner.Kind.INTERACTIVE, () -> runner.scheduleOnFirstTick(() -> {})));

        InteractiveTestSession.reset();

        assertTrue(new GameTestRunner().tryStart(GameTestRunner.Kind.BATCH, () -> {}));
    }

    @Test
    public void interactiveLaunchCannotReplaceReportedBatch() {
        GameTestDefinition definition = definition("horizonqatest:empty");
        GameTestRunner batch = new GameTestRunner();
        assertTrue(batch.tryStart(GameTestRunner.Kind.BATCH, () -> batch.scheduleOnFirstTick(() -> {})));

        assertEquals(
            0,
            InteractiveTestSession.get()
                .launchTest(definition));
        assertTrue(GameTestRunner.isBatchActive());
    }

    @SuppressWarnings("unchecked")
    private static Set<String> worldOwnedCellIds(InteractiveTestSession session) {
        try {
            Field field = InteractiveTestSession.class.getDeclaredField("worldOwnedCellIds");
            field.setAccessible(true);
            return (Set<String>) field.get(session);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static GameTestRunner runner(InteractiveTestSession session) {
        try {
            Field field = InteractiveTestSession.class.getDeclaredField("runner");
            field.setAccessible(true);
            return (GameTestRunner) field.get(session);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static GameTestDefinition definition(String templateName) {
        return new GameTestDefinition("horizonqatest:Interactive.missing", null, templateName, 20, "default", true, 0);
    }
}
