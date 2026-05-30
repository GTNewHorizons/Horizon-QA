package com.gtnewhorizons.horizonqa.mixin;

import java.io.IOException;
import java.net.InetAddress;

import net.minecraft.network.NetworkSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.WorldType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.HorizonQAMod;
import com.gtnewhorizons.horizonqa.world.GameTestWorldType;

@Mixin(DedicatedServer.class)
public abstract class MixinDedicatedServer {

    @Shadow
    private boolean canSpawnStructures;

    @Redirect(
        method = "startServer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/WorldType;parseWorldType(Ljava/lang/String;)Lnet/minecraft/world/WorldType;"))
    private static WorldType gametest$forceLevelTypeProperty(String name) {
        if (HorizonQAProperties.isCi()) {
            return GameTestWorldType.INSTANCE;
        }
        return WorldType.parseWorldType(name);
    }

    @Redirect(
        method = "startServer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkSystem;addLanEndpoint(Ljava/net/InetAddress;I)V"))
    private void gametest$skipBind(NetworkSystem net, InetAddress address, int port) throws IOException {
        if (HorizonQAProperties.isCi()) {
            HorizonQAMod.LOG.info("GameTest: skipping dedicated server network bind (would have used port {})", port);
            return;
        }
        net.addLanEndpoint(address, port);
    }

    @Inject(
        method = "startServer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/dedicated/DedicatedServer;loadAllWorlds(Ljava/lang/String;Ljava/lang/String;JLnet/minecraft/world/WorldType;Ljava/lang/String;)V",
            shift = At.Shift.BEFORE))
    private void gametest$tuneDedicatedFlags(CallbackInfoReturnable<Boolean> cir) {
        if (!HorizonQAProperties.isCi()) {
            return;
        }
        MinecraftServer self = (MinecraftServer) (Object) this;
        self.setCanSpawnAnimals(false);
        self.setCanSpawnNPCs(false);
        this.canSpawnStructures = false;
    }

    @Inject(method = "allowSpawnMonsters", at = @At("HEAD"), cancellable = true)
    private void gametest$noHostileSpawns(CallbackInfoReturnable<Boolean> cir) {
        if (HorizonQAProperties.isCi()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getAllowNether", at = @At("HEAD"), cancellable = true)
    private void gametest$disableNether(CallbackInfoReturnable<Boolean> cir) {
        if (HorizonQAProperties.isCi()) {
            cir.setReturnValue(false);
        }
    }
}
