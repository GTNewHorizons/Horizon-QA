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
    public static void mobVolumeDoesNotOverwriteSavedOptions(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .client("test sounds are muted without changing saved volume", client -> {
                Minecraft mc = Minecraft.getMinecraft();
                Path options = mc.mcDataDir.toPath()
                    .resolve("options.txt");
                SoundCategory[] categories = HorizonQAProperties.clientMuted() ? SoundCategory.values()
                    : new SoundCategory[] { SoundCategory.MOBS, SoundCategory.ANIMALS };
                Map<SoundCategory, Float> configured = new EnumMap<>(SoundCategory.class);
                for (SoundCategory category : categories) configured.put(category, savedVolume(options, category));
                assertMuted(mc, categories);
                mc.gameSettings.saveOptions();
                for (SoundCategory category : categories) {
                    if (savedVolume(options, category) != configured.get(category)) {
                        throw new AssertionError("Saving options overwrote the configured volume: " + category);
                    }
                }
                assertMuted(mc, categories);
            })
            .succeed();
    }

    private static void assertMuted(Minecraft mc, SoundCategory[] categories) {
        for (SoundCategory category : categories) {
            if (mc.gameSettings.getSoundLevel(category) != 0.0F) {
                throw new AssertionError("Sound category must be muted during client tests: " + category);
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
