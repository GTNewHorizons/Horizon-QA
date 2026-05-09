package com.gtnewhorizons.gametest.api.gt;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.gametest.api.GameTestAssertException;
import com.gtnewhorizons.gametest.api.annotation.Experimental;

/**
 * Collection of {@link Bus} instances from {@link Multiblock#inputs()} or {@link Multiblock#outputs()}.
 */
@Experimental
public final class BusGroup {

    private final List<Bus> buses = new ArrayList<>();
    private final String label;

    BusGroup(String label) {
        this.label = label;
    }

    void add(Bus bus) {
        buses.add(bus);
    }

    /** Passes when some slot in some bus matches {@code matcher}. */
    public void assertContains(ItemMatcher matcher) {
        for (Bus bus : buses) {
            for (int i = 0; i < bus.size(); i++) {
                ItemStack slot = bus.slot(i);
                if (slot != null && matcher.matches(slot)) return;
            }
        }
        throw new GameTestAssertException(label + " does not contain " + matcher + " in any bus", 0, 0, 0);
    }

    public void assertEmpty() {
        for (Bus bus : buses) {
            bus.assertEmpty();
        }
    }
}
