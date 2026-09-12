package com.gtnewhorizons.horizonqa.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.lwjgl.opengl.Display;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.gtnewhorizons.horizonqa.client.ClientTaskQueue.Phase;
import com.gtnewhorizons.horizonqa.client.LwjglInput;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow
    private boolean isGamePaused;

    @Inject(method = "displayGuiScreen", at = @At("HEAD"))
    private void horizonqa$prepareScreenChange(GuiScreen requested, CallbackInfo ci) {
        LwjglInput.screenChanging(requested);
        if (!HorizonQAProperties.clientTestsEnabled() || !(requested instanceof GuiIngameMenu)) return;
        Minecraft mc = Minecraft.getMinecraft();
        ClientTest.recordLifecycleDiagnostic(
            "pauseScreenRequested previous=" + (mc.currentScreen == null ? "<none>"
                : mc.currentScreen.getClass()
                    .getName())
                + ", rawPaused="
                + isGamePaused
                + ", reportedPaused="
                + mc.isGamePaused()
                + ", displayActive="
                + Display.isActive()
                + ", gameFocus="
                + mc.inGameHasFocus
                + ", pauseOnLostFocus="
                + mc.gameSettings.pauseOnLostFocus
                + ", guiScale="
                + mc.gameSettings.guiScale
                + "\n"
                + ExceptionUtils.getStackTrace(new Throwable("Pause screen requested")));
    }

    @Inject(method = "setIngameFocus", at = @At("HEAD"), cancellable = true)
    private void horizonqa$leaveNativeCursorAlone(CallbackInfo ci) {
        if (HorizonQAProperties.clientTestsEnabled()) ci.cancel();
    }

    @WrapOperation(
        method = "runGameLoop",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;isGamePaused:Z", opcode = Opcodes.PUTFIELD))
    private void horizonqa$keepClientAndServerTicking(Minecraft mc, boolean paused, Operation<Void> original) {
        original.call(mc, paused && !HorizonQAProperties.clientTestsEnabled());
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;func_147120_f()V"))
    private void horizonqa$completedFrame(CallbackInfo ci) {
        ClientTest.tick(Phase.FRAME);
    }

    @WrapMethod(method = "runGameLoop")
    private void horizonqa$gameLoop(Operation<Void> original) {
        try {
            LwjglInput.applyPointer();
            original.call();
        } catch (RuntimeException | Error error) {
            if (!ClientTest.failActive(error)) throw error;
            try {
                Minecraft.getMinecraft()
                    .displayGuiScreen(null);
            } catch (RuntimeException | Error closingError) {
                if (closingError != error) error.addSuppressed(closingError);
            }
            ClientTest.tick(Phase.FRAME);
        }
    }
}
