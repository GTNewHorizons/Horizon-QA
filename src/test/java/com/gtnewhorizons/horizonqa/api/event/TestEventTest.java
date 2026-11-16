package com.gtnewhorizons.horizonqa.api.event;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.event.state.DeformedCause;
import com.gtnewhorizons.horizonqa.api.event.state.ExplodedCause;
import com.gtnewhorizons.horizonqa.api.event.state.FormedCause;
import com.gtnewhorizons.horizonqa.api.event.state.HatchTopology;

public class TestEventTest {

    private static final TestPos POS = new TestPos(1, 2, 3);
    private static final String POS_TEXT = POS.toString();
    public static final int TICK = 7;

    @Test
    public void summariesAreStableReporterOutput() {
        assertEvent(
            new AfterBatchRan(TICK, "main", "tearDown"),
            TestEvent.Category.LIFECYCLE,
            "@AfterBatch 'tearDown' ran for batch 'main'");
        assertEvent(
            new BeforeBatchRan(TICK, "main", "setUp"),
            TestEvent.Category.LIFECYCLE,
            "@BeforeBatch 'setUp' ran for batch 'main'");
        assertEvent(
            new BusInserted(TICK, POS, "Iron Ingot", 3),
            TestEvent.Category.RESOURCE,
            "Inserted 3× Iron Ingot into " + POS_TEXT);
        assertEvent(
            new CleanroomEfficiencyChanged(TICK, POS, 9876),
            TestEvent.Category.DIAGNOSTIC,
            "Cleanroom efficiency at " + POS_TEXT + ": 98.76 %");
        assertEvent(
            new EUBufferOverflow(TICK, POS, 32, 0),
            TestEvent.Category.ENERGY,
            "EU supply rejected at " + POS_TEXT
                + ": buffer at capacity, 32 EU/t push dropped (further rejections for this job suppressed)");
        assertEvent(
            new EUSupplyJobRegistered(TICK, POS, 32, 2, 20),
            TestEvent.Category.ENERGY,
            "EU supply job: 32 EU/t × 2 A for 20t into " + POS_TEXT);
        assertEvent(
            new EventOverflow(TICK, 256),
            TestEvent.Category.DIAGNOSTIC,
            "Event log truncated at cap=256 — further events dropped");
        assertEvent(
            new HatchFilled(TICK, POS, "Water", 1000, 750),
            TestEvent.Category.RESOURCE,
            "Filled 750/1000 mB of 'Water' into " + POS_TEXT);
        assertEvent(
            new HatchVoltageMismatch(TICK, POS, 128, 32),
            TestEvent.Category.ENERGY,
            "Hatch voltage mismatch at " + POS_TEXT + ": supplied 128 EU/t > hatch max 32 EU/t");
        assertEvent(
            new MachineDeformed(TICK, POS, DeformedCause.BLOCK_CHANGED),
            TestEvent.Category.STRUCTURE,
            "Multiblock deformed at " + POS_TEXT + " (BLOCK_CHANGED)");
        assertEvent(
            new MachineDisabled(TICK, POS, "manual"),
            TestEvent.Category.STRUCTURE,
            "Machine disabled at " + POS_TEXT + " (manual)");
        assertEvent(
            new MachineExploded(TICK, POS, ExplodedCause.OVERVOLTAGE),
            TestEvent.Category.SAFETY,
            "Machine exploded at " + POS_TEXT + " (OVERVOLTAGE)");
        assertEvent(
            new MachineFormed(TICK, POS, "EBF", FormedCause.FORCED_BY_ASSERTION, new HatchTopology(1, 2, 3, 4, 5)),
            TestEvent.Category.STRUCTURE,
            "EBF formed at " + POS_TEXT + " (FORCED_BY_ASSERTION, 1ib/2ob/3ih/4oh/5eh)");
        assertEvent(
            new MaintenanceFixed(TICK, POS, "WRENCH"),
            TestEvent.Category.MAINTENANCE,
            "Maintenance fixed at " + POS_TEXT + " (WRENCH)");
        assertEvent(
            new MaintenanceIssueAppeared(TICK, POS, "CROWBAR"),
            TestEvent.Category.MAINTENANCE,
            "Maintenance issue 'CROWBAR' appeared at " + POS_TEXT);
        assertEvent(
            new PollutionEmitted(TICK, POS, 10, 25),
            TestEvent.Category.DIAGNOSTIC,
            "Pollution emitted at " + POS_TEXT + ": 10 (cumulative 25)");
        assertEvent(
            new ProgrammedCircuitSet(TICK, POS, 4),
            TestEvent.Category.RESOURCE,
            "Programmed circuit set to 4 in " + POS_TEXT);
        assertEvent(
            new RecipeAborted(TICK, POS, 5, 20, "disabled"),
            TestEvent.Category.RECIPE,
            "Recipe aborted at " + POS_TEXT + " (5/20t, reason=disabled)");
        assertEvent(
            new RecipeFinished(TICK, POS, 20),
            TestEvent.Category.RECIPE,
            "Recipe finished at " + POS_TEXT + " (took 20t)");
        assertEvent(
            new RecipeNotFound(TICK, POS, "no_recipe"),
            TestEvent.Category.RECIPE,
            "No recipe ran at " + POS_TEXT + " (last check result: no_recipe)");
        assertEvent(
            new RecipeProgressed(TICK, POS, 5, 20, 25),
            TestEvent.Category.RECIPE,
            "Recipe at " + POS_TEXT + " 25% (5/20)");
        assertEvent(
            new RecipeStarted(TICK, POS, -32, 20, 2),
            TestEvent.Category.RECIPE,
            "Recipe started at " + POS_TEXT + " (-32 EU/t × 20t, 2p)");
        assertEvent(
            new StructureCheckRan(TICK, POS, true, false),
            TestEvent.Category.STRUCTURE,
            "checkStructure(forceReset=true) at " + POS_TEXT + " → still unformed");
        assertEvent(
            new StructurePlaced(TICK, "mod:fixture", POS, 3, 4, 5),
            TestEvent.Category.LIFECYCLE,
            "Placed template 'mod:fixture' at " + POS_TEXT + " (3×4×5)");
        assertEvent(
            new TestFinished(TICK, "mod:Suite.test", "passed", 20),
            TestEvent.Category.LIFECYCLE,
            "Test mod:Suite.test passed after 20 simulated tick(s)");
        assertEvent(
            new TestRecipeInjected(TICK, POS, "furnace", 32, 20),
            TestEvent.Category.RECIPE,
            "Test recipe injected into furnace for " + POS_TEXT + " (32 EU/t × 20t)");
        assertEvent(
            new TestRecipeRemoved(TICK, POS, "furnace"),
            TestEvent.Category.RECIPE,
            "Test recipe removed from furnace for " + POS_TEXT);
        assertEvent(
            new TestStarted(TICK, "mod:Suite.test", POS),
            TestEvent.Category.LIFECYCLE,
            "Test mod:Suite.test started at " + POS_TEXT);
        assertEvent(
            new TickCallbackStateChanged(TICK, "watch output", "registered-enabled"),
            TestEvent.Category.DIAGNOSTIC,
            "Per-tick callback 'watch output' registered enabled");
        assertEvent(
            new WarpFinished(TICK, 20, "idle"),
            TestEvent.Category.LIFECYCLE,
            "Time-warp finished after 20 simulated tick(s) (idle)");
        assertEvent(
            new WarpStarted(TICK, 100, 2),
            TestEvent.Category.LIFECYCLE,
            "Time-warp started (maxTicks=100, watching 2 controller(s))");
    }

    @Test
    public void optionalFailureDetailsRenderOnlyWhenPresent() {
        assertEvent(
            new AssertionFailed(TICK, "bad output", "AssertionError", POS),
            TestEvent.Category.FAILURE,
            "Assertion failed at " + POS_TEXT + ": bad output");
        assertEvent(
            new AssertionFailed(TICK, "bad output", "AssertionError", null),
            TestEvent.Category.FAILURE,
            "Assertion failed: bad output");
        assertEvent(
            new IsolationViolation(TICK, "Item", POS, "outside cell"),
            TestEvent.Category.FAILURE,
            "Isolation violation: Item at " + POS_TEXT + " — outside cell");
        assertEvent(
            new IsolationViolation(TICK, "Item", null, ""),
            TestEvent.Category.FAILURE,
            "Isolation violation: Item");
        assertEvent(
            new RecipeNotFound(TICK, POS, null),
            TestEvent.Category.RECIPE,
            "No recipe ran at " + POS_TEXT + " (last check result: unknown)");
        assertEvent(
            new StructureCheckRan(TICK, POS, false, true),
            TestEvent.Category.STRUCTURE,
            "checkStructure(forceReset=false) at " + POS_TEXT + " → formed");
    }

    private static void assertEvent(TestEvent event, String category, String summary) {
        assertEquals(TICK, event.tick());
        assertEquals(category, event.category());
        assertEquals(summary, event.summary());
    }
}
