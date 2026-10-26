package com.gtnewhorizons.horizonqa.mixin;

import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.settings.GameSettings;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.horizonqa.client.ClientTestRuntime;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(GameSettings.class)
public abstract class MixinGameSettings {

    @Unique
    private boolean horizonqa$savingOptions;

    @Inject(method = "getSoundLevel", at = @At("RETURN"), cancellable = true)
    private void horizonqa$testSoundLevel(SoundCategory category, CallbackInfoReturnable<Float> cir) {
        if (!horizonqa$savingOptions) {
            cir.setReturnValue(ClientTestRuntime.soundLevel(category, cir.getReturnValueF()));
        }
    }

    @WrapMethod(method = "saveOptions")
    private void horizonqa$saveConfiguredSoundLevels(Operation<Void> original) {
        boolean previous = horizonqa$savingOptions;
        horizonqa$savingOptions = true;
        try {
            original.call();
        } finally {
            horizonqa$savingOptions = previous;
        }
    }
}
