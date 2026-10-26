package com.gtnewhorizons.horizonqa.mixin.late;

import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.gtnewhorizons.horizonqa.client.OffscreenWindowDriver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/** Retains exceptions which the host catches inside its render and input scope */
@Pseudo
@Mixin(targets = "com.sbancuz.offscreen.window.HostedStack", remap = false)
public abstract class MixinOffscreenHostedStack {

    @WrapOperation(
        method = "runScoped(Lcom/sbancuz/offscreen/window/input/FrameEvent;IILjava/util/function/Consumer;)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/apache/logging/log4j/Logger;error(Ljava/lang/String;Ljava/lang/Throwable;)V"))
    private void horizonqa$scopedFailure(Logger logger, String message, Throwable error, Operation<Void> original) {
        OffscreenWindowDriver.renderingFailed(error);
        original.call(logger, message, error);
    }
}
