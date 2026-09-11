package com.gtnewhorizons.horizonqa.mixin;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @WrapOperation(
        method = "updateCameraAndRender",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;pauseOnLostFocus:Z",
            opcode = Opcodes.GETFIELD))
    private boolean horizonqa$keepInactiveTestWindowRunning(GameSettings settings, Operation<Boolean> original) {
        return original.call(settings) && !HorizonQAProperties.clientTestsEnabled();
    }
}
