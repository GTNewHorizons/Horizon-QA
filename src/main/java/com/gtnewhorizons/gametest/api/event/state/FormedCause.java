package com.gtnewhorizons.gametest.api.event.state;

import com.gtnewhorizons.gametest.api.annotation.Experimental;

/** Why a {@code MachineFormed} event was emitted. Lets readers distinguish the three legitimate sources. */
@Experimental
public enum FormedCause {

    /** Controller already reported {@code mMachine == true} the first time the recorder polled it. */
    OBSERVED_ON_FIRST_POLL,
    /** {@code mMachine} transitioned false → true during a time-warp pass. */
    FORMED_DURING_WARP,
    /** {@code assertFormed} called {@code checkStructure(true)} and the controller then reported formed. */
    FORCED_BY_ASSERTION
}
