package com.gtnewhorizons.horizonqa.mixin.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.horizonqa.client.OffscreenWindowDriver;

/** Observes the presented image in its native context before the back buffer is swapped */
@Pseudo
@Mixin(targets = "com.sbancuz.offscreen.window.Renderer", remap = false)
public abstract class MixinOffscreenRenderer {

    @Inject(method = "blit", at = @At("RETURN"), require = 1)
    private void horizonqa$presentedFrame(int width, int height, CallbackInfo ci) {
        OffscreenWindowDriver.presentedFrame(width, height);
    }
}
