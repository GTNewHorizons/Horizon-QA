package com.gtnewhorizons.horizonqa.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.horizonqa.GameTestJvmFlags;
import com.gtnewhorizons.horizonqa.world.GameTestWorldType;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {

    @Redirect(
        method = "loadAllWorlds",
        at = @At(
            value = "NEW",
            target = "(JLnet/minecraft/world/WorldSettings$GameType;ZZLnet/minecraft/world/WorldType;)Lnet/minecraft/world/WorldSettings;"))
    private WorldSettings gametest$newSettingsFromSeed(long seed, WorldSettings.GameType gameType, boolean mapFeatures,
        boolean hardcore, WorldType requestedType) {
        if (!GameTestJvmFlags.isEnabled()) {
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
        if (!GameTestJvmFlags.isEnabled()) {
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
}
