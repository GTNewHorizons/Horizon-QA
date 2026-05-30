package com.gtnewhorizons.horizonqa.mixin;

import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.HorizonQAProperties;

@Mixin(NetHandlerPlayServer.class)
public abstract class MixinNetHandlerPlayServer {

    @Redirect(
        method = "onDisconnect",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;initiateShutdown()V"))
    private void gametest$dontKillEmptyIntegratedServer(MinecraftServer server) {
        if (HorizonQAProperties.isActive() && server.isSinglePlayer()) {
            HorizonQAMod.LOG.info("GameTest: host disconnected; integrated server stays running until /stop.");
            return;
        }
        server.initiateShutdown();
    }
}
