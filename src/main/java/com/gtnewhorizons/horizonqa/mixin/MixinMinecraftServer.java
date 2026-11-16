package com.gtnewhorizons.horizonqa.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldInfo;

import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.internal.GameTestRunner;
import com.gtnewhorizons.horizonqa.world.GameTestWorldType;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {

    @Shadow
    protected abstract void saveAllWorlds(boolean dontLog);

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tick()V"))
    private void gametest$tickAtTurboRate(MinecraftServer server) {
        int multiplier = gametest$isTurboTicking() ? HorizonQAProperties.turboMultiplier() : 1;
        for (int tick = 0; tick < multiplier; tick++) {
            server.tick();
            if (!gametest$isTurboTicking()) {
                break;
            }
        }
    }

    @Redirect(
        method = "run",
        at = @At(
            value = "INVOKE",
            target = "Lorg/apache/logging/log4j/Logger;warn(Ljava/lang/String;[Ljava/lang/Object;)V",
            remap = false))
    private void gametest$suppressKeepUpWarning(Logger logger, String message, Object[] parameters) {
        if (!gametest$isTurboTicking()) {
            logger.warn(message, parameters);
        }
    }

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/ServerConfigurationManager;saveAllPlayerData()V"))
    private void gametest$skipScheduledPlayerSave(ServerConfigurationManager manager) {
        if (!gametest$isTurboTicking()) {
            manager.saveAllPlayerData();
        }
    }

    @Redirect(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;saveAllWorlds(Z)V"))
    private void gametest$skipScheduledWorldSave(MinecraftServer server, boolean dontLog) {
        if (!gametest$isTurboTicking()) {
            saveAllWorlds(dontLog);
        }
    }

    @Redirect(
        method = "loadAllWorlds",
        at = @At(
            value = "NEW",
            target = "(JLnet/minecraft/world/WorldSettings$GameType;ZZLnet/minecraft/world/WorldType;)Lnet/minecraft/world/WorldSettings;"))
    private WorldSettings gametest$newSettingsFromSeed(long seed, WorldSettings.GameType gameType, boolean mapFeatures,
        boolean hardcore, WorldType requestedType) {
        if (!HorizonQAProperties.usesVoidWorld()) {
            return new WorldSettings(seed, gameType, mapFeatures, hardcore, requestedType);
        }
        return new WorldSettings(seed, gameType, false, hardcore, GameTestWorldType.INSTANCE);
    }

    @Redirect(
        method = "loadAllWorlds",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/world/storage/WorldInfo;)Lnet/minecraft/world/WorldSettings;"))
    private WorldSettings gametest$newSettingsFromDisk(WorldInfo info) {
        if (!HorizonQAProperties.usesVoidWorld()) {
            return new WorldSettings(info);
        }
        WorldSettings recreated = new WorldSettings(
            info.getSeed(),
            info.getGameType(),
            false,
            info.isHardcoreModeEnabled(),
            GameTestWorldType.INSTANCE);
        return recreated.func_82750_a(info.getGeneratorOptions());
    }

    private static boolean gametest$isTurboTicking() {
        return GameTestRunner.isTurboActive();
    }
}
