package com.gtnewhorizons.horizonqa.api;

import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;

import com.gtnewhorizons.horizonqa.api.annotation.Experimental;

@Experimental
public enum Weather {

    CLEAR(false, false),
    RAIN(true, false),
    THUNDER(true, true);

    private final boolean raining;
    private final boolean thundering;

    Weather(boolean raining, boolean thundering) {
        this.raining = raining;
        this.thundering = thundering;
    }

    private static final int LOCKED_DURATION_TICKS = 1_000_000_000;

    public void applyTo(WorldServer world) {
        WorldInfo info = world.getWorldInfo();
        info.setRaining(raining);
        info.setThundering(thundering);
        info.setRainTime(LOCKED_DURATION_TICKS);
        info.setThunderTime(LOCKED_DURATION_TICKS);
    }
}
