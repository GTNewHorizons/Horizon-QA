package com.gtnewhorizons.horizonqa.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.horizonqa.GameTestJvmFlags;
import com.gtnewhorizons.horizonqa.internal.GameTestRunner;

import cpw.mods.fml.common.FMLCommonHandler;

@Mixin(value = FMLCommonHandler.class, remap = false)
public class MixinFMLCommonHandler {

    @Inject(method = "onPreServerTick", at = @At("HEAD"))
    private void gametest$beforeTickStart(CallbackInfo ci) {
        if (GameTestJvmFlags.isEnabled()) {
            GameTestRunner.handleTickStart();
        }
    }

    @Inject(method = "onPostServerTick", at = @At("RETURN"))
    private void gametest$afterTickEnd(CallbackInfo ci) {
        if (GameTestJvmFlags.isEnabled()) {
            GameTestRunner.handleTickEnd();
        }
    }
}
