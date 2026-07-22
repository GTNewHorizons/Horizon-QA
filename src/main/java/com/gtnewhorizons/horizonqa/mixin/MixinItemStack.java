package com.gtnewhorizons.horizonqa.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.horizonqa.internal.ItemStackExportCapture;

@Mixin(ItemStack.class)
public abstract class MixinItemStack {

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void horizonqa$markExportedItemStack(NBTTagCompound serialized,
        CallbackInfoReturnable<NBTTagCompound> cir) {
        ItemStackExportCapture.markSerializedStack((ItemStack) (Object) this, cir.getReturnValue());
    }
}
