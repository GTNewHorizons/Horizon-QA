package com.gtnewhorizons.gametest.api.gt;

import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.gametest.api.annotation.Experimental;

/**
 * Predicate for {@link Bus#assertContains} and {@link BusGroup# assertContains}. Default matching ignores stack size;
 * use {@link #count(int)} to require a minimum amount in the matched slot.
 */
@Experimental
public final class ItemMatcher {

    private final Predicate<ItemStack> predicate;
    private final String description;
    private int requiredCount = 1;

    private ItemMatcher(Predicate<ItemStack> predicate, String description) {
        this.predicate = predicate;
        this.description = description;
    }

    /** Same item, damage, and NBT as {@code template}; stack size ignored unless {@link #count(int)} is used. */
    public static ItemMatcher of(ItemStack template) {
        if (template == null) throw new IllegalArgumentException("template must not be null");
        return new ItemMatcher(
            stack -> stack != null
                && stack.getItem() == template.getItem()
                && stack.getItemDamage() == template.getItemDamage()
                && nbtMatches(template, stack), template.getDisplayName() + " x" + template.stackSize);
    }

    public static ItemMatcher predicate(Predicate<ItemStack> test) {
        if (test == null) throw new IllegalArgumentException("test must not be null");
        return new ItemMatcher(test, "<custom predicate>");
    }

    private static boolean nbtMatches(ItemStack template, ItemStack actual) {
        if (template.getTagCompound() == null && actual.getTagCompound() == null) return true;
        if (template.getTagCompound() == null || actual.getTagCompound() == null) return false;
        return template.getTagCompound()
            .equals(actual.getTagCompound());
    }

    /** Returns a new matcher that also requires at least {@code n} items in the stack. */
    public ItemMatcher count(int n) {
        if (n < 1) throw new IllegalArgumentException("count must be >= 1, got " + n);
        ItemMatcher copy = new ItemMatcher(predicate, description);
        copy.requiredCount = n;
        return copy;
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.stackSize < requiredCount) return false;
        return predicate.test(stack);
    }

    @Override
    public String toString() {
        return requiredCount > 1 ? description + " (count>=" + requiredCount + ")" : description;
    }
}
