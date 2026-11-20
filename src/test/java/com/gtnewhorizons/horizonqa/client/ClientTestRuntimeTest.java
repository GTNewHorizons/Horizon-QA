package com.gtnewhorizons.horizonqa.client;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import net.minecraft.client.audio.SoundCategory;

import org.junit.Test;

import com.gtnewhorizons.horizonqa.report.RunResult;

public class ClientTestRuntimeTest {

    @Test
    public void manualLaboratoryUsesQuietVolumesWithoutChangingOtherCategories() {
        String previous = System.getProperty("horizonqa.manualSession");
        System.setProperty("horizonqa.manualSession", "horizonqaexamples:ManualLaboratoryExample.prepare");
        ClientTestRuntime runtime = new ClientTestRuntime(() -> {});
        try {
            assertEquals(0.0F, ClientTestRuntime.soundLevel(SoundCategory.MUSIC, 0.65F), 0.0F);
            assertEquals(0.0F, ClientTestRuntime.soundLevel(SoundCategory.WEATHER, 0.65F), 0.0F);
            assertEquals(0.0F, ClientTestRuntime.soundLevel(SoundCategory.MOBS, 0.65F), 0.0F);
            assertEquals(0.0F, ClientTestRuntime.soundLevel(SoundCategory.ANIMALS, 0.65F), 0.0F);
            assertEquals(0.5F, ClientTestRuntime.soundLevel(SoundCategory.BLOCKS, 0.65F), 0.0F);
            assertEquals(0.5F, ClientTestRuntime.soundLevel(SoundCategory.MASTER, 0.65F), 0.0F);
            assertEquals(0.65F, ClientTestRuntime.soundLevel(SoundCategory.PLAYERS, 0.65F), 0.0F);
            assertEquals(0.65F, ClientTestRuntime.soundLevel(SoundCategory.AMBIENT, 0.65F), 0.0F);
            assertEquals(0.65F, ClientTestRuntime.soundLevel(SoundCategory.RECORDS, 0.65F), 0.0F);
            runtime.finish(RunResult.preRun("ci", Collections.emptyList(), ""));
            for (SoundCategory category : SoundCategory.values()) {
                assertEquals(0.65F, ClientTestRuntime.soundLevel(category, 0.65F), 0.0F);
            }
        } finally {
            runtime.finish(RunResult.preRun("ci", Collections.emptyList(), ""));
            if (previous == null) System.clearProperty("horizonqa.manualSession");
            else System.setProperty("horizonqa.manualSession", previous);
        }
    }

    @Test
    public void fullMuteAppliesOnlyDuringAutomatedSession() {
        String previous = System.getProperty("horizonqa.client.sound");
        System.clearProperty("horizonqa.client.sound");
        ClientTestRuntime runtime = new ClientTestRuntime(() -> {});
        try {
            for (SoundCategory category : SoundCategory.values()) {
                assertEquals(0.0F, ClientTestRuntime.soundLevel(category, 0.65F), 0.0F);
            }
            runtime.finish(RunResult.preRun("ci", Collections.emptyList(), ""));
            for (SoundCategory category : SoundCategory.values()) {
                assertEquals(0.65F, ClientTestRuntime.soundLevel(category, 0.65F), 0.0F);
            }
        } finally {
            runtime.finish(RunResult.preRun("ci", Collections.emptyList(), ""));
            if (previous == null) System.clearProperty("horizonqa.client.sound");
            else System.setProperty("horizonqa.client.sound", previous);
        }
    }

    @Test
    public void optingOutPreservesConfiguredVolumes() {
        String previous = System.getProperty("horizonqa.client.sound");
        System.setProperty("horizonqa.client.sound", "true");
        assertEquals("hostile", SoundCategory.MOBS.getCategoryName());
        assertEquals("neutral", SoundCategory.ANIMALS.getCategoryName());
        assertEquals(0.65F, ClientTestRuntime.soundLevel(SoundCategory.MOBS, 0.65F), 0.0F);
        assertEquals(0.65F, ClientTestRuntime.soundLevel(SoundCategory.ANIMALS, 0.65F), 0.0F);
        ClientTestRuntime runtime = new ClientTestRuntime(() -> {});
        try {
            for (SoundCategory category : SoundCategory.values()) {
                assertEquals(0.65F, ClientTestRuntime.soundLevel(category, 0.65F), 0.0F);
            }
        } finally {
            runtime.finish(RunResult.preRun("ci", Collections.emptyList(), ""));
            if (previous == null) System.clearProperty("horizonqa.client.sound");
            else System.setProperty("horizonqa.client.sound", previous);
        }
        assertEquals(0.65F, ClientTestRuntime.soundLevel(SoundCategory.MOBS, 0.65F), 0.0F);
        assertEquals(0.65F, ClientTestRuntime.soundLevel(SoundCategory.ANIMALS, 0.65F), 0.0F);
    }
}
