package com.gtnewhorizons.horizonqa.api.gt;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.horizonqa.api.GameTestAssertException;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

/**
 * Collection of {@link Bus} instances from {@link Multiblock#inputs()} or {@link Multiblock#outputs()}.
 */
@Experimental
public final class BusGroup {

    private final List<Bus> buses = new ArrayList<>();
    private final String label;
    private final TestPos controllerPos;

    BusGroup(String label, TestPos controllerPos) {
        this.label = label;
        this.controllerPos = controllerPos;
    }

    void add(Bus bus) {
        buses.add(bus);
    }

    /** Passes when some slot in some bus contains {@code stack} (item, damage, and NBT match; stack size ignored). */
    public void assertContains(ItemStack stack) {
        assertContains(ItemMatcher.of(stack));
    }

    /** Passes when some slot in some bus matches {@code matcher}. */
    public void assertContains(ItemMatcher matcher) {
        for (Bus bus : buses) {
            for (int i = 0; i < bus.size(); i++) {
                ItemStack slot = bus.slot(i);
                if (slot != null && matcher.matches(slot)) return;
            }
        }
        throw new GameTestAssertException(
            label + " does not contain " + matcher + " in any bus",
            controllerPos.x(),
            controllerPos.y(),
            controllerPos.z());
    }

    public void assertEmpty() {
        for (Bus bus : buses) {
            bus.assertEmpty();
        }
    }
}
