package com.gtnewhorizons.horizonqa.api.event.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EventStateTest {

    @Test
    public void maintenanceSnapshotTracksIssuesAndNamesFlags() {
        MaintenanceSnapshot prior = new MaintenanceSnapshot(MaintenanceSnapshot.WRENCH);
        MaintenanceSnapshot current = new MaintenanceSnapshot(
            MaintenanceSnapshot.WRENCH | MaintenanceSnapshot.CROWBAR | MaintenanceSnapshot.SOFT_MALLET);

        assertTrue(current.has(MaintenanceSnapshot.WRENCH));
        assertFalse(current.has(MaintenanceSnapshot.HARD_HAMMER));
        assertEquals(MaintenanceSnapshot.CROWBAR | MaintenanceSnapshot.SOFT_MALLET, current.newlySetSince(prior));
        assertEquals("WRENCH", MaintenanceSnapshot.nameOf(MaintenanceSnapshot.WRENCH));
        assertEquals("SCREWDRIVER", MaintenanceSnapshot.nameOf(MaintenanceSnapshot.SCREWDRIVER));
        assertEquals("SOFT_MALLET", MaintenanceSnapshot.nameOf(MaintenanceSnapshot.SOFT_MALLET));
        assertEquals("HARD_HAMMER", MaintenanceSnapshot.nameOf(MaintenanceSnapshot.HARD_HAMMER));
        assertEquals("SOLDERING_TOOL", MaintenanceSnapshot.nameOf(MaintenanceSnapshot.SOLDERING_TOOL));
        assertEquals("CROWBAR", MaintenanceSnapshot.nameOf(MaintenanceSnapshot.CROWBAR));
        assertEquals("UNKNOWN(128)", MaintenanceSnapshot.nameOf(128));
        assertEquals(0, MaintenanceSnapshot.OK.mask());
    }

    @Test
    public void recipeAndHatchSnapshotsExposeCompactState() {
        assertFalse(RecipeStateSnapshot.EMPTY.isActive());
        RecipeStateSnapshot active = new RecipeStateSnapshot(true, 3, 20, -32, 10_000, "success", 2);
        assertTrue(active.isActive());
        assertEquals(3, active.progressTime());
        assertEquals("success", active.checkRecipeResultId());

        assertEquals("0ib/0ob/0ih/0oh/0eh", HatchTopology.EMPTY.compact());
        assertEquals("1ib/2ob/3ih/4oh/5eh", new HatchTopology(1, 2, 3, 4, 5).compact());
    }

    @Test
    public void causeEnumsExposeAllDocumentedValues() {
        assertEquals(4, DeformedCause.values().length);
        assertEquals(4, ExplodedCause.values().length);
        assertEquals(3, FormedCause.values().length);
    }
}
