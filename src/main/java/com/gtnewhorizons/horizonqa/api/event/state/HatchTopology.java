package com.gtnewhorizons.horizonqa.api.event.state;

import com.github.bsideup.jabel.Desugar;

/** Counts of the hatch lists exposed by {@code MTEMultiBlockBase}. Used for {@code MachineFormed} payloads. */
@Desugar
public record HatchTopology(int inputBuses, int outputBuses, int inputHatches, int outputHatches, int energyHatches) {

    public static final HatchTopology EMPTY = new HatchTopology(0, 0, 0, 0, 0);

    public String compact() {
        return inputBuses + "ib/"
            + outputBuses
            + "ob/"
            + inputHatches
            + "ih/"
            + outputHatches
            + "oh/"
            + energyHatches
            + "eh";
    }
}
