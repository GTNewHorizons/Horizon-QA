package com.gtnewhorizons.horizonqa.examples.tests;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.FakePlayer;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.Weather;
import com.gtnewhorizons.horizonqa.api.annotation.BeforeBatch;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.examples.ExamplesMod;

@GameTestHolder(ExamplesMod.MODID)
public class HelperApiTests {

    @BeforeBatch("")
    public static void setup() {}

    @GameTest(timeoutTicks = 20)
    public static void blockPlaceAndAssert(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.diamond_block);
        helper.assertBlockPresent(Blocks.diamond_block, 0, 0, 0);
        helper.assertBlockAbsent(Blocks.stone, 0, 0, 0);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    public static void blockDestroyAndAssert(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.iron_block);
        helper.destroyBlock(0, 0, 0);
        helper.assertBlockAbsent(Blocks.iron_block, 0, 0, 0);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    public static void blockMetaAssert(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.wool, 14);
        helper.assertBlockPresent(Blocks.wool, 14, 0, 0, 0);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    public static void chestInsertAndAssert(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.chest);
        helper.startSequence()
            .thenIdle(1)
            .thenExecute(() -> {
                helper.insertItem(0, 0, 0, new ItemStack(Items.diamond, 5));
                helper.assertInventoryContains(0, 0, 0, new ItemStack(Items.diamond, 5));
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    public static void chestSlotAssert(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.chest);
        helper.startSequence()
            .thenIdle(1)
            .thenExecute(() -> {
                helper.insertItem(0, 0, 0, new ItemStack(Items.iron_ingot, 3));
                helper.assertSlot(0, 0, 0, 0, new ItemStack(Items.iron_ingot, 3));
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    public static void chestExtractAndEmpty(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.chest);
        helper.startSequence()
            .thenIdle(1)
            .thenExecute(() -> {
                helper.insertItem(0, 0, 0, new ItemStack(Items.gold_ingot, 10));
                helper.extractItem(0, 0, 0, new ItemStack(Items.gold_ingot), 10);
                helper.assertInventoryEmpty(0, 0, 0);
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    public static void tileEntityPresent(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.chest);
        helper.startSequence()
            .thenIdle(1)
            .thenExecute(() -> helper.assertTileEntityPresent(0, 0, 0))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    public static void tileNbtSubset(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.chest);
        helper.startSequence()
            .thenIdle(1)
            .thenExecute(() -> {
                NBTTagCompound expected = new NBTTagCompound();
                expected.setString("id", "Chest");
                helper.assertTileNBT(0, 0, 0, expected);
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    public static void getTileNbtReturnsDeepCopy(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.chest);
        helper.startSequence()
            .thenIdle(1)
            .thenExecute(() -> {
                NBTTagCompound nbt = helper.getTileNBT(0, 0, 0);
                if (!nbt.hasKey("id")) {
                    helper.fail("Expected 'id' key in TileEntity NBT copy");
                }
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 30)
    public static void redstoneInput(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.stone);
        helper.setRedstoneInput(1, 0, 0, 15);
        helper.startSequence()
            .thenIdle(2)
            .thenExecute(() -> helper.assertRedstonePower(0, 0, 0, 1))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    public static void fixWorldTimeAndWeather(GameTestHelper helper) {
        helper.disableRandomTicks();
        helper.fixWorldTime(6000L);
        helper.setWeather(Weather.CLEAR);

        long time = helper.getWorld()
            .getWorldTime();
        if (time != 6000L) {
            helper.fail("World time was not set to 6000");
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    public static void setTileNbt(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.chest);
        helper.startSequence()
            .thenIdle(1)
            .thenExecute(() -> {
                NBTTagCompound nbt = helper.getTileNBT(0, 0, 0);
                nbt.setString("CustomName", "TestChest");
                helper.setTile(0, 0, 0, nbt);

                NBTTagCompound after = helper.getTileNBT(0, 0, 0);
                String name = after.getString("CustomName");
                if (!"TestChest".equals(name)) {
                    helper.fail("Custom name was not persisted: " + name);
                }
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    public static void fakePlayerSpawn(GameTestHelper helper) {
        FakePlayer player = helper.spawnFakePlayer("GameTestBot");
        if (player == null) {
            helper.fail("spawnFakePlayer returned null");
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    public static void fakePlayerRightClick(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.lever);
        FakePlayer player = helper.spawnFakePlayer("ClickBot");
        helper.startSequence()
            .thenIdle(1)
            .thenExecute(() -> helper.simulateRightClick(0, 0, 0, player, null))
            .thenIdle(1)
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 60)
    public static void hopperFillsChestBelow(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.chest);
        helper.setBlock(0, 1, 0, Blocks.hopper, 0);
        helper.startSequence()
            .thenIdle(1)
            .thenExecute(() -> helper.insertItem(0, 1, 0, new ItemStack(Items.iron_ingot, 3)));
        helper.succeedWhen(() -> {
            TileEntity te = helper.getWorld()
                .getTileEntity(helper.getOriginX(), helper.getOriginY(), helper.getOriginZ());
            if (!(te instanceof IInventory inv)) return false;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s != null && s.getItem() == Items.iron_ingot) return true;
            }
            return false;
        });
    }

    @GameTest(timeoutTicks = 5, required = false)
    public static void succeedWhenTimesOutAsHardFail(GameTestHelper helper) {
        helper.succeedWhen(() -> false);
    }

    @GameTest(required = false)
    public static void testGhostBlockDiff(GameTestHelper helper) {
        helper.assertBlockPresent(Blocks.stone, 0, 0, 0);
    }

    @GameTest(required = false)
    public static void testNoGhostBlockDiff(GameTestHelper helper) {
        helper.fail();
    }

    @GameTest(template = "chicken")
    public static void testChicken(GameTestHelper helper) {
        helper.assertEntityCount(EntityChicken.class, 1, 1, 1, 1);
        helper.assertEntityPresent(EntityItem.class, 1, 1, 3);
        helper.succeed();
    }
}
