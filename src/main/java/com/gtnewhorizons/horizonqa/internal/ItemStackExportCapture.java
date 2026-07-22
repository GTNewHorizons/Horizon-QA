package com.gtnewhorizons.horizonqa.internal;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.registry.GameData;

/** Marks ItemStacks serialized while HorizonQA is capturing a structure export. */
public final class ItemStackExportCapture {

    public static final String PORTABLE_ID_KEY = "HorizonQAItemId";

    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private ItemStackExportCapture() {}

    public static Scope open() {
        Integer depth = DEPTH.get();
        DEPTH.set(depth == null ? 1 : depth + 1);
        return new Scope(Thread.currentThread());
    }

    public static void markSerializedStack(ItemStack stack, NBTTagCompound serialized) {
        if (!isActive() || stack == null || serialized == null) return;

        Object registryName = stack.getItem() != null ? GameData.getItemRegistry()
            .getNameForObject(stack.getItem()) : null;
        serialized.setString(PORTABLE_ID_KEY, registryName != null ? registryName.toString() : "");
    }

    static boolean isActive() {
        Integer depth = DEPTH.get();
        return depth != null && depth > 0;
    }

    public static final class Scope implements AutoCloseable {

        private final Thread owner;
        private boolean closed;

        private Scope(Thread owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            if (closed) return;
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("ItemStack export scope closed from another thread");
            }

            Integer depth = DEPTH.get();
            if (depth == null || depth <= 1) {
                DEPTH.remove();
            } else {
                DEPTH.set(depth - 1);
            }
            closed = true;
        }
    }
}
