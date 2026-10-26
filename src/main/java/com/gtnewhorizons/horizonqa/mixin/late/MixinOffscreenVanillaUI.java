package com.gtnewhorizons.horizonqa.mixin.late;

import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.gtnewhorizons.horizonqa.client.OffscreenWindowDriver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/** Retains ordinary GuiScreen handler failures caught before they reach the hosted scope */
@Pseudo
@Mixin(targets = "com.sbancuz.offscreen.integration.vanilla.VanillaUI", remap = false)
public abstract class MixinOffscreenVanillaUI {

    @WrapOperation(
        method = { "onMouseClicked", "onKeyTyped" },
        at = @At(
            value = "INVOKE",
            target = "Lorg/apache/logging/log4j/Logger;trace(Ljava/lang/String;Ljava/lang/Throwable;)V"))
    private void horizonqa$handlerFailure(Logger logger, String message, Throwable error, Operation<Void> original) {
        OffscreenWindowDriver.renderingFailed(error);
        original.call(logger, message, error);
    }
}
