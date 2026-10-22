package com.gtnewhorizons.horizonqa.mixin.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.horizonqa.client.OffscreenWindowDriver;

/** Observes actual Offscreen frames without treating main-window frames as progress. */
@Pseudo
@Mixin(targets = "com.sbancuz.offscreen.window.Window", remap = false)
public abstract class MixinOffscreenWindow {

    @Shadow
    private int pixelWidth;
    @Shadow
    private int pixelHeight;

    @Inject(
        method = "renderFrame",
        at = @At(
            value = "INVOKE",
            target = "Lcom/sbancuz/offscreen/window/HostedStack;runScoped(Lcom/sbancuz/offscreen/window/input/FrameEvent;IILjava/util/function/Consumer;)V",
            shift = At.Shift.AFTER))
    private void horizonqa$completedOffscreenFrame(CallbackInfo ci) {
        OffscreenWindowDriver.completedFrame(this, pixelWidth, pixelHeight);
    }

    @Inject(method = "destroy", at = @At("RETURN"))
    private void horizonqa$closedOffscreenWindow(CallbackInfo ci) {
        OffscreenWindowDriver.windowClosed(this);
    }
}
