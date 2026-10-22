package com.gtnewhorizons.horizonqa.examples.tests;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;

import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Exercises effective test volume and options persistence in a real client. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientAudioTests {

    private ClientAudioTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 200)
    public static void effectiveVolumeDoesNotOverwriteSavedOptions(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .client("effective sound levels preserve saved volume", client -> {
                Minecraft mc = Minecraft.getMinecraft();
                Path options = mc.mcDataDir.toPath()
                    .resolve("options.txt");
                Map<SoundCategory, Float> configured = new EnumMap<>(SoundCategory.class);
                for (SoundCategory category : SoundCategory.values())
                    configured.put(category, savedVolume(options, category));
                assertEffectiveVolumes(mc, configured);
                mc.gameSettings.saveOptions();
                for (SoundCategory category : SoundCategory.values()) {
                    if (savedVolume(options, category) != configured.get(category)) {
                        throw new AssertionError("Saving options overwrote the configured volume: " + category);
                    }
                }
                assertEffectiveVolumes(mc, configured);
            })
            .succeed();
    }

    private static void assertEffectiveVolumes(Minecraft mc, Map<SoundCategory, Float> configured) {
        for (SoundCategory category : SoundCategory.values()) {
            float expected = !HorizonQAProperties.clientSoundEnabled() ? 0.0F : configured.get(category);
            if (mc.gameSettings.getSoundLevel(category) != expected) {
                throw new AssertionError("Unexpected effective sound level during client tests: " + category);
            }
        }
    }

    private static float savedVolume(Path options, SoundCategory category) {
        Properties saved = new Properties();
        try (Reader reader = Files.newBufferedReader(options, StandardCharsets.UTF_8)) {
            saved.load(reader);
        } catch (IOException e) {
            throw new AssertionError("Cannot read client options", e);
        }
        return Float.parseFloat(saved.getProperty("soundCategory_" + category.getCategoryName(), "1.0"));
    }
}
