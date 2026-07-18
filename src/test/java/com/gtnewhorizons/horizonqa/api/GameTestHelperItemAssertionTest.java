package com.gtnewhorizons.horizonqa.api;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class GameTestHelperItemAssertionTest {

    private final GameTestHelper helper = new GameTestHelper(null, null, 1, 2, 3);
    private final Item item = new Item();

    @Test
    public void acceptsStacksWithEqualItemDamageSizeAndNbt() {
        ItemStack expected = stack(item, 3, 2, "same");

        helper.assertItemEqual(expected, expected.copy());
        helper.assertItemEqual(null, null);
    }

    @Test
    public void comparesEveryItemStackField() {
        ItemStack expected = stack(item, 3, 2, "expected");

        assertMismatch(expected, stack(new Item(), 3, 2, "expected"));
        assertMismatch(expected, stack(item, 3, 4, "expected"));
        assertMismatch(expected, stack(item, 5, 2, "expected"));
        assertMismatch(expected, stack(item, 3, 2, "actual"));
        assertMismatch(expected, null);
    }

    @Test
    public void failurePrintsBothStacksWithAllComparedFields() {
        ItemStack expected = stack(item, 3, 2, "expected");
        ItemStack actual = stack(new Item(), 5, 4, "actual");

        GameTestAssertException failure = assertThrows(
            GameTestAssertException.class,
            () -> helper.assertItemEqual(expected, actual, "Output slot mismatch"));

        String message = failure.getMessage();
        assertTrue(message.startsWith("Output slot mismatch: expected <item ID="));
        assertTrue(message.contains(", damage=2, stack size=3, NBT="));
        assertTrue(message.contains("expected"));
        assertTrue(message.contains("> but found <item ID="));
        assertTrue(message.contains(", damage=4, stack size=5, NBT="));
        assertTrue(message.contains("actual"));
    }

    private void assertMismatch(ItemStack expected, ItemStack actual) {
        assertThrows(GameTestAssertException.class, () -> helper.assertItemEqual(expected, actual));
    }

    private static ItemStack stack(Item item, int size, int damage, String marker) {
        ItemStack stack = new ItemStack(item, size, damage);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("marker", marker);
        stack.setTagCompound(nbt);
        return stack;
    }
}
