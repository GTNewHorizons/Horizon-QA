package com.gtnewhorizons.horizonqa.mixin;

import net.minecraft.client.Minecraft;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.horizonqa.ClientProxy;
import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @WrapOperation(
        method = "runGameLoop",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;isGamePaused:Z", opcode = Opcodes.PUTFIELD))
    private void horizonqa$keepWorldTicksRunning(Minecraft mc, boolean paused, Operation<Void> original) {
        original.call(mc, paused && !HorizonQAProperties.clientTestsEnabled());
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;func_147120_f()V"))
    private void horizonqa$completedFrame(CallbackInfo ci) {
        if (HorizonQAProperties.clientTestsEnabled()) ((ClientProxy) HorizonQAMod.proxy).completedClientFrame();
    }

    @WrapMethod(method = "runGameLoop")
    private void horizonqa$gameLoop(Operation<Void> original) {
        try {
            original.call();
        } catch (RuntimeException | Error error) {
            if (!HorizonQAProperties.clientTestsEnabled()) throw error;
            ((ClientProxy) HorizonQAMod.proxy).clientCrashed(error);
        }
    }
}
