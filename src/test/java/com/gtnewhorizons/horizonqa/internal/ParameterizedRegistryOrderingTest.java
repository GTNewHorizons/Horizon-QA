package com.gtnewhorizons.horizonqa.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.GameTestArguments;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.annotation.MethodSource;

import cpw.mods.fml.common.discovery.ASMDataTable;

public class ParameterizedRegistryOrderingTest {

    @Test
    public void registryPreservesMethodSourceEncounterOrder() {
        GameTestCatalog catalog = GameTestRegistry
            .discoverTests(holderAsmData(EncounterOrderTests.class), modId -> true);

        assertTrue(
            catalog.diagnostics()
                .issues()
                .isEmpty());
        assertTrue(
            catalog.diagnostics()
                .duplicateIds()
                .isEmpty());
        assertEquals(
            Arrays.asList(
                "matrix:EncounterOrderTests.inSourceOrder[zeta]",
                "matrix:EncounterOrderTests.inSourceOrder[alpha]",
                "matrix:EncounterOrderTests.inSourceOrder[middle]"),
            testIds(catalog.tests()));
        assertEquals(
            Arrays.asList(0, 1, 2),
            Arrays.asList(
                catalog.tests()
                    .get(0)
                    .getCaseOrdinal(),
                catalog.tests()
                    .get(1)
                    .getCaseOrdinal(),
                catalog.tests()
                    .get(2)
                    .getCaseOrdinal()));
    }

    @Test
    public void duplicateBaseIdsAreRejectedEvenWhenCaseKeysDoNotOverlap() {
        GameTestCatalog catalog = GameTestRegistry
            .discoverTests(holderAsmData(OverloadedParameterizedTests.class), modId -> true);

        assertTrue(
            catalog.tests()
                .isEmpty());
        assertEquals(
            1,
            catalog.diagnostics()
                .duplicateIds()
                .size());
        assertEquals(
            "matrix:OverloadedParameterizedTests.collides",
            catalog.diagnostics()
                .duplicateIds()
                .get(0)
                .testId());
        String diagnostic = catalog.diagnostics()
            .issues()
            .get(0)
            .message();
        assertTrue(diagnostic.contains(GameTestHelper.class.getName()));
        assertTrue(diagnostic.contains(String.class.getName()));
    }

    @Test
    public void duplicateBaseIdsAreRejectedBeforeEitherSourceIsResolved() {
        MixedValidityDuplicateTests.validProviderInvoked = false;
        GameTestCatalog catalog = GameTestRegistry
            .discoverTests(holderAsmData(MixedValidityDuplicateTests.class), modId -> true);

        assertTrue(
            catalog.tests()
                .isEmpty());
        assertEquals(
            1,
            catalog.diagnostics()
                .duplicateIds()
                .size());
        assertTrue(
            catalog.diagnostics()
                .invalidTests()
                .isEmpty());
        assertFalse(MixedValidityDuplicateTests.validProviderInvoked);
    }

    private static List<String> testIds(List<GameTestDefinition> definitions) {
        java.util.ArrayList<String> ids = new java.util.ArrayList<>(definitions.size());
        for (GameTestDefinition definition : definitions) {
            ids.add(definition.getTestId());
        }
        return ids;
    }

    private static ASMDataTable holderAsmData(Class<?> holderClass) {
        ASMDataTable table = new ASMDataTable();
        table.addASMData(
            null,
            GameTestHolder.class.getName(),
            holderClass.getName(),
            holderClass.getName(),
            Collections.emptyMap());
        return table;
    }

    @GameTestHolder("matrix")
    public static final class EncounterOrderTests {

        @GameTest
        @MethodSource("rows")
        public static void inSourceOrder(GameTestHelper helper, int value) {}

        public static List<GameTestArguments> rows() {
            return Arrays.asList(
                GameTestArguments.named("zeta", 0),
                GameTestArguments.named("alpha", 1),
                GameTestArguments.named("middle", 2));
        }
    }

    @GameTestHolder("matrix")
    public static final class OverloadedParameterizedTests {

        @GameTest
        @MethodSource("integerRows")
        public static void collides(GameTestHelper helper, int value) {}

        @GameTest
        @MethodSource("stringRows")
        public static void collides(GameTestHelper helper, String value) {}

        public static List<GameTestArguments> integerRows() {
            return Collections.singletonList(GameTestArguments.named("integer", 1));
        }

        public static List<GameTestArguments> stringRows() {
            return Collections.singletonList(GameTestArguments.named("string", "one"));
        }
    }

    @GameTestHolder("matrix")
    public static final class MixedValidityDuplicateTests {

        static boolean validProviderInvoked;

        @GameTest
        @MethodSource("validRows")
        public static void collides(GameTestHelper helper, int value) {}

        @GameTest
        @MethodSource("missingRows")
        public static void collides(GameTestHelper helper, String value) {}

        public static List<GameTestArguments> validRows() {
            validProviderInvoked = true;
            return Collections.singletonList(GameTestArguments.named("valid", 1));
        }
    }
}
