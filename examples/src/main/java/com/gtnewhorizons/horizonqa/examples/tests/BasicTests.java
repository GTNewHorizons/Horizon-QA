package com.gtnewhorizons.horizonqa.examples.tests;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.AfterBatch;
import com.gtnewhorizons.horizonqa.api.annotation.BeforeBatch;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.examples.ExamplesMod;

@GameTestHolder(ExamplesMod.MODID)
public class BasicTests {

    private BasicTests() {}

    @BeforeBatch("")
    public static void setupBatch() {}

    @AfterBatch("")
    public static void teardownBatch() {}

    @GameTest(timeoutTicks = 40)
    public static void simplePass(GameTestHelper helper) {
        helper.startSequence()
            .thenIdle(10)
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20, required = false)
    public static void simpleFail(GameTestHelper helper) {
        helper.startSequence()
            .thenExecute("demonstrate sequence failure context", () -> helper.fail("Intentional failure"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    public static void immediatePass(GameTestHelper helper) {
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    public static void runtimeAssumptionSkip(GameTestHelper helper) {
        helper.assumeTrue(false, "Example-only runtime capability is unavailable");
        throw new AssertionError("A failed assumption must abort the test method");
    }

    @GameTest(timeoutTicks = 10, required = false)
    public static void timeoutTest(GameTestHelper helper) {}
}
