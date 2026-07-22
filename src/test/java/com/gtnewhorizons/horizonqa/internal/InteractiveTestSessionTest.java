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
import com.gtnewhorizons.horizonqa.command.HorizonQACommandUtils.CellRecord;
import com.gtnewhorizons.horizonqa.report.CaseResult;

public class InteractiveTestSessionTest {

    @After
    public void tearDown() {
        InteractiveTestSession.reset();
    }

    @Test
    public void explicitlyEmptyTemplateRemainsAnEmptyFixture() throws Exception {
        assertNull(InteractiveTestSession.loadTemplate(definition("")));
    }

    @Test
    public void templateLoadFailureIsNotCollapsedIntoAnEmptyFixture() {
        assertThrows(
            IOException.class,
            () -> InteractiveTestSession.loadTemplate(definition("horizonqatest:missing_interactive_template")));
    }

    @Test
    public void legacyTemplateFailureCreatesVisibleTemplateErrorMarker() {
        GameTestDefinition definition = definition("horizonqatest:legacy_numeric_stack");
        IOException error = assertThrows(IOException.class, () -> InteractiveTestSession.loadTemplate(definition));
        InteractiveTestSession session = InteractiveTestSession.get();

        session.recordTemplateLoadFailure(definition, new IOException("previous marker"));
        CellRecord existingCell = session.getKnownCells()
            .iterator()
            .next();
        session.recordTemplateLoadFailure(definition, error);

        assertEquals(
            1,
            session.getKnownCells()
                .size());
        assertSame(
            existingCell,
            session.getKnownCells()
                .iterator()
                .next());
        GameTestInstance instance = session.getLastInstance(definition.getTestId());
        assertNotNull(instance);
        assertEquals(GameTestStatus.ERROR, instance.getStatus());
        assertNull(instance.getCleanupFailureCause());
        assertTrue(instance.getFailureCause() instanceof GameTestInfrastructureException);
        GameTestInfrastructureException failure = (GameTestInfrastructureException) instance.getFailureCause();
        assertEquals(CaseResult.TEMPLATE_ERROR, failure.kind());
        assertTrue(
            failure.getMessage()
                .contains("unsafe numeric ItemStack ID"));
        assertTrue(
            failure.getMessage()
                .contains("$.entities[0].Item"));
        assertFalse(
            failure.getMessage()
                .contains("previous marker"));
        assertEquals(
            CaseResult.TEMPLATE_ERROR,
            CaseResult.from(instance)
                .failureType());
        assertFalse(worldOwnedCellIds(session).contains(definition.getTestId()));
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

    private static GameTestDefinition definition(String templateName) {
        return new GameTestDefinition("horizonqatest:Interactive.missing", null, templateName, 20, "default", true, 0);
    }
}
