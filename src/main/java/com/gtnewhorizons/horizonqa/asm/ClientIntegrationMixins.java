package com.gtnewhorizons.horizonqa.asm;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.relauncher.FMLLaunchHandler;

/** Registers optional client instrumentation after Forge has discovered the integration mod */
@LateMixin
public final class ClientIntegrationMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.horizonqa.integrations.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return FMLLaunchHandler.side()
            .isClient() && loadedMods.contains("offscreen")
                ? Arrays.asList("MixinOffscreenWindow", "MixinOffscreenHostedStack", "MixinOffscreenVanillaUI")
                : Collections.emptyList();
    }
}
