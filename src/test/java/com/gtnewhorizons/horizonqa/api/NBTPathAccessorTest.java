package com.gtnewhorizons.horizonqa.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.Test;

public class NBTPathAccessorTest {

    @Test
    public void resolvesCompoundsListsAndEscapedDots() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound nested = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("id", "minecraft:stone");
        list.appendTag(entry);
        nested.setTag("items", list);
        nested.setString("literal.dot", "value");
        root.setTag("nested", nested);

        assertEquals(
            "minecraft:stone",
            ((NBTTagString) NBTPathAccessor.resolve(root, "nested.items.0.id")).func_150285_a_());
        assertEquals("value", ((NBTTagString) NBTPathAccessor.resolve(root, "nested.literal\\.dot")).func_150285_a_());
        assertEquals("\"minecraft:stone\"", NBTPathAccessor.resolveAsString(root, "nested.items.0.id"));
        assertTrue(NBTPathAccessor.exists(root, "nested.items.0"));
    }

    @Test
    public void missingAndInvalidPathsReturnNull() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagString("value"));
        root.setTag("list", list);

        assertNull(NBTPathAccessor.resolve(null, "list.0"));
        assertNull(NBTPathAccessor.resolve(root, null));
        assertNull(NBTPathAccessor.resolve(root, ""));
        assertNull(NBTPathAccessor.resolve(root, "missing"));
        assertNull(NBTPathAccessor.resolve(root, "list.not-a-number"));
        assertNull(NBTPathAccessor.resolve(root, "list.-1"));
        assertNull(NBTPathAccessor.resolve(root, "list.1"));
        assertNull(NBTPathAccessor.resolve(root, "list.0.child"));
        assertNull(NBTPathAccessor.resolveAsString(root, "missing"));
        assertFalse(NBTPathAccessor.exists(root, "missing"));
    }
}
