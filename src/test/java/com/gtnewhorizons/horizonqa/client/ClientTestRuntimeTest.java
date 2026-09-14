package com.gtnewhorizons.horizonqa.client;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import net.minecraft.client.audio.SoundCategory;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.report.RunResult;

public class ClientTestRuntimeTest {

    @Test
    public void mutesOnlyMobSoundsUntilTheRunFinishes() {
        assertEquals("hostile", SoundCategory.MOBS.getCategoryName());
        assertEquals("neutral", SoundCategory.ANIMALS.getCategoryName());
        assertEquals(0.65F, ClientTestRuntime.soundLevel(SoundCategory.MOBS, 0.65F), 0.0F);
        assertEquals(0.65F, ClientTestRuntime.soundLevel(SoundCategory.ANIMALS, 0.65F), 0.0F);
        ClientTestRuntime runtime = new ClientTestRuntime(() -> {});
        try {
            for (SoundCategory category : SoundCategory.values()) {
                assertEquals(
                    category == SoundCategory.MOBS || category == SoundCategory.ANIMALS ? 0.0F : 0.65F,
                    ClientTestRuntime.soundLevel(category, 0.65F),
                    0.0F);
            }
        } finally {
            runtime.finish(RunResult.preRun("ci", Collections.emptyList(), ""));
        }
        assertEquals(0.65F, ClientTestRuntime.soundLevel(SoundCategory.MOBS, 0.65F), 0.0F);
        assertEquals(0.65F, ClientTestRuntime.soundLevel(SoundCategory.ANIMALS, 0.65F), 0.0F);
    }
}
