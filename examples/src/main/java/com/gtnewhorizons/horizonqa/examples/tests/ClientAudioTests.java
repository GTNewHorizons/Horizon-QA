package com.gtnewhorizons.horizonqa.examples.tests;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Exercises effective mob volume and options persistence in a real client. */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientAudioTests {

    private ClientAudioTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 200)
    public static void mobVolumeDoesNotOverwriteSavedOptions(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .client("mob sounds are muted without changing saved volume", client -> {
                Minecraft mc = Minecraft.getMinecraft();
                Path options = mc.mcDataDir.toPath()
                    .resolve("options.txt");
                float hostile = savedVolume(options, SoundCategory.MOBS);
                float neutral = savedVolume(options, SoundCategory.ANIMALS);
                assertMobsMuted(mc);
                mc.gameSettings.saveOptions();
                if (savedVolume(options, SoundCategory.MOBS) != hostile
                    || savedVolume(options, SoundCategory.ANIMALS) != neutral) {
                    throw new AssertionError("Saving options overwrote the configured mob volume");
                }
                assertMobsMuted(mc);
            })
            .succeed();
    }

    private static void assertMobsMuted(Minecraft mc) {
        for (SoundCategory category : new SoundCategory[] { SoundCategory.MOBS, SoundCategory.ANIMALS }) {
            if (mc.gameSettings.getSoundLevel(category) != 0.0F) {
                throw new AssertionError("Mob sounds must be muted during client tests: " + category);
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
