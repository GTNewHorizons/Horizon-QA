package com.gtnewhorizons.gametest.examples.tests;

import com.gtnewhorizons.gametest.api.GameTestHelper;
import com.gtnewhorizons.gametest.api.annotation.AfterBatch;
import com.gtnewhorizons.gametest.api.annotation.BeforeBatch;
import com.gtnewhorizons.gametest.api.annotation.GameTest;
import com.gtnewhorizons.gametest.api.annotation.GameTestHolder;

@GameTestHolder("gametestexamples")
public class BasicTests {

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
            .thenExecute(() -> helper.fail("Intentional failure"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    public static void immediatePass(GameTestHelper helper) {
        helper.succeed();
    }

    @GameTest(timeoutTicks = 10, required = false)
    public static void timeoutTest(GameTestHelper helper) {}
}
