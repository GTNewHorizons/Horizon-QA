package com.gtnewhorizons.horizonqa.examples.tests;

import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

/** A common holder verifies that a supplied custom menu does not prevent integrated-client startup */
@GameTestHolder("horizonqaexamples")
public final class ClientBootstrapTests {

    public static final String SELECTION = "horizonqaexamples:ClientBootstrapTests.commonTestStartsFromCustomMenu";
    public static volatile boolean customMenuSupplied;

    private ClientBootstrapTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 100)
    public static void commonTestStartsFromCustomMenu(GameTestHelper helper) {
        helper.assumeTrue(
            HorizonQAProperties.clientTestsEnabled() && SELECTION.equals(HorizonQAProperties.rawTests()),
            "Select this bootstrap fixture alone in an automated client");
        helper.afterTest(() -> customMenuSupplied = false);
        helper.assertTrue(customMenuSupplied, "The custom menu must have been supplied before world startup");
        helper.recordDiagnostic("customMenuSupplied=true; original common test ID executed");
        helper.succeed();
    }
}
