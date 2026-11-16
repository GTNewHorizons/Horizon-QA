package com.gtnewhorizons.horizonqa.examples.tests;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.AfterBatch;
import com.gtnewhorizons.horizonqa.api.annotation.BeforeBatch;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.examples.ExamplesMod;

@GameTestHolder(ExamplesMod.MODID)
public class ReportedRunLifecycleTests {

    private static boolean batchMutation;

    private ReportedRunLifecycleTests() {}

    @BeforeBatch("lifecycle_cleanup")
    public static void applyGlobalMutation() {
        batchMutation = true;
    }

    @BeforeBatch("lifecycle_cleanup")
    public static void failSetupAfterMutation() {
        throw new AssertionError("Intentional before-hook failure verifies owed cleanup");
    }

    @AfterBatch("lifecycle_cleanup")
    public static void restoreGlobalMutation() {
        batchMutation = false;
    }

    @GameTest(batch = "lifecycle_cleanup")
    public static void blockedByBeforeFailure(GameTestHelper helper) {
        helper.fail("A failed before-hook must block this test");
    }

    @GameTest(batch = "lifecycle_verify")
    public static void cleanupWasRestored(GameTestHelper helper) {
        helper.assertFalse(batchMutation, "The prior batch's after-hook must restore its global mutation");
        helper.succeed();
    }
}
