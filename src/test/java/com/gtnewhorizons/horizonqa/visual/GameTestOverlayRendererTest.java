package com.gtnewhorizons.horizonqa.visual;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.GameTestInfrastructureException;
import com.gtnewhorizons.horizonqa.internal.GameTestDefinition;
import com.gtnewhorizons.horizonqa.internal.GameTestInstance;
import com.gtnewhorizons.horizonqa.report.CaseResult;

public class GameTestOverlayRendererTest {

    @Test
    public void templateInfrastructureErrorIsNotLabeledAsCleanup() throws Exception {
        GameTestInstance instance = instance("templateFailure");

        instance.start(null);

        String rendered = String.join(
            "\n",
            GameTestOverlayRenderer.buildLines(
                instance.getDefinition()
                    .getTestId(),
                instance.getStatus(),
                instance));
        assertTrue(rendered.contains(CaseResult.TEMPLATE_ERROR));
        assertTrue(rendered.contains("unsafe numeric ItemStack ID"));
        assertFalse(rendered.contains("Cleanup error"));
    }

    @Test
    public void genuineCleanupErrorKeepsCleanupLabel() throws Exception {
        GameTestInstance instance = instance("cleanupFailure");

        instance.start(null);

        String rendered = String.join(
            "\n",
            GameTestOverlayRenderer.buildLines(
                instance.getDefinition()
                    .getTestId(),
                instance.getStatus(),
                instance));
        assertTrue(rendered.contains(CaseResult.CLEANUP_ERROR));
        assertTrue(rendered.contains("cleanup broke"));
        assertFalse(rendered.contains(CaseResult.TEMPLATE_ERROR));
    }

    @Test
    public void nullInfrastructureKindDoesNotBreakOverlay() throws Exception {
        GameTestInstance instance = instance("nullKindFailure");

        instance.start(null);

        String rendered = String.join(
            "\n",
            GameTestOverlayRenderer.buildLines(
                instance.getDefinition()
                    .getTestId(),
                instance.getStatus(),
                instance));
        assertTrue(rendered.contains("kindless infrastructure error"));
    }

    private static GameTestInstance instance(String methodName) throws Exception {
        Method method = TestDefinitions.class.getMethod(methodName, GameTestHelper.class);
        GameTestDefinition definition = new GameTestDefinition(
            "horizonqatest:Overlay." + methodName,
            method,
            "",
            20,
            "default",
            true,
            0);
        return new GameTestInstance(definition, 0, 64, 0);
    }

    public static final class TestDefinitions {

        public static void templateFailure(GameTestHelper helper) {
            throw new GameTestInfrastructureException(
                CaseResult.TEMPLATE_ERROR,
                "format_version 1 contains an unsafe numeric ItemStack ID");
        }

        public static void cleanupFailure(GameTestHelper helper) {
            helper.afterTest(() -> { throw new AssertionError("cleanup broke"); });
            helper.succeed();
        }

        public static void nullKindFailure(GameTestHelper helper) {
            throw new GameTestInfrastructureException(null, "kindless infrastructure error");
        }
    }
}
