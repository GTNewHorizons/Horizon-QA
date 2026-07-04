package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.TreeMap;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.LabelResolutionException;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.report.CaseResult;
import com.gtnewhorizons.horizonqa.structure.HybridStructureTemplate;
import com.gtnewhorizons.horizonqa.structure.StructureAnnotations;

public class GameTestLabelTest {

    @Test
    public void resolveLabelAppliesStructureRotation() throws Exception {
        GameTestInstance instance = instance("mod:Labels.noop", "noop", 1);

        assertEquals(new TestPos(0, 0, 1), instance.resolveLabel("corner"));
        assertEquals(new TestPos(2, 0, 0), instance.resolveLabel("origin"));
    }

    @Test
    public void missingLabelIsInfrastructureError() throws Exception {
        GameTestInstance instance = instance("mod:Labels.missingLabel", "missingLabel", 0);

        instance.start(null);

        assertEquals(GameTestStatus.ERROR, instance.getStatus());
        assertTrue(instance.getFailureCause() instanceof LabelResolutionException);

        CaseResult result = CaseResult.from(instance);
        assertEquals(CaseResult.Status.ERROR, result.status());
        assertEquals(LabelResolutionException.KIND, result.failureType());
        assertTrue(
            result.failureMessage()
                .contains("Unknown label 'missing'"));
        assertTrue(
            result.failureMessage()
                .contains("available: corner, origin"));
    }

    private static GameTestInstance instance(String id, String methodName, int rotation) throws Exception {
        Method method = TestDefinitions.class.getMethod(methodName, GameTestHelper.class);
        GameTestDefinition definition = new GameTestDefinition(id, method, "horizonqatest:annotated", 100, "", true, rotation);
        return new GameTestInstance(definition, 10, 64, 30, template());
    }

    private static HybridStructureTemplate template() {
        TreeMap<String, TestPos> labels = new TreeMap<>();
        labels.put("corner", new TestPos(1, 0, 2));
        labels.put("origin", new TestPos(0, 0, 0));
        HybridStructureTemplate.PaletteEntry[] palette = {
            new HybridStructureTemplate.PaletteEntry("minecraft:air", 0) };
        return new HybridStructureTemplate(
            2,
            1,
            3,
            palette,
            new char[] { HybridStructureTemplate.AIR_KEY },
            new int[2][1][3],
            new NBTTagCompound(),
            new NBTTagCompound(),
            new StructureAnnotations(labels));
    }

    public static final class TestDefinitions {

        public static void noop(GameTestHelper helper) {}

        public static void missingLabel(GameTestHelper helper) {
            helper.pos("missing");
        }
    }
}
